package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
}
