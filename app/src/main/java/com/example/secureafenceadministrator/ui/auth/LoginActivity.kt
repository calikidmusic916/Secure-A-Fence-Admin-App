package com.example.secureafenceadministrator.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.secureafenceadministrator.data.model.LoginRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.databinding.ActivityLoginBinding
import com.example.secureafenceadministrator.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedToken = com.example.secureafenceadministrator.data.network.SessionManager.getToken(this)
        if (!savedToken.isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performLogin(email, password)
            } else {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin(email: String, pass: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.login(LoginRequest(email, pass))
                if (response.isSuccessful && response.body()?.user?.role == "admin" && !response.body()?.token.isNullOrEmpty()) {
                    // Save token and navigate
                    com.example.secureafenceadministrator.data.network.SessionManager.saveToken(this@LoginActivity, response.body()!!.token)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Login Failed: Admin required", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
}
