package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WebhookEventDto(
    val eventId: String,
    val provider: String, // "ZENDIT", "GIFTBIT"
    val eventType: String, // "order.fulfilled", "order.failed", "topup.delivered", "esim.provisioned"
    val timestamp: String,
    val payload: WebhookPayloadDto
)

@JsonClass(generateAdapter = true)
data class WebhookPayloadDto(
    val orderId: String,
    val orderNumber: String? = null,
    val status: String, // "COMPLETED", "FAILED", "REFUNDED"
    val voucherCode: String? = null,
    val voucherPin: String? = null,
    val failureReason: String? = null,
    val externalTransactionId: String? = null
)
