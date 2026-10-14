# Reel Counter — billing verification backend

A small service that verifies Google Play subscription purchases for the Reel Counter Android app and keeps them in sync through Real-Time Developer Notifications (RTDN).

**What it stores:** one Firestore document per purchase, keyed by the **SHA-256 hash** of the purchase token. Each document holds the product, status and expiry.
**What it never stores or receives:** raw purchase tokens, user identity or account data, device IDs, or any Reel Counter activity.

| Endpoint | Purpose |
|---|---|
| `POST /v1/subscriptions/verify` | Called by the app. Body is `{packageName, productId, purchaseToken}`. Returns `{status, expiryTimeMillis, acknowledged}` and acknowledges new purchases. |
| `POST /v1/rtdn` | Pub/Sub push endpoint for RTDN. Requires a Google-signed OIDC token and rejects everything else. |
| `GET /healthz` | Health check. |

Status values: `ACTIVE`, `CANCELED_ACTIVE` (still paid up), `GRACE`, `ON_HOLD`, `PAUSED`, `PENDING`, `EXPIRED`, `REVOKED` (refunded or voided). The app grants Pro only for `ACTIVE`, `CANCELED_ACTIVE` and `GRACE`.

## Local development

```bash
npm install
npm test
npm run build
```

## Deploy (Google Cloud Run + Firestore + Pub/Sub)

The commands below use bash syntax. Replace `PROJECT` with your Google Cloud project ID and `REGION` with your region (for example `asia-south1`).

1. **Create the project and turn on the APIs**

```bash
gcloud services enable run.googleapis.com firestore.googleapis.com pubsub.googleapis.com androidpublisher.googleapis.com cloudbuild.googleapis.com --project PROJECT
gcloud firestore databases create --location=REGION --project PROJECT
```

2. **Create the service account the backend runs as**

```bash
gcloud iam service-accounts create reel-billing --project PROJECT
gcloud projects add-iam-policy-binding PROJECT --member=serviceAccount:reel-billing@PROJECT.iam.gserviceaccount.com --role=roles/datastore.user
```

3. **Give that service account access to your Play Console**
   - In Play Console, go to **Users and permissions → Invite new users**.
   - Enter `reel-billing@PROJECT.iam.gserviceaccount.com`.
   - Under App permissions, grant **View financial data** and **Manage orders and subscriptions** for the Reel Counter app.
   - No key file is needed. Cloud Run uses the attached service account.

4. **Deploy the service**

```bash
gcloud run deploy reel-counter-billing --source . --region REGION --project PROJECT --service-account reel-billing@PROJECT.iam.gserviceaccount.com --allow-unauthenticated --set-env-vars NODE_ENV=production,STORE=firestore,PACKAGE_NAME=com.paicons.selfdevelopment,ALLOWED_PRODUCTS=pro_monthly,pro_yearly
```

   `--allow-unauthenticated` is needed because the app calls `/verify` directly. `/rtdn` still enforces its own OIDC check.

5. **Set up Real-Time Developer Notifications**

```bash
gcloud pubsub topics create play-rtdn --project PROJECT
gcloud pubsub topics add-iam-policy-binding play-rtdn --project PROJECT --member=serviceAccount:google-play-developer-notifications@system.gserviceaccount.com --role=roles/pubsub.publisher
gcloud iam service-accounts create rtdn-pusher --project PROJECT
gcloud pubsub subscriptions create play-rtdn-push --topic play-rtdn --project PROJECT --push-endpoint=https://SERVICE_URL/v1/rtdn --push-auth-service-account=rtdn-pusher@PROJECT.iam.gserviceaccount.com --push-auth-token-audience=https://SERVICE_URL/v1/rtdn --ack-deadline=30
gcloud run services update reel-counter-billing --region REGION --project PROJECT --update-env-vars PUBSUB_AUDIENCE=https://SERVICE_URL/v1/rtdn,PUBSUB_SERVICE_ACCOUNT=rtdn-pusher@PROJECT.iam.gserviceaccount.com
```

   Replace `SERVICE_URL` with the URL that step 4 printed.

   Then in Play Console, go to **Monetize with Play → Monetization setup → Real-time developer notifications**. Set the topic to `projects/PROJECT/topics/play-rtdn` and click **Send test notification**. The logs should show `rtdn test notification received`.

6. **Turn on Firestore TTL for the deduplication collection**

```bash
gcloud firestore fields ttls update expireAt --collection-group=rtdn_messages --enable-ttl --project PROJECT
```

7. **Point the app at the backend**

   In `reel-counter-android/gradle.properties`, set:

```properties
reelcounter.billingBackendUrl=https://SERVICE_URL
```

## Security notes

- The service fails closed. If `PUBSUB_AUDIENCE` or `PUBSUB_SERVICE_ACCOUNT` is missing, every RTDN request is rejected.
- `ALLOW_UNAUTHENTICATED_RTDN=true` is refused when `NODE_ENV=production`.
- The verify endpoint accepts only your package name and your product IDs. It is rate-limited per IP, and request bodies are capped at 16 KB.
- Logs contain only the first 12 characters of token hashes.
- If you need more abuse protection, add Cloud Armor or set Cloud Run max instances.
