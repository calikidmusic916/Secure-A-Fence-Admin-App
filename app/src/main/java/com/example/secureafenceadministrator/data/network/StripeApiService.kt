package com.example.secureafenceadministrator.data.network

import retrofit2.Response
import retrofit2.http.*

interface StripeApiService {

    @FormUrlEncoded
    @POST("v1/payment_intents")
    suspend fun createPaymentIntent(
        @Header("Authorization") bearerToken: String,
        @Field("amount") amountCents: Long,
        @Field("currency") currency: String = "usd",
        @Field("description") description: String,
        @Field("receipt_email") receiptEmail: String?,
        @Field("metadata[order_id]") orderId: String,
        @Field("metadata[business]") businessName: String = "Secure-A-Fence Rentals & Sales"
    ): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("v1/payment_intents/{id}/confirm")
    suspend fun confirmPaymentIntent(
        @Header("Authorization") bearerToken: String,
        @Path("id") paymentIntentId: String,
        @Field("payment_method") paymentMethodId: String
    ): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("v1/customers")
    suspend fun createStripeCustomer(
        @Header("Authorization") bearerToken: String,
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("description") description: String = "Secure-A-Fence Client"
    ): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("v1/invoices")
    suspend fun createStripeInvoice(
        @Header("Authorization") bearerToken: String,
        @Field("customer") customerId: String,
        @Field("collection_method") collectionMethod: String = "send_invoice",
        @Field("days_until_due") daysUntilDue: Int = 30,
        @Field("description") description: String = "Secure-A-Fence Fence Rental/Sale Invoice"
    ): Response<Map<String, Any>>

    @GET("v1/payment_intents")
    suspend fun listPaymentIntents(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 20
    ): Response<Map<String, Any>>
}
