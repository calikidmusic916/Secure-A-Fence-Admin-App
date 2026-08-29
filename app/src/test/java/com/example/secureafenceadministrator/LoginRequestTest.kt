package com.example.secureafenceadministrator

import com.example.secureafenceadministrator.data.model.LoginRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginRequestTest {

    @Test
    fun testLoginRequestFields() {
        val request = LoginRequest("admin@example.com", "password123")
        assertEquals("admin@example.com", request.email)
        assertEquals("password123", request.password)
    }
}
