package com.example.network

import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.UUID

@JsonClass(generateAdapter = true)
data class FirstData3DSData(
    val signature: String,
    val type: String = "G",
    val version: String = "ECv1",
    val data: String
)

@JsonClass(generateAdapter = true)
data class FirstDataPaymentRequest(
    @Json(name = "currency_code") val currencyCode: String,
    val amount: String,
    @Json(name = "merchant_ref") val merchantRef: String,
    @Json(name = "transaction_type") val transactionType: String = "purchase",
    val method: String = "3DS",
    @Json(name = "3DS") val threeDS: FirstData3DSData
)

@JsonClass(generateAdapter = true)
data class FirstDataPaymentResponse(
    @Json(name = "correlation_id") val correlationId: String?,
    @Json(name = "transaction_status") val transactionStatus: String?,
    @Json(name = "validation_status") val validationStatus: String?,
    @Json(name = "transaction_id") val transactionId: String?,
    @Json(name = "bank_message") val bankMessage: String?,
    @Json(name = "gateway_message") val gatewayMessage: String?
)

class FirstDataPaymentService(
    private val apiKey: String,
    private val token: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api.firstdata.com/gateway/v2/payments"
) {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun computeHmac(payload: String, secret: String, timestamp: String, nonce: String): String {
        val dataToSign = "$apiKey$nonce$timestamp$payload"
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256Hmac.init(secretKey)
        val hash = sha256Hmac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    suspend fun processGooglePayPayment(
        currency: String,
        amountCents: String,
        orderId: String,
        encryptedMessage: String,
        ephemeralPublicKey: String,
        tag: String,
        googleSignature: String
    ): FirstDataPaymentResponse? {
        val dataJson = "{\"encryptedMessage\":\"$encryptedMessage\",\"ephemeralPublicKey\":\"$ephemeralPublicKey\",\"tag\":\"$tag\"}"
        
        val threeDS = FirstData3DSData(
            signature = googleSignature,
            type = "G",
            version = "ECv1",
            data = dataJson
        )

        val requestPayload = FirstDataPaymentRequest(
            currencyCode = currency,
            amount = amountCents,
            merchantRef = orderId,
            transactionType = "purchase",
            method = "3DS",
            threeDS = threeDS
        )

        val adapter = moshi.adapter(FirstDataPaymentRequest::class.java)
        val jsonPayload = adapter.toJson(requestPayload)

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val authorization = computeHmac(jsonPayload, apiSecret, timestamp, nonce)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url(baseUrl)
            .post(body)
            .addHeader("apikey", apiKey)
            .addHeader("token", token)
            .addHeader("content-type", "application/json")
            .addHeader("Authorization", authorization)
            .addHeader("nonce", nonce)
            .addHeader("timestamp", timestamp)
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from First Data")

        if (!response.isSuccessful) {
            throw Exception("First Data Payment Error (${response.code}): $responseBody")
        }

        val responseAdapter = moshi.adapter(FirstDataPaymentResponse::class.java)
        return responseAdapter.fromJson(responseBody)
    }
}
