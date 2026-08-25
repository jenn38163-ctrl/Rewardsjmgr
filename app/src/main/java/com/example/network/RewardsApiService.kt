package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GatewayOrderDto(
    val id: String,
    val orderNumber: String,
    val title: String,
    val amountInr: Int,
    val state: String, // PENDING, PROCESSING, COMPLETED, FAILED_RETRYABLE, FAILED_TERMINAL, REFUNDED
    val provider: String,
    val recipient: String = "",
    val voucherCode: String? = null,
    val voucherPin: String? = null,
    val itemType: String = "Gift Card",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val retryCount: Int = 0,
    val failureReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayOrdersResponse(
    val status: String,
    val count: Int = 0,
    val orders: List<GatewayOrderDto> = emptyList(),
    val gatewayLatencyMs: Long = 24
)

@JsonClass(generateAdapter = true)
data class RetryOrderResponse(
    val status: String,
    val message: String? = null,
    val order: GatewayOrderDto? = null
)

@JsonClass(generateAdapter = true)
data class WalletStatusResponse(
    val status: String,
    val wallet: WalletData?
)

@JsonClass(generateAdapter = true)
data class WalletData(
    val tokenBalance: Double,
    val caftBalance: Double,
    val ctcBalance: Double,
    val jmgrBalance: Double,
    val zenditBalanceCents: Int,
    val giftbitBalanceCents: Int
)

@JsonClass(generateAdapter = true)
data class RegisterPushRequest(
    val pushToken: String,
    val platform: String = "ANDROID"
)

@JsonClass(generateAdapter = true)
data class GenericResponse(
    val status: String,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SwapRequest(
    val tokenType: String,
    val amount: Double,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class VoteRequest(
    val proposalId: String,
    val approve: Boolean
)

@JsonClass(generateAdapter = true)
data class AllocateRequest(
    val targetProvider: String,
    val amount: Double
)

interface RewardsApiService {
    @GET("/api/v1/wallet/status")
    suspend fun getWalletStatus(): WalletStatusResponse
    
    @POST("/api/v1/wallet/register-device-push")
    suspend fun registerDevicePush(@Body request: RegisterPushRequest): GenericResponse

    @POST("/api/v1/assets/execute-oracle-swap")
    suspend fun executeOracleSwap(@Body request: SwapRequest): GenericResponse

    @POST("/api/v1/assets/vote")
    suspend fun voteJmgr(@Body request: VoteRequest): GenericResponse

    @POST("/api/v1/wallet/allocate")
    suspend fun allocateFunds(@Body request: AllocateRequest): GenericResponse

    @GET("/api/v1/orders")
    suspend fun getOrders(): GatewayOrdersResponse

    @POST("/api/v1/orders/{orderId}/retry")
    suspend fun retryOrder(@Path("orderId") orderId: String): RetryOrderResponse
}
