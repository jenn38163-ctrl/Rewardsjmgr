package com.example.network

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Interceptor that automatically retrieves the Firebase ID token and injects it as a
 * Bearer token into the Authorization header of every outgoing OkHttp request.
 */
class AuthInterceptor(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val AUTH_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val TOKEN_TIMEOUT_SECONDS = 5L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Inject Bearer token if Authorization header is not already manually provided
        if (originalRequest.header(AUTH_HEADER) == null) {
            val token = fetchToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader(AUTH_HEADER, "$BEARER_PREFIX$token")
            }
        }

        return chain.proceed(requestBuilder.build())
    }

    private fun fetchToken(): String? {
        val user = firebaseAuth.currentUser
        if (user != null) {
            try {
                // Await token resolution synchronously on OkHttp's background thread
                val tokenResult = Tasks.await(
                    user.getIdToken(false),
                    TOKEN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
                val token = tokenResult.token
                if (!token.isNullOrBlank()) {
                    SessionManager.setAuthToken(token)
                    return token
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Firebase ID token, checking SessionManager cache: ${e.message}")
            }
        }

        // Fallback to SessionManager if user is authenticated or cached
        return SessionManager.getAuthToken()
    }
}
