package com.example.network

import com.example.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val authInterceptor = AuthInterceptor()
    private val auditLoggingInterceptor = AuditLoggingInterceptor()

    val zenditCircuitBreaker = CircuitBreaker(name = "ZenditGateway", failureThreshold = 3, resetTimeoutMillis = 15000L)
    val giftbitCircuitBreaker = CircuitBreaker(name = "GiftbitGateway", failureThreshold = 3, resetTimeoutMillis = 15000L)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(auditLoggingInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: RewardsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(RewardsApiService::class.java)
    }
}
