package com.example.secureafenceadministrator.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val company: String?,
    val phone: String?
)

data class AdminOverviewResponse(
    val metrics: Metrics
)

data class Metrics(
    val totalSalesRevenue: Double,
    val monthlyRentalRevenue: Double,
    val totalPanelsRentedOut: Int,
    val panelsInWarehouse: Int,
    val totalOrdersCount: Int,
    val activeRentalsCount: Int,
    val pendingDispatchesCount: Int
)

data class OrderItem(
    val productId: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val total: Double
)

data class Order(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerCompany: String,
    val customerEmail: String,
    val customerPhone: String,
    val orderType: String, // 'sale' or 'rental'
    val items: List<OrderItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val tax: Double,
    val totalAmount: Double,
    val status: String,
    val deliveryAddress: String,
    val jobsiteContact: String,
    val deliveryDate: String,
    val createdAt: String,
    val paymentStatus: String? = "Unpaid",
    val paymentMethod: String? = "None"
)

data class OrderPaymentUpdateRequest(
    val paymentStatus: String,
    val paymentMethod: String,
    val status: String? = null
)

data class RentalItem(
    val productId: String,
    val name: String,
    val quantity: Int,
    val monthlyUnitPrice: Double,
    val subtotal: Double
)

data class Rental(
    val id: String,
    val orderId: String,
    val customerId: String,
    val customerName: String,
    val customerCompany: String,
    val customerEmail: String,
    val customerPhone: String,
    val jobsiteAddress: String,
    val jobsiteContact: String,
    val startDate: String,
    val endDate: String,
    val monthlyRateTotal: Double,
    val status: String,
    val items: List<RentalItem>,
    val notes: String
)

data class Shipment(
    val id: String,
    val orderId: String,
    val type: String,
    val driverName: String,
    val dispatchDate: String,
    val status: String,
    val destination: String,
    val notes: String
)

data class StatusUpdateRequest(
    val status: String
)

data class ShipmentUpdateRequest(
    val driverName: String?,
    val status: String?,
    val dispatchDate: String?,
    val notes: String?
)

data class ExtendRentalRequest(
    val endDate: String
)

data class SchedulePickupRequest(
    val orderId: String,
    val driverName: String,
    val dispatchDate: String,
    val destination: String,
    val notes: String
)

data class Invoice(
    val id: String,
    val orderId: String,
    val customerName: String,
    val amount: Double,
    val status: String,
    val createdAt: String
)

data class CreateInvoiceRequest(
    val orderId: String,
    val customerName: String,
    val amount: Double,
    val status: String
)

data class Customer(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val company: String?,
    val phone: String?
)

data class CreateCustomerRequest(
    val name: String,
    val email: String,
    val company: String?,
    val phone: String?,
    val role: String? = "customer"
)

data class Product(
    val id: String?,
    val name: String,
    val category: String,
    val type: String,
    val salePrice: Double,
    val rentalPriceMonthly: Double,
    val inStock: Int,
    val rentedCount: Int,
    val description: String,
    val image: String,
    val specs: String,
    val suspended: Boolean = false
)
