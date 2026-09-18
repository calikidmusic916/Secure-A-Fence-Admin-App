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

    fun getStripePublishableKey(context: Context): String {
        return context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("stripe_pk", "pk_test_51UGs9qERCsfh1i1Di6n7HvRCVbVwdt3Rh6CSGln2eVjUGCwSdXmRY3Af88zHFm5KOPKY7Smi1ZRZD16vmjKZWvhZ00fL302Or4")
            ?: "pk_test_51UGs9qERCsfh1i1Di6n7HvRCVbVwdt3Rh6CSGln2eVjUGCwSdXmRY3Af88zHFm5KOPKY7Smi1ZRZD16vmjKZWvhZ00fL302Or4"
    }

    fun saveStripePublishableKey(context: Context, key: String) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("stripe_pk", key.trim()).apply()
    }

    fun getStripeSecretKey(context: Context): String {
        return context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("stripe_sk", "")
            ?: ""
    }

    fun saveStripeSecretKey(context: Context, key: String) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("stripe_sk", key.trim()).apply()
    }
}
