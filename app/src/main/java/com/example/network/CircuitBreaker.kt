package com.example.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class CircuitState {
    CLOSED,    // Normal traffic flow
    OPEN,      // Tripped - fail fast with cached fallback
    HALF_OPEN  // Testing downstream service health
}

/**
 * Enterprise Circuit Breaker for Downstream Gateway APIs (Zendit / Giftbit)
 */
class CircuitBreaker(
    val name: String,
    val failureThreshold: Int = 3,
    val resetTimeoutMillis: Long = 15000L // 15 seconds cool-down
) {
    private val _state = MutableStateFlow(CircuitState.CLOSED)
    val state: StateFlow<CircuitState> = _state.asStateFlow()

    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)

    suspend fun <T> execute(
        fallback: (suspend (Exception) -> T)? = null,
        block: suspend () -> T
    ): T {
        val currentState = _state.value
        val now = System.currentTimeMillis()

        // Check if OPEN state has expired -> transition to HALF_OPEN
        if (currentState == CircuitState.OPEN) {
            if (now - lastFailureTime.get() > resetTimeoutMillis) {
                Log.w("CircuitBreaker", "[$name] Reset timeout elapsed. Transitioning OPEN -> HALF_OPEN")
                _state.value = CircuitState.HALF_OPEN
            } else {
                val error = CircuitBreakerOpenException("Circuit [$name] is OPEN. Fast-failing downstream request.")
                if (fallback != null) {
                    return fallback(error)
                }
                throw error
            }
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            if (fallback != null) {
                fallback(e)
            } else {
                throw e
            }
        }
    }

    private fun onSuccess() {
        failureCount.set(0)
        if (_state.value != CircuitState.CLOSED) {
            Log.i("CircuitBreaker", "[$name] Downstream call succeeded. Transitioning ${_state.value} -> CLOSED")
            _state.value = CircuitState.CLOSED
        }
    }

    private fun onFailure(e: Exception) {
        lastFailureTime.set(System.currentTimeMillis())
        val failures = failureCount.incrementAndGet()
        Log.e("CircuitBreaker", "[$name] Failure recorded ($failures/$failureThreshold): ${e.message}")

        if (failures >= failureThreshold) {
            _state.value = CircuitState.OPEN
            Log.e("CircuitBreaker", "[$name] Failure threshold breached! Circuit transitioned to OPEN.")
        }
    }

    fun reset() {
        failureCount.set(0)
        _state.value = CircuitState.CLOSED
    }
}

class CircuitBreakerOpenException(message: String) : Exception(message)
