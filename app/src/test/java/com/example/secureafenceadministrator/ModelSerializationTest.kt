package com.example.secureafenceadministrator

import com.example.secureafenceadministrator.data.model.AdminOverviewResponse
import com.example.secureafenceadministrator.data.model.LoginResponse
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.Rental
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelSerializationTest {

    private val gson = Gson()

    @Test
    fun testLoginResponseDeserialization() {
        val json = """
            {
              "token": "test-jwt-token-123",
              "user": {
                "id": "u1",
                "name": "Admin User",
                "email": "admin@example.com",
                "role": "admin",
                "company": "Secure Fence Co",
                "phone": "555-0199"
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, LoginResponse::class.java)
        assertEquals("test-jwt-token-123", response.token)
        assertEquals("u1", response.user?.id)
        assertEquals("Admin User", response.user?.name)
        assertEquals("admin", response.user?.role)
    }

    @Test
    fun testAdminOverviewDeserialization() {
        val json = """
            {
              "metrics": {
                "totalSalesRevenue": 50000.0,
                "monthlyRentalRevenue": 12000.0,
                "totalPanelsRentedOut": 150,
                "panelsInWarehouse": 350,
                "totalOrdersCount": 25,
                "activeRentalsCount": 10,
                "pendingDispatchesCount": 3
              }
            }
        """.trimIndent()

        val overview = gson.fromJson(json, AdminOverviewResponse::class.java)
        assertNotNull(overview.metrics)
        assertEquals(50000.0, overview.metrics?.totalSalesRevenue ?: 0.0, 0.001)
        assertEquals(350, overview.metrics?.panelsInWarehouse)
        assertEquals(10, overview.metrics?.activeRentalsCount)
    }
}
