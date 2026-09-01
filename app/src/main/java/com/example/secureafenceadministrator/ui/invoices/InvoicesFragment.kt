package com.example.secureafenceadministrator.ui.invoices

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.databinding.FragmentInvoicesBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch

class InvoicesFragment : Fragment() {

    private var _binding: FragmentInvoicesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewInvoices.layoutManager = LinearLayoutManager(requireContext())
        loadInvoices()

        binding.btnCreateInvoice.setOnClickListener {
            showCreateInvoiceDialog()
        }

        binding.btnPlaceOrder.setOnClickListener {
            showPlaceOrderDialog()
        }
    }

    private fun showPlaceOrderDialog() {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Place New Order")

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputCustomer = android.widget.EditText(context).apply { hint = "Customer Name" }
        val inputAddress = android.widget.EditText(context).apply { hint = "Delivery Address" }
        val inputQty = android.widget.EditText(context).apply { 
            hint = "Quantity"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(inputCustomer)
        layout.addView(inputAddress)
        layout.addView(inputQty)
        builder.setView(layout)

        builder.setPositiveButton("Place Order") { _, _ ->
            val customer = inputCustomer.text.toString()
            val address = inputAddress.text.toString()
            val qty = inputQty.text.toString().toIntOrNull() ?: 0

            if (customer.isNotEmpty() && address.isNotEmpty() && qty > 0) {
                placeOrder(customer, address, qty)
            } else {
                Toast.makeText(context, "Please fill out all fields correctly", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun placeOrder(customerName: String, address: String, quantity: Int) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                // Simplified order placement using a basic Order model
                val response = ApiClient.instance.createOrder(
                    "Bearer $token",
                    com.example.secureafenceadministrator.data.model.Order(
                        id = "ORD-" + System.currentTimeMillis().toString().takeLast(5),
                        customerId = "cust-manual",
                        customerName = customerName,
                        customerCompany = "Manual Order",
                        customerEmail = "manual@order.com",
                        customerPhone = "000-000-0000",
                        orderType = "sale",
                        items = emptyList(), // In real implementation, include items
                        subtotal = quantity * 50.0,
                        deliveryFee = 50.0,
                        tax = 10.0,
                        totalAmount = (quantity * 50.0) + 60.0,
                        status = "pending",
                        deliveryAddress = address,
                        jobsiteContact = "Manual",
                        deliveryDate = "2026-09-05",
                        createdAt = "2026-08-29"
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Order placed successfully", Toast.LENGTH_SHORT).show()
                    loadInvoices()
                } else {
                    Toast.makeText(context, "Failed to place order", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateInvoiceDialog() {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Create New Invoice")

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputOrderId = android.widget.EditText(context).apply { hint = "Order ID (e.g. ORD-101)" }
        val inputCustomer = android.widget.EditText(context).apply { hint = "Customer Name" }
        val inputAmount = android.widget.EditText(context).apply { 
            hint = "Amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        layout.addView(inputOrderId)
        layout.addView(inputCustomer)
        layout.addView(inputAmount)
        builder.setView(layout)

        builder.setPositiveButton("Create") { _, _ ->
            val orderId = inputOrderId.text.toString()
            val customer = inputCustomer.text.toString()
            val amount = inputAmount.text.toString().toDoubleOrNull() ?: 0.0

            if (orderId.isNotEmpty() && customer.isNotEmpty() && amount > 0.0) {
                createInvoice(orderId, customer, amount)
            } else {
                Toast.makeText(context, "Please fill out all fields correctly", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun createInvoice(orderId: String, customer: String, amount: Double) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.createInvoice(
                    "Bearer $token",
                    com.example.secureafenceadministrator.data.model.CreateInvoiceRequest(
                        orderId = orderId,
                        customerName = customer,
                        amount = amount,
                        status = "unpaid"
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Invoice created successfully", Toast.LENGTH_SHORT).show()
                    loadInvoices()
                } else {
                    Toast.makeText(context, "Failed to create invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInvoices() {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getSalesOrders("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!
                    val adapter = GenericAdapter(
                        orders,
                        titleProvider = { "${it.orderType.uppercase()} #${it.id}" },
                        subtitleProvider = { 
                            val itemsSummary = if (it.items.isNullOrEmpty()) "1x Custom Package" else it.items.joinToString(", ") { item -> "${item.quantity}x ${item.name}" }
                            "Customer: ${it.customerName}\nItems: $itemsSummary\nAmount: $${it.totalAmount}"
                        },
                        statusProvider = { "Status: ${it.status} | Payment: ${it.paymentStatus ?: "Unpaid"}" },
                        onItemClick = { showOrderDetailsDialog(it) }
                    )
                    binding.recyclerViewInvoices.adapter = adapter
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load invoices", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOrderDetailsDialog(order: com.example.secureafenceadministrator.data.model.Order) {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Order & Invoice #${order.id}")

        val detailText = StringBuilder()
        detailText.append("Customer: ${order.customerName}\n")
        detailText.append("Company: ${order.customerCompany}\n")
        detailText.append("Phone: ${order.customerPhone}\n")
        detailText.append("Address: ${order.deliveryAddress}\n")
        detailText.append("Delivery Date: ${order.deliveryDate}\n")
        detailText.append("Status: ${order.status}\n")
        detailText.append("Payment Status: ${order.paymentStatus ?: "Unpaid"} (${order.paymentMethod ?: "None"})\n\n")

        detailText.append("--- Line Items ---\n")
        if (order.items.isNullOrEmpty()) {
            detailText.append("• 1x Custom Temporary Fence Package @ $${order.subtotal}\n")
        } else {
            for (item in order.items) {
                detailText.append("• ${item.quantity}x ${item.name} @ $${item.unitPrice} = $${item.total}\n")
            }
        }
        detailText.append("\n")
        detailText.append("Subtotal: $${order.subtotal}\n")
        detailText.append("Delivery Fee: $${order.deliveryFee}\n")
        detailText.append("Tax (8%): $${order.tax}\n")
        detailText.append("Total: $${order.totalAmount}\n")

        builder.setMessage(detailText.toString())

        builder.setPositiveButton("Print Invoice (PDF)") { _, _ ->
            generatePdfInvoice(order)
        }

        builder.setNeutralButton("Mark Paid") { _, _ ->
            showMarkPaidDialog(order)
        }

        builder.setNegativeButton("Update Status") { _, _ ->
            showUpdateDeliveryStatusDialog(order)
        }

        builder.show()
    }

    private fun showMarkPaidDialog(order: com.example.secureafenceadministrator.data.model.Order) {
        val context = context ?: return
        val options = arrayOf("Cash", "CashApp", "Credit Card", "Unpaid")
        android.app.AlertDialog.Builder(context)
            .setTitle("Select Payment Method")
            .setItems(options) { _, which ->
                val selectedMethod = options[which]
                val newPaymentStatus = if (selectedMethod == "Unpaid") "Unpaid" else "Paid"
                updateOrderPayment(order.id, newPaymentStatus, selectedMethod)
            }
            .show()
    }

    private fun showUpdateDeliveryStatusDialog(order: com.example.secureafenceadministrator.data.model.Order) {
        val context = context ?: return
        val options = arrayOf("Pending Dispatch", "In Route", "Delivered", "On Hold")
        android.app.AlertDialog.Builder(context)
            .setTitle("Select Delivery Status")
            .setItems(options) { _, which ->
                val selectedStatus = options[which]
                updateOrderDeliveryStatus(order.id, selectedStatus)
            }
            .show()
    }

    private fun updateOrderPayment(orderId: String, paymentStatus: String, paymentMethod: String) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateOrderPayment(
                    "Bearer $token",
                    orderId,
                    com.example.secureafenceadministrator.data.model.OrderPaymentUpdateRequest(
                        paymentStatus = paymentStatus,
                        paymentMethod = paymentMethod
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Payment status updated to $paymentStatus", Toast.LENGTH_SHORT).show()
                    loadInvoices()
                } else {
                    Toast.makeText(context, "Failed to update payment", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOrderDeliveryStatus(orderId: String, status: String) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateOrderStatus(
                    "Bearer $token",
                    orderId,
                    com.example.secureafenceadministrator.data.model.StatusUpdateRequest(status = status)
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Status updated to $status", Toast.LENGTH_SHORT).show()
                    loadInvoices()
                } else {
                    Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePdfInvoice(order: com.example.secureafenceadministrator.data.model.Order) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("SECURE-A-FENCE INVOICE", 10f, 25f, paint)

        paint.textSize = 8f
        paint.isFakeBoldText = false
        canvas.drawText("Order ID: ${order.id}", 10f, 45f, paint)
        canvas.drawText("Customer: ${order.customerName}", 10f, 60f, paint)
        canvas.drawText("Company: ${order.customerCompany}", 10f, 75f, paint)
        canvas.drawText("Address: ${order.deliveryAddress}", 10f, 90f, paint)
        canvas.drawText("Date: ${order.deliveryDate}", 10f, 105f, paint)

        canvas.drawText("Items:", 10f, 130f, paint)
        var y = 145f
        if (order.items.isNullOrEmpty()) {
            canvas.drawText("1x Custom Temporary Fence Package @ $${order.subtotal}", 10f, y, paint)
            y += 15f
        } else {
            for (item in order.items) {
                canvas.drawText("${item.quantity}x ${item.name} @ $${item.unitPrice}", 10f, y, paint)
                y += 15f
            }
        }

        y += 10f
        canvas.drawText("Subtotal: $${order.subtotal}", 10f, y, paint)
        y += 15f
        canvas.drawText("Delivery Fee: $${order.deliveryFee}", 10f, y, paint)
        y += 15f
        canvas.drawText("Tax: $${order.tax}", 10f, y, paint)
        y += 15f
        paint.isFakeBoldText = true
        canvas.drawText("Total: $${order.totalAmount}", 10f, y, paint)

        pdfDocument.finishPage(page)

        val file = java.io.File("/sdcard/Download/Invoice_${order.id}.pdf")
        try {
            pdfDocument.writeTo(java.io.FileOutputStream(file))
            Toast.makeText(context, "Invoice saved to Downloads as Invoice_${order.id}.pdf", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
