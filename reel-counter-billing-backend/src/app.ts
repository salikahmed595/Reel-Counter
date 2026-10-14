import express, { type Request, type Response, type NextFunction } from "express";
import { OAuth2Client } from "google-auth-library";
import { grantsPro, hashToken, toEntitlement } from "./entitlement.js";
import { PlayTokenNotFound, type PlayClient } from "./play.js";
import type { EntitlementStore } from "./store.js";

export interface Config {
  packageName: string;
  allowedProducts: string[];
  pubsubAudience: string;
  pubsubServiceAccount: string;
  allowUnauthenticatedRtdn: boolean;
}

/** Verifies the Pub/Sub push OIDC token. Returns false when the request is not from our push subscription. */
export type RtdnAuthenticator = (authorizationHeader: string | undefined) => Promise<boolean>;

export function googleOidcAuthenticator(cfg: Config): RtdnAuthenticator {
  const client = new OAuth2Client();
  return async (header) => {
    if (cfg.allowUnauthenticatedRtdn) return true;
    if (!cfg.pubsubAudience || !cfg.pubsubServiceAccount) return false; // fail closed
    const token = header?.startsWith("Bearer ") ? header.slice(7) : null;
    if (!token) return false;
    try {
      const ticket = await client.verifyIdToken({ idToken: token, audience: cfg.pubsubAudience });
      const p = ticket.getPayload();
      return !!p && p.email === cfg.pubsubServiceAccount && p.email_verified === true;
    } catch {
      return false;
    }
  };
}

const log = (severity: "INFO" | "WARNING" | "ERROR", message: string, extra: Record<string, unknown> = {}) =>
  console.log(JSON.stringify({ severity, message, ...extra })); // structured for Cloud Logging; never tokens

const TOKEN_RE = /^[A-Za-z0-9._\-:]{10,2048}$/;

/** Tiny fixed-window rate limiter (per instance). Cloud Armor / API Gateway can add more. */
function rateLimit(limitPerMinute: number) {
  const hits = new Map<string, { n: number; reset: number }>();
  return (req: Request, res: Response, next: NextFunction) => {
    const key = req.ip ?? "unknown";
    const now = Date.now();
    const cur = hits.get(key);
    if (!cur || cur.reset < now) {
      hits.set(key, { n: 1, reset: now + 60_000 });
      if (hits.size > 50_000) hits.clear();
      return next();
    }
    if (++cur.n > limitPerMinute) return res.status(429).json({ error: "rate_limited" });
    next();
  };
}

export function createApp(cfg: Config, play: PlayClient, store: EntitlementStore, rtdnAuth: RtdnAuthenticator, now = () => Date.now()) {
  const app = express();
  app.disable("x-powered-by");
  app.set("trust proxy", true);
  app.use(express.json({ limit: "16kb" }));

  app.get("/healthz", (_req, res) => res.json({ ok: true }));

  /**
   * Client verification. Body: { packageName, productId, purchaseToken }.
   * Only the purchase is involved — no device IDs, accounts or activity are accepted or stored.
   */
  app.post("/v1/subscriptions/verify", rateLimit(30), async (req, res) => {
    const { packageName, productId, purchaseToken } = (req.body ?? {}) as Record<string, unknown>;
    if (packageName !== cfg.packageName || typeof productId !== "string" || !cfg.allowedProducts.includes(productId) ||
      typeof purchaseToken !== "string" || !TOKEN_RE.test(purchaseToken)) {
      return res.status(400).json({ error: "invalid_request" });
    }
    const tokenHash = hashToken(purchaseToken);
    try {
      const sub = await play.getSubscription(cfg.packageName, purchaseToken);
      const ent = toEntitlement(sub, cfg.allowedProducts, now());
      let acknowledged = ent.acknowledged;
      if (ent.needsAcknowledgement && ent.productId) {
        try {
          await play.acknowledge(cfg.packageName, ent.productId, purchaseToken);
          acknowledged = true;
        } catch (e) {
          log("WARNING", "acknowledge failed; client may retry", { tokenHash: tokenHash.slice(0, 12), err: String(e) });
        }
      }
      await store.put({
        tokenHash, productId: ent.productId, status: ent.status, expiryTimeMillis: ent.expiryTimeMillis,
        acknowledged, test: ent.test, supersededBy: null, updatedAt: now(), source: "verify",
      });
      if (ent.linkedTokenHash) await store.markSuperseded(ent.linkedTokenHash, tokenHash, now());
      const rec = await store.get(tokenHash);
      const status = rec?.status === "REVOKED" ? "REVOKED" : ent.status;
      log("INFO", "verified", { tokenHash: tokenHash.slice(0, 12), status, pro: grantsPro(status), test: ent.test });
      return res.json({ status, expiryTimeMillis: ent.expiryTimeMillis, acknowledged });
    } catch (e) {
      if (e instanceof PlayTokenNotFound) return res.status(404).json({ error: "not_found" });
      log("ERROR", "verification failed", { tokenHash: tokenHash.slice(0, 12), err: String(e) });
      return res.status(503).json({ error: "unavailable" });
    }
  });

  /** Real-time developer notifications via Pub/Sub push (authenticated with OIDC). */
  app.post("/v1/rtdn", async (req, res) => {
    if (!(await rtdnAuth(req.header("authorization")))) return res.status(401).json({ error: "unauthorized" });
    const message = (req.body as { message?: { data?: string; messageId?: string } })?.message;
    if (!message?.data || !message.messageId) return res.status(400).json({ error: "bad_message" });

    let n: {
      packageName?: string;
      subscriptionNotification?: { notificationType?: number; purchaseToken?: string; subscriptionId?: string };
      voidedPurchaseNotification?: { purchaseToken?: string };
      testNotification?: unknown;
    };
    try {
      n = JSON.parse(Buffer.from(message.data, "base64").toString("utf8"));
    } catch {
      return res.status(204).end(); // malformed payloads are dropped, not retried forever
    }
    if (n.packageName !== cfg.packageName) return res.status(204).end();
    if (n.testNotification) {
      log("INFO", "rtdn test notification received");
      return res.status(204).end();
    }
    if (!(await store.claimMessage(message.messageId, now()))) return res.status(204).end(); // duplicate delivery

    try {
      if (n.voidedPurchaseNotification?.purchaseToken) {
        const h = hashToken(n.voidedPurchaseNotification.purchaseToken);
        await store.markRevoked(h, now());
        log("INFO", "purchase voided", { tokenHash: h.slice(0, 12) });
      } else if (n.subscriptionNotification?.purchaseToken) {
        const token = n.subscriptionNotification.purchaseToken;
        const h = hashToken(token);
        const ent = toEntitlement(await play.getSubscription(cfg.packageName, token), cfg.allowedProducts, now());
        await store.put({
          tokenHash: h, productId: ent.productId, status: ent.status, expiryTimeMillis: ent.expiryTimeMillis,
          acknowledged: ent.acknowledged, test: ent.test, supersededBy: null, updatedAt: now(), source: "rtdn",
        });
        if (ent.linkedTokenHash) await store.markSuperseded(ent.linkedTokenHash, h, now());
        log("INFO", "rtdn subscription update", { tokenHash: h.slice(0, 12), type: n.subscriptionNotification.notificationType, status: ent.status });
      }
      return res.status(204).end();
    } catch (e) {
      if (e instanceof PlayTokenNotFound) return res.status(204).end();
      log("ERROR", "rtdn processing failed; Pub/Sub will retry", { err: String(e) });
      return res.status(500).json({ error: "retry" });
    }
  });

  app.use((_req, res) => res.status(404).json({ error: "not_found" }));
  return app;
}
