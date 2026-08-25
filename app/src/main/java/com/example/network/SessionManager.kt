package com.example.network

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "rewards_session"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAuthToken(): String? {
        return prefs?.getString("auth_token", null)
    }

    fun setAuthToken(token: String) {
        prefs?.edit()?.putString("auth_token", token)?.apply()
    }
    
    fun clearSession() {
        prefs?.edit()?.remove("auth_token")?.apply()
    }
}
