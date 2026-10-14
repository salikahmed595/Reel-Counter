import { google } from "googleapis";
import type { SubscriptionPurchaseV2 } from "./entitlement.js";

export class PlayTokenNotFound extends Error {}

export interface PlayClient {
  getSubscription(packageName: string, token: string): Promise<SubscriptionPurchaseV2>;
  acknowledge(packageName: string, productId: string, token: string): Promise<void>;
}

/**
 * Google Play Developer API client. Authenticates with Application Default Credentials
 * (the Cloud Run service account, which must be granted access in Play Console → Users & permissions).
 * No key files are bundled.
 */
export class GooglePlayClient implements PlayClient {
  private api = google.androidpublisher({
    version: "v3",
    auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
  });

  async getSubscription(packageName: string, token: string): Promise<SubscriptionPurchaseV2> {
    try {
      const res = await this.api.purchases.subscriptionsv2.get({ packageName, token });
      return res.data as SubscriptionPurchaseV2;
    } catch (e: unknown) {
      const code = (e as { code?: number; status?: number }).code ?? (e as { status?: number }).status;
      if (code === 404 || code === 410 || code === 400) throw new PlayTokenNotFound("purchase token not found");
      throw e;
    }
  }

  async acknowledge(packageName: string, productId: string, token: string): Promise<void> {
    await this.api.purchases.subscriptions.acknowledge({
      packageName,
      subscriptionId: productId,
      token,
      requestBody: {},
    });
  }
}
