import { createHash } from "node:crypto";

/** Status strings understood by the Android client (RemoteEntitlementVerifier). */
export type EntitlementStatus =
  | "ACTIVE"
  | "CANCELED_ACTIVE"
  | "GRACE"
  | "ON_HOLD"
  | "PAUSED"
  | "PENDING"
  | "EXPIRED"
  | "REVOKED";

/** Subset of androidpublisher v3 SubscriptionPurchaseV2 that we rely on. */
export interface SubscriptionPurchaseV2 {
  subscriptionState?: string | null;
  acknowledgementState?: string | null;
  linkedPurchaseToken?: string | null;
  lineItems?: Array<{ productId?: string | null; expiryTime?: string | null }> | null;
  testPurchase?: object | null;
}

export interface Entitlement {
  status: EntitlementStatus;
  productId: string | null;
  expiryTimeMillis: number;
  acknowledged: boolean;
  needsAcknowledgement: boolean;
  linkedTokenHash: string | null;
  test: boolean;
}

export const hashToken = (token: string): string => createHash("sha256").update(token).digest("hex");

/**
 * Maps Play's subscription state to the app's entitlement. Pro is granted by the client only for
 * ACTIVE / CANCELED_ACTIVE / GRACE. Everything else (pending, on hold, paused, expired) is locked.
 */
export function toEntitlement(p: SubscriptionPurchaseV2, allowedProducts: string[], nowMs: number): Entitlement {
  const items = (p.lineItems ?? []).filter((li) => li.productId && allowedProducts.includes(li.productId));
  const expiry = Math.max(0, ...items.map((li) => (li.expiryTime ? Date.parse(li.expiryTime) || 0 : 0)));
  const productId = items[0]?.productId ?? null;
  const acknowledged = p.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";

  let status: EntitlementStatus;
  if (!productId) {
    status = "EXPIRED"; // not one of our products
  } else {
    switch (p.subscriptionState) {
      case "SUBSCRIPTION_STATE_ACTIVE":
        status = expiry > nowMs ? "ACTIVE" : "EXPIRED";
        break;
      case "SUBSCRIPTION_STATE_CANCELED":
        // Cancelled but still inside the paid period: keep access until expiry.
        status = expiry > nowMs ? "CANCELED_ACTIVE" : "EXPIRED";
        break;
      case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
        status = "GRACE";
        break;
      case "SUBSCRIPTION_STATE_ON_HOLD":
        status = "ON_HOLD";
        break;
      case "SUBSCRIPTION_STATE_PAUSED":
        status = "PAUSED";
        break;
      case "SUBSCRIPTION_STATE_PENDING":
        status = "PENDING";
        break;
      default:
        status = "EXPIRED"; // EXPIRED, PENDING_PURCHASE_CANCELED, unknown
    }
  }

  return {
    status,
    productId,
    expiryTimeMillis: expiry,
    acknowledged,
    needsAcknowledgement: !acknowledged && (status === "ACTIVE" || status === "GRACE"),
    linkedTokenHash: p.linkedPurchaseToken ? hashToken(p.linkedPurchaseToken) : null,
    test: !!p.testPurchase,
  };
}

export const grantsPro = (s: EntitlementStatus) => s === "ACTIVE" || s === "CANCELED_ACTIVE" || s === "GRACE";
