package com.example.secureafenceadministrator.data.network

import android.content.Context
import android.content.Intent
import com.example.secureafenceadministrator.ui.auth.LoginActivity

object SessionManager {
    fun getToken(context: Context): String? {
        return context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", null)
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("token", token).apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().remove("token").apply()
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
