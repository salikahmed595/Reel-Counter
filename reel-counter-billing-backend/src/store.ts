import { Firestore } from "@google-cloud/firestore";
import type { EntitlementStatus } from "./entitlement.js";

/**
 * Minimal entitlement ledger. Keyed by SHA-256(purchase token) — the raw token, user identity and
 * any Reel Counter activity are never stored (PRD §11).
 */
export interface EntitlementRecord {
  tokenHash: string;
  productId: string | null;
  status: EntitlementStatus;
  expiryTimeMillis: number;
  acknowledged: boolean;
  test: boolean;
  supersededBy: string | null;
  updatedAt: number;
  source: "verify" | "rtdn";
}

export interface EntitlementStore {
  get(tokenHash: string): Promise<EntitlementRecord | null>;
  put(rec: EntitlementRecord): Promise<void>;
  markSuperseded(oldHash: string, newHash: string, at: number): Promise<void>;
  markRevoked(tokenHash: string, at: number): Promise<void>;
  /** @return false if this RTDN message id was already processed (idempotency). */
  claimMessage(messageId: string, at: number): Promise<boolean>;
}

export class MemoryStore implements EntitlementStore {
  readonly records = new Map<string, EntitlementRecord>();
  private messages = new Set<string>();

  async get(h: string) { return this.records.get(h) ?? null; }
  async put(r: EntitlementRecord) {
    const prev = this.records.get(r.tokenHash);
    this.records.set(r.tokenHash, {
      ...r,
      status: prev?.status === "REVOKED" ? "REVOKED" : r.status,
      supersededBy: prev?.supersededBy ?? r.supersededBy ?? null,
    });
  }
  async markSuperseded(oldHash: string, newHash: string, at: number) {
    const r = this.records.get(oldHash);
    this.records.set(oldHash, { ...(r ?? emptyRecord(oldHash, at)), supersededBy: newHash, updatedAt: at });
  }
  async markRevoked(h: string, at: number) {
    const r = this.records.get(h);
    this.records.set(h, { ...(r ?? emptyRecord(h, at)), status: "REVOKED", updatedAt: at, source: "rtdn" });
  }
  async claimMessage(id: string) {
    if (this.messages.has(id)) return false;
    this.messages.add(id);
    return true;
  }
}

const emptyRecord = (h: string, at: number): EntitlementRecord => ({
  tokenHash: h, productId: null, status: "EXPIRED", expiryTimeMillis: 0, acknowledged: false,
  test: false, supersededBy: null, updatedAt: at, source: "rtdn",
});

export class FirestoreStore implements EntitlementStore {
  private db = new Firestore({ ignoreUndefinedProperties: true });
  private ents = this.db.collection("entitlements");
  private msgs = this.db.collection("rtdn_messages");

  async get(h: string) {
    const d = await this.ents.doc(h).get();
    return d.exists ? (d.data() as EntitlementRecord) : null;
  }
  async put(r: EntitlementRecord) {
    // Never overwrite a REVOKED (refunded/voided) record with a later verify.
    await this.db.runTransaction(async (tx) => {
      const ref = this.ents.doc(r.tokenHash);
      const cur = await tx.get(ref);
      const prev = cur.exists ? (cur.data() as EntitlementRecord) : null;
      const status = prev?.status === "REVOKED" ? "REVOKED" : r.status;
      tx.set(ref, { ...r, status, supersededBy: prev?.supersededBy ?? r.supersededBy ?? null }, { merge: true });
    });
  }
  async markSuperseded(oldHash: string, newHash: string, at: number) {
    await this.ents.doc(oldHash).set({ tokenHash: oldHash, supersededBy: newHash, updatedAt: at }, { merge: true });
  }
  async markRevoked(h: string, at: number) {
    await this.ents.doc(h).set({ tokenHash: h, status: "REVOKED", updatedAt: at, source: "rtdn" }, { merge: true });
  }
  async claimMessage(id: string, at: number) {
    try {
      // create() fails if the doc exists → atomic dedupe. Configure a Firestore TTL on `expireAt`.
      await this.msgs.doc(id).create({ at, expireAt: new Date(at + 30 * 24 * 3600 * 1000) });
      return true;
    } catch (e: unknown) {
      if ((e as { code?: number }).code === 6) return false; // ALREADY_EXISTS
      throw e;
    }
  }
}
