package com.example.secureafenceadministrator.data.network

import com.example.secureafenceadministrator.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AdminApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<User>

    @GET("api/admin/overview")
    suspend fun getAdminOverview(@Header("Authorization") token: String): Response<AdminOverviewResponse>

    @GET("api/admin/sales")
    suspend fun getSalesOrders(@Header("Authorization") token: String): Response<List<Order>>

    @PUT("api/admin/sales/{id}/status")
    suspend fun updateOrderStatus(
        @Header("Authorization") token: String,
        @Path("id") orderId: String,
        @Body request: StatusUpdateRequest
    ): Response<Map<String, Any>>

    @GET("api/admin/rentals")
    suspend fun getRentals(@Header("Authorization") token: String): Response<List<Rental>>

    @PUT("api/admin/rentals/{id}/checkin")
    suspend fun checkInRental(
        @Header("Authorization") token: String,
        @Path("id") rentalId: String
    ): Response<Map<String, Any>>

    @GET("api/admin/shipments")
    suspend fun getShipments(@Header("Authorization") token: String): Response<List<Shipment>>

    @PUT("api/admin/shipments/{id}")
    suspend fun updateShipment(
        @Header("Authorization") token: String,
        @Path("id") shipmentId: String,
        @Body request: ShipmentUpdateRequest
    ): Response<Map<String, Any>>
}
