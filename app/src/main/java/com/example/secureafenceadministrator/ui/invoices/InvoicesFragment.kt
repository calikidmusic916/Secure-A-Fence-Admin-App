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
                val response = ApiClient.instance.updateOrderStatus(
                    "Bearer $token",
                    orderId,
                    com.example.secureafenceadministrator.data.model.StatusUpdateRequest(status = "Paid ($paymentMethod)")
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Payment status updated to $paymentStatus via $paymentMethod", Toast.LENGTH_SHORT).show()
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
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Header
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("SECURE-A-FENCE RENTALS & SALES", 40f, 60f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("123 Perimeter Way, Sacramento, CA 95814 | Phone: (279) 261-3890", 40f, 85f, paint)
        canvas.drawText("Web: secure-a-fence.com | Email: support@secureafence.com", 40f, 100f, paint)
        canvas.drawLine(40f, 115f, 555f, 115f, paint)

        // Metadata
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE / ORDER SUMMARY", 40f, 150f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Order ID: ${order.id}", 40f, 175f, paint)
        canvas.drawText("Date: ${order.deliveryDate}", 40f, 190f, paint)
        canvas.drawText("Status: ${order.status}", 40f, 205f, paint)
        canvas.drawText("Payment: ${order.paymentStatus ?: "Unpaid"} (${order.paymentMethod ?: "None"})", 40f, 220f, paint)

        // Bill To
        paint.isFakeBoldText = true
        canvas.drawText("BILL TO:", 300f, 150f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(order.customerName, 300f, 175f, paint)
        canvas.drawText(order.customerCompany, 300f, 190f, paint)
        canvas.drawText(order.deliveryAddress, 300f, 205f, paint)

        // Table Header
        paint.isFakeBoldText = true
        canvas.drawLine(40f, 240f, 555f, 240f, paint)
        canvas.drawText("Item Description", 40f, 260f, paint)
        canvas.drawText("Qty", 350f, 260f, paint)
        canvas.drawText("Unit Price", 420f, 260f, paint)
        canvas.drawText("Total", 500f, 260f, paint)
        canvas.drawLine(40f, 275f, 555f, 275f, paint)

        // Items
        paint.isFakeBoldText = false
        var y = 300f
        if (order.items.isNullOrEmpty()) {
            canvas.drawText("Custom Temporary Fence Package", 40f, y, paint)
            canvas.drawText("1", 350f, y, paint)
            canvas.drawText("$${order.subtotal}", 420f, y, paint)
            canvas.drawText("$${order.subtotal}", 500f, y, paint)
            y += 25f
        } else {
            for (item in order.items) {
                canvas.drawText(item.name.take(30), 40f, y, paint)
                canvas.drawText(item.quantity.toString(), 350f, y, paint)
                canvas.drawText("$${item.unitPrice}", 420f, y, paint)
                canvas.drawText("$${item.total}", 500f, y, paint)
                y += 25f
            }
        }

        // Totals
        canvas.drawLine(300f, y + 10, 555f, y + 10, paint)
        y += 40f
        canvas.drawText("Subtotal:", 420f, y, paint)
        canvas.drawText("$${order.subtotal}", 500f, y, paint)
        y += 25f
        canvas.drawText("Delivery Fee:", 420f, y, paint)
        canvas.drawText("$${order.deliveryFee}", 500f, y, paint)
        y += 25f
        canvas.drawText("Tax (8%):", 420f, y, paint)
        canvas.drawText("$${order.tax}", 500f, y, paint)
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Total:", 420f, y, paint)
        canvas.drawText("$${order.totalAmount}", 500f, y, paint)

        // Footer
        paint.isFakeBoldText = false
        canvas.drawText("Thank you for choosing Secure-A-Fence Temporary Perimeter Protection!", 40f, 780f, paint)

        pdfDocument.finishPage(page)

        val file = java.io.File("/sdcard/Download/Invoice_${order.id}.pdf")
        try {
            pdfDocument.writeTo(java.io.FileOutputStream(file))
            Toast.makeText(context, "Invoice exported: Invoice_${order.id}.pdf", Toast.LENGTH_LONG).show()
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
