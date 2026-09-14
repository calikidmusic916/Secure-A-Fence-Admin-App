package com.example.secureafenceadministrator.data.network

import android.content.Context
import android.content.Intent
import com.example.secureafenceadministrator.ui.auth.LoginActivity

object SessionManager {
    fun getToken(context: Context): String? {
        val rawToken = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", null)
        if (rawToken.isNullOrEmpty()) return null
        return if (rawToken.startsWith("Bearer ", ignoreCase = true)) {
            rawToken.substring(7).trim()
        } else {
            rawToken.trim()
        }
    }

    fun saveToken(context: Context, token: String) {
        val cleanToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token.substring(7).trim()
        } else {
            token.trim()
        }
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("token", cleanToken).apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().remove("token").apply()
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
