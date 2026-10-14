import { describe, expect, it, beforeEach, afterEach } from "vitest";
import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import { createApp, type Config } from "../src/app.js";
import { hashToken, toEntitlement, type SubscriptionPurchaseV2 } from "../src/entitlement.js";
import { PlayTokenNotFound, type PlayClient } from "../src/play.js";
import { MemoryStore } from "../src/store.js";

const NOW = Date.parse("2026-10-14T12:00:00Z");
const FUTURE = "2026-11-14T12:00:00Z";
const PAST = "2026-09-14T12:00:00Z";
const PRODUCTS = ["pro_monthly", "pro_yearly"];
const TOKEN = "tok_abcdefghijklmnop.1234";

const sub = (state: string, expiry = FUTURE, extra: Partial<SubscriptionPurchaseV2> = {}): SubscriptionPurchaseV2 => ({
  subscriptionState: state,
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  lineItems: [{ productId: "pro_monthly", expiryTime: expiry }],
  ...extra,
});

describe("toEntitlement", () => {
  it.each([
    ["SUBSCRIPTION_STATE_ACTIVE", FUTURE, "ACTIVE"],
    ["SUBSCRIPTION_STATE_ACTIVE", PAST, "EXPIRED"],
    ["SUBSCRIPTION_STATE_CANCELED", FUTURE, "CANCELED_ACTIVE"],
    ["SUBSCRIPTION_STATE_CANCELED", PAST, "EXPIRED"],
    ["SUBSCRIPTION_STATE_IN_GRACE_PERIOD", FUTURE, "GRACE"],
    ["SUBSCRIPTION_STATE_ON_HOLD", PAST, "ON_HOLD"],
    ["SUBSCRIPTION_STATE_PAUSED", PAST, "PAUSED"],
    ["SUBSCRIPTION_STATE_PENDING", FUTURE, "PENDING"],
    ["SUBSCRIPTION_STATE_EXPIRED", PAST, "EXPIRED"],
    ["SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", PAST, "EXPIRED"],
  ])("%s (%s) → %s", (state, expiry, expected) => {
    expect(toEntitlement(sub(state, expiry), PRODUCTS, NOW).status).toBe(expected);
  });

  it("rejects products that are not ours", () => {
    const e = toEntitlement({ ...sub("SUBSCRIPTION_STATE_ACTIVE"), lineItems: [{ productId: "other", expiryTime: FUTURE }] }, PRODUCTS, NOW);
    expect(e.status).toBe("EXPIRED");
  });

  it("flags acknowledgement only for active unacknowledged purchases", () => {
    const pending = { acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING" };
    expect(toEntitlement(sub("SUBSCRIPTION_STATE_ACTIVE", FUTURE, pending), PRODUCTS, NOW).needsAcknowledgement).toBe(true);
    expect(toEntitlement(sub("SUBSCRIPTION_STATE_PENDING", FUTURE, pending), PRODUCTS, NOW).needsAcknowledgement).toBe(false);
  });
});

class FakePlay implements PlayClient {
  subs = new Map<string, SubscriptionPurchaseV2>();
  acks: string[] = [];
  fail = false;
  async getSubscription(_pkg: string, token: string) {
    if (this.fail) throw new Error("play down");
    const s = this.subs.get(token);
    if (!s) throw new PlayTokenNotFound("nope");
    return s;
  }
  async acknowledge(_pkg: string, _pid: string, token: string) {
    this.acks.push(token);
  }
}

const cfg: Config = {
  packageName: "com.paicons.selfdevelopment", allowedProducts: PRODUCTS,
  pubsubAudience: "aud", pubsubServiceAccount: "sa", allowUnauthenticatedRtdn: false,
};

describe("HTTP API", () => {
  let server: Server;
  let base: string;
  let play: FakePlay;
  let store: MemoryStore;
  let authorized = true;

  beforeEach(async () => {
    play = new FakePlay();
    store = new MemoryStore();
    authorized = true;
    const app = createApp(cfg, play, store, async (h) => authorized && h === "Bearer ok", () => NOW);
    server = app.listen(0);
    await new Promise((r) => server.once("listening", r));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  afterEach(() => new Promise<void>((r) => server.close(() => r())));

  const verify = (body: object) =>
    fetch(`${base}/v1/subscriptions/verify`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  const rtdn = (payload: object, id = "m1", auth = "Bearer ok") =>
    fetch(`${base}/v1/rtdn`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: auth },
      body: JSON.stringify({ message: { messageId: id, data: Buffer.from(JSON.stringify(payload)).toString("base64") } }),
    });

  it("verifies, acknowledges and stores only a token hash", async () => {
    play.subs.set(TOKEN, sub("SUBSCRIPTION_STATE_ACTIVE", FUTURE, { acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING" }));
    const res = await verify({ packageName: cfg.packageName, productId: "pro_monthly", purchaseToken: TOKEN });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: "ACTIVE", expiryTimeMillis: Date.parse(FUTURE), acknowledged: true });
    expect(play.acks).toEqual([TOKEN]);
    expect([...store.records.keys()]).toEqual([hashToken(TOKEN)]);
    expect(JSON.stringify([...store.records.values()])).not.toContain(TOKEN);
  });

  it("rejects wrong package, unknown product and malformed token", async () => {
    expect((await verify({ packageName: "x", productId: "pro_monthly", purchaseToken: TOKEN })).status).toBe(400);
    expect((await verify({ packageName: cfg.packageName, productId: "free_stuff", purchaseToken: TOKEN })).status).toBe(400);
    expect((await verify({ packageName: cfg.packageName, productId: "pro_monthly", purchaseToken: "<script>" })).status).toBe(400);
  });

  it("returns 404 for unknown tokens and 503 when Play is unavailable", async () => {
    expect((await verify({ packageName: cfg.packageName, productId: "pro_monthly", purchaseToken: TOKEN })).status).toBe(404);
    play.fail = true;
    expect((await verify({ packageName: cfg.packageName, productId: "pro_monthly", purchaseToken: TOKEN })).status).toBe(503);
  });

  it("voided purchases stay revoked even if a later verify sees them active", async () => {
    play.subs.set(TOKEN, sub("SUBSCRIPTION_STATE_ACTIVE"));
    expect((await rtdn({ packageName: cfg.packageName, voidedPurchaseNotification: { purchaseToken: TOKEN } })).status).toBe(204);
    const res = await verify({ packageName: cfg.packageName, productId: "pro_monthly", purchaseToken: TOKEN });
    expect((await res.json()).status).toBe("REVOKED");
  });

  it("RTDN requires authentication and is idempotent", async () => {
    play.subs.set(TOKEN, sub("SUBSCRIPTION_STATE_ON_HOLD", PAST));
    const n = { packageName: cfg.packageName, subscriptionNotification: { notificationType: 5, purchaseToken: TOKEN, subscriptionId: "pro_monthly" } };
    expect((await rtdn(n, "m1", "Bearer forged")).status).toBe(401);
    expect((await rtdn(n, "m1")).status).toBe(204);
    expect(store.records.get(hashToken(TOKEN))?.status).toBe("ON_HOLD");
    play.subs.set(TOKEN, sub("SUBSCRIPTION_STATE_ACTIVE"));
    expect((await rtdn(n, "m1")).status).toBe(204); // duplicate delivery ignored
    expect(store.records.get(hashToken(TOKEN))?.status).toBe("ON_HOLD");
  });

  it("upgrades mark the previous token as superseded", async () => {
    const NEW = "tok_newtokenvalue.5678";
    play.subs.set(NEW, sub("SUBSCRIPTION_STATE_ACTIVE", FUTURE, { linkedPurchaseToken: TOKEN, lineItems: [{ productId: "pro_yearly", expiryTime: FUTURE }] }));
    await verify({ packageName: cfg.packageName, productId: "pro_yearly", purchaseToken: NEW });
    expect(store.records.get(hashToken(TOKEN))?.supersededBy).toBe(hashToken(NEW));
  });
});
