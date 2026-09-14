package com.example.secureafenceadministrator.data.network

import com.example.secureafenceadministrator.data.model.*
import okhttp3.MultipartBody
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

    @PUT("api/admin/sales/{id}/payment")
    suspend fun updateOrderPayment(
        @Header("Authorization") token: String,
        @Path("id") orderId: String,
        @Body request: OrderPaymentUpdateRequest
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

    @POST("api/admin/sales")
    suspend fun createOrder(
        @Header("Authorization") token: String,
        @Body request: Order
    ): Response<Map<String, Any>>

    @POST("api/orders")
    suspend fun submitCustomQuoteOrder(
        @Header("Authorization") token: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @PUT("api/admin/rentals/{id}/extend")
    suspend fun extendRental(
        @Header("Authorization") token: String,
        @Path("id") rentalId: String,
        @Body request: ExtendRentalRequest
    ): Response<Map<String, Any>>

    @PUT("api/admin/rentals/{id}/checkin")
    suspend fun checkinRental(
        @Header("Authorization") token: String,
        @Path("id") rentalId: String
    ): Response<Map<String, Any>>

    @POST("api/admin/rentals/{id}/invoice")
    suspend fun generateMonthlyRentalInvoice(
        @Header("Authorization") token: String,
        @Path("id") rentalId: String
    ): Response<Map<String, Any>>

    @DELETE("api/admin/rentals/{id}")
    suspend fun deleteRental(
        @Header("Authorization") token: String,
        @Path("id") rentalId: String
    ): Response<Map<String, Any>>

    @POST("api/admin/shipments/pickup")
    suspend fun schedulePickup(
        @Header("Authorization") token: String,
        @Body request: SchedulePickupRequest
    ): Response<Map<String, Any>>

    @GET("api/admin/invoices")
    suspend fun getInvoices(@Header("Authorization") token: String): Response<List<Invoice>>

    @POST("api/admin/invoices")
    suspend fun createInvoice(
        @Header("Authorization") token: String,
        @Body request: CreateInvoiceRequest
    ): Response<Map<String, Any>>

    @POST("api/admin/customers")
    suspend fun createCustomer(
        @Header("Authorization") token: String,
        @Body request: CreateCustomerRequest
    ): Response<Map<String, Any>>

    @GET("api/admin/customers")
    suspend fun getCustomers(@Header("Authorization") token: String): Response<List<Customer>>

    @PUT("api/admin/customers/{id}")
    suspend fun updateCustomer(
        @Header("Authorization") token: String,
        @Path("id") customerId: String,
        @Body request: CreateCustomerRequest
    ): Response<Map<String, Any>>

    @DELETE("api/admin/customers/{id}")
    suspend fun deleteCustomer(
        @Header("Authorization") token: String,
        @Path("id") customerId: String
    ): Response<Map<String, Any>>

    @POST("api/admin/customers/{id}/jobsites")
    suspend fun createJobsite(
        @Header("Authorization") token: String,
        @Path("id") customerId: String,
        @Body jobsite: Jobsite
    ): Response<Map<String, Any>>

    @PUT("api/admin/customers/{id}/jobsites/{jobsiteId}")
    suspend fun updateJobsite(
        @Header("Authorization") token: String,
        @Path("id") customerId: String,
        @Path("jobsiteId") jobsiteId: String,
        @Body jobsite: Jobsite
    ): Response<Map<String, Any>>

    @DELETE("api/admin/customers/{id}/jobsites/{jobsiteId}")
    suspend fun deleteJobsite(
        @Header("Authorization") token: String,
        @Path("id") customerId: String,
        @Path("jobsiteId") jobsiteId: String
    ): Response<Map<String, Any>>

    // --- PRODUCT MANAGEMENT ---

    @GET("api/admin/products")
    suspend fun getAdminProducts(@Header("Authorization") token: String): Response<List<Product>>

    @POST("api/admin/products")
    suspend fun createProduct(
        @Header("Authorization") token: String,
        @Body product: Product
    ): Response<Map<String, Any>>

    @PUT("api/admin/products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") token: String,
        @Path("id") productId: String,
        @Body product: Product
    ): Response<Map<String, Any>>

    @DELETE("api/admin/products/{id}")
    suspend fun deleteProduct(
        @Header("Authorization") token: String,
        @Path("id") productId: String
    ): Response<Map<String, Any>>

    @Multipart
    @POST("api/admin/products/upload")
    suspend fun uploadProductImage(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part
    ): Response<Map<String, Any>>
}
