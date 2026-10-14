package com.reelcounter.app.billing

import com.reelcounter.app.BuildConfig
import com.reelcounter.app.core.Clock
import com.reelcounter.app.data.prefs.EntitlementState
import com.reelcounter.app.data.prefs.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

object ProductIds {
    const val MONTHLY = "pro_monthly"
    const val YEARLY = "pro_yearly"
    val ALL = listOf(MONTHLY, YEARLY)
}

class EntitlementRepository(private val clock: Clock) {
    /**
     * Pro only for a server-verified ACTIVE/GRACE entitlement that hasn't expired and was verified
     * within the capped offline window. Never for PENDING or unverified purchases (AC16).
     * Core features never depend on this.
     */
    fun isPro(prefs: UserPrefs): Boolean {
        val e = prefs.entitlement
        val now = clock.nowMs()
        if (e.state != EntitlementState.ACTIVE && e.state != EntitlementState.GRACE) return false
        if (e.expiresAtMs in 1 until now) return false
        return now - e.lastVerifiedAtMs <= OFFLINE_GRACE_MS
    }

    companion object {
        /** PRD §11: exact window is an open business/security decision; 72h is the placeholder. */
        val OFFLINE_GRACE_MS: Long = TimeUnit.HOURS.toMillis(72)
    }
}

sealed interface VerificationResult {
    data class Verified(val state: EntitlementState, val expiresAtMs: Long, val serverAcknowledged: Boolean) : VerificationResult
    data object Rejected : VerificationResult
    data class Unavailable(val reason: String) : VerificationResult
}

interface EntitlementVerifier {
    suspend fun verify(packageName: String, productId: String, purchaseToken: String): VerificationResult
}

/**
 * Sends ONLY the purchase token, product and package to the billing backend over HTTPS.
 * No activity history or device identifiers are ever sent (PRD §11).
 *
 * Contract (implemented by the separate backend repo):
 *   POST {BILLING_BACKEND_URL}/v1/subscriptions/verify
 *   { "packageName": "...", "productId": "...", "purchaseToken": "..." }
 *   → 200 { "status": "ACTIVE|GRACE|ON_HOLD|PAUSED|EXPIRED|CANCELED_ACTIVE|PENDING|REVOKED",
 *           "expiryTimeMillis": 0, "acknowledged": true }
 */
class RemoteEntitlementVerifier(private val baseUrl: String = BuildConfig.BILLING_BACKEND_URL) : EntitlementVerifier {
    override suspend fun verify(packageName: String, productId: String, purchaseToken: String): VerificationResult =
        withContext(Dispatchers.IO) {
            if (!baseUrl.startsWith("https://")) return@withContext VerificationResult.Unavailable("backend not configured")
            runCatching {
                val conn = (URL("${baseUrl.trimEnd('/')}/v1/subscriptions/verify").openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("packageName", packageName).put("productId", productId).put("purchaseToken", purchaseToken)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode == 404 || conn.responseCode == 410) return@runCatching VerificationResult.Rejected
                if (conn.responseCode !in 200..299) return@runCatching VerificationResult.Unavailable("HTTP ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val state = when (json.optString("status")) {
                    "ACTIVE", "CANCELED_ACTIVE" -> EntitlementState.ACTIVE
                    "GRACE" -> EntitlementState.GRACE
                    "PENDING" -> EntitlementState.PENDING
                    else -> EntitlementState.EXPIRED // ON_HOLD, PAUSED, EXPIRED, REVOKED → no Pro
                }
                VerificationResult.Verified(state, json.optLong("expiryTimeMillis", 0), json.optBoolean("acknowledged", false))
            }.getOrElse { VerificationResult.Unavailable(it.javaClass.simpleName) }
        }
}
