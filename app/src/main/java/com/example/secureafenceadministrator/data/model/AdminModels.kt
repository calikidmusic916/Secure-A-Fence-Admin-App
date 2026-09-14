package com.example.secureafenceadministrator.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String? = null,
    val accessToken: String? = null,
    val jwt: String? = null,
    val user: User? = null
) {
    fun fetchToken(): String? = token ?: accessToken ?: jwt
}

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "admin",
    val company: String? = null,
    val phone: String? = null
)

data class AdminOverviewResponse(
    val metrics: Metrics? = null
)

data class Metrics(
    val totalSalesRevenue: Double = 0.0,
    val monthlyRentalRevenue: Double = 0.0,
    val totalPanelsRentedOut: Int = 0,
    val panelsInWarehouse: Int = 0,
    val totalOrdersCount: Int = 0,
    val activeRentalsCount: Int = 0,
    val pendingDispatchesCount: Int = 0
)

data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 0,
    val deliveredQuantity: Int? = null,
    val total: Double = 0.0
)

data class Order(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerCompany: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val orderType: String = "sale",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val tax: Double = 0.0,
    val totalAmount: Double = 0.0,
    val status: String = "Processing",
    val deliveryAddress: String = "",
    val jobsiteContact: String = "",
    val deliveryDate: String = "",
    val createdAt: String = "",
    val paymentStatus: String? = "Unpaid",
    val paymentMethod: String? = "None"
)

data class OrderPaymentUpdateRequest(
    val paymentStatus: String,
    val paymentMethod: String,
    val status: String? = null
)

data class RentalItem(
    val productId: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val monthlyUnitPrice: Double = 0.0,
    val subtotal: Double = 0.0
)

data class Rental(
    val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerCompany: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val jobsiteAddress: String = "",
    val jobsiteContact: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val monthlyRateTotal: Double = 0.0,
    val status: String = "Active",
    val items: List<RentalItem> = emptyList(),
    val notes: String = ""
)

data class Shipment(
    val id: String = "",
    val orderId: String = "",
    val type: String = "",
    val driverName: String = "",
    val dispatchDate: String = "",
    val status: String = "",
    val destination: String = "",
    val notes: String = "",
    val eta: String? = "",
    val deliveryPhotos: List<String>? = emptyList(),
    val deliveredItems: List<OrderItem>? = emptyList()
)

data class StatusUpdateRequest(
    val status: String
)

data class ShipmentUpdateRequest(
    val driverName: String? = null,
    val status: String? = null,
    val dispatchDate: String? = null,
    val notes: String? = null,
    val eta: String? = null,
    val deliveryPhotos: List<String>? = null,
    val deliveredItems: List<OrderItem>? = null
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
    val id: String = "",
    val orderId: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val status: String = "unpaid",
    val createdAt: String = ""
)

data class CreateInvoiceRequest(
    val orderId: String,
    val customerName: String,
    val amount: Double,
    val status: String
)

data class Jobsite(
    val id: String? = null,
    val name: String = "",
    val address: String = "",
    val contactName: String? = null,
    val contactPhone: String? = null,
    val specialInstructions: String? = null,
    val deliveryDistanceMiles: Double = 0.0,
    val activeRentals: List<Rental>? = null
)

data class Customer(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "customer",
    val company: String? = null,
    val phone: String? = null,
    val isTaxable: Boolean = true,
    val businessAddress: String? = null,
    val jobsites: List<Jobsite>? = null
)

data class CreateCustomerRequest(
    val name: String,
    val email: String,
    val company: String?,
    val phone: String?,
    val role: String? = "customer",
    val isTaxable: Boolean = true,
    val businessAddress: String? = null,
    val password: String? = null,
    val jobsites: List<Jobsite>? = null
)

data class Product(
    val id: String? = null,
    val name: String = "",
    val category: String = "sales",
    val type: String = "panel",
    val salePrice: Double = 0.0,
    val rentalPriceMonthly: Double = 0.0,
    val inStock: Int = 0,
    val rentedCount: Int = 0,
    val description: String = "",
    val image: String = "",
    val specs: String = "",
    val suspended: Boolean = false,
    val isRental: Boolean = true,
    val isPurchase: Boolean = true
)
