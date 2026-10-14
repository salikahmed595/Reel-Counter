import { createApp, googleOidcAuthenticator, type Config } from "./app.js";
import { GooglePlayClient } from "./play.js";
import { FirestoreStore, MemoryStore } from "./store.js";

const env = (k: string, d = "") => (process.env[k] ?? d).trim();

const cfg: Config = {
  packageName: env("PACKAGE_NAME", "com.paicons.selfdevelopment"),
  allowedProducts: env("ALLOWED_PRODUCTS", "pro_monthly,pro_yearly").split(",").map((s) => s.trim()).filter(Boolean),
  pubsubAudience: env("PUBSUB_AUDIENCE"),
  pubsubServiceAccount: env("PUBSUB_SERVICE_ACCOUNT"),
  allowUnauthenticatedRtdn: env("ALLOW_UNAUTHENTICATED_RTDN") === "true",
};

if (cfg.allowUnauthenticatedRtdn && process.env.NODE_ENV === "production") {
  throw new Error("ALLOW_UNAUTHENTICATED_RTDN must not be enabled in production");
}

const store = env("STORE", "firestore") === "memory" ? new MemoryStore() : new FirestoreStore();
const app = createApp(cfg, new GooglePlayClient(), store, googleOidcAuthenticator(cfg));
const port = Number(env("PORT", "8080"));
app.listen(port, () => console.log(JSON.stringify({ severity: "INFO", message: `listening on ${port}`, packageName: cfg.packageName })));
