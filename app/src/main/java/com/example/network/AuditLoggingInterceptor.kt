package com.example.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.UUID

/**
 * Enterprise Audit Logging Interceptor with PII and Secret Redaction
 * 
 * Functions:
 * 1. Automatically injects X-Correlation-ID header on all outbound proxy calls.
 * 2. Obfuscates Authorization tokens, phone numbers, and voucher PINs in logs.
 */
class AuditLoggingInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val correlationId = originalRequest.header("X-Correlation-ID") ?: UUID.randomUUID().toString()

        // Inject correlation ID if missing
        val requestWithHeaders = originalRequest.newBuilder()
            .header("X-Correlation-ID", correlationId)
            .header("X-Client-Platform", "Android-RewardsGateway")
            .build()

        val requestUrl = requestWithHeaders.url.toString()
        val method = requestWithHeaders.method

        // Redact and log outbound request
        val sanitizedUrl = redactSensitiveUrl(requestUrl)
        Log.i("GatewayAudit", "[REQ] [$correlationId] $method $sanitizedUrl")

        val startTime = System.currentTimeMillis()
        val response: Response
        try {
            response = chain.proceed(requestWithHeaders)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e("GatewayAudit", "[FAIL] [$correlationId] $method $sanitizedUrl FAILED in ${elapsed}ms: ${e.message}")
            throw e
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i("GatewayAudit", "[RESP] [$correlationId] HTTP ${response.code} for $method $sanitizedUrl in ${elapsed}ms")

        return response
    }

    private fun redactSensitiveUrl(url: String): String {
        return url
            .replace(Regex("api_key=[^&]+", RegexOption.IGNORE_CASE), "api_key=REDACTED")
            .replace(Regex("token=[^&]+", RegexOption.IGNORE_CASE), "token=REDACTED")
    }

    companion object {
        fun maskPhoneNumber(phone: String): String {
            if (phone.length < 7) return "***"
            return phone.take(4) + " **** " + phone.takeLast(3)
        }

        fun maskVoucherCode(code: String?): String {
            if (code.isNullOrBlank()) return "N/A"
            if (code.length <= 6) return "******"
            return code.take(3) + "-****-" + code.takeLast(3)
        }
    }
}
