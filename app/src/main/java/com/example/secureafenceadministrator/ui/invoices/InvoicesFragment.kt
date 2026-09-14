package com.example.secureafenceadministrator.ui.invoices

import android.app.AlertDialog
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.CreateInvoiceRequest
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.OrderItem
import com.example.secureafenceadministrator.data.model.OrderPaymentUpdateRequest
import com.example.secureafenceadministrator.data.model.Product
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.model.StatusUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogCartCheckoutBinding
import com.example.secureafenceadministrator.databinding.DialogOrderCatalogBinding
import com.example.secureafenceadministrator.databinding.FragmentInvoicesBinding
import com.example.secureafenceadministrator.databinding.ItemProductCatalogBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            showCatalogOrderDialog()
        }
    }

    private fun showCatalogOrderDialog() {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                var productsList = listOf<Product>()
                val response = ApiClient.instance.getAdminProducts("Bearer $token")
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    productsList = response.body()!!
                }

                if (productsList.isEmpty()) {
                    productsList = listOf(
                        Product("prod-1", "Refurbished Temporary Fence Panel (6' x 12')", "sales", "panel", 65.0, 15.0, 150, 0, "Galvanized tubular steel frame perimeter panel", "/assets/banner.jpg", "", false, true, true),
                        Product("prod-2", "Refurbished Temporary Fence Panel (6' x 10')", "sales", "panel", 50.0, 12.0, 120, 0, "Heavy-duty perimeter panel for compact sites", "/assets/banner.jpg", "", false, true, true),
                        Product("prod-3", "Flat Stand (Standard Tubular Steel Base)", "sales", "stand", 10.0, 3.0, 300, 0, "Tubular steel base plate for panel support", "/assets/logo.jpg", "", false, true, true),
                        Product("prod-4", "Safety Clamp / Panel Connector Clip", "sales", "clip", 5.0, 1.0, 500, 0, "High-tensile steel clamp coupler", "/assets/logo.jpg", "", false, true, true),
                        Product("prod-5", "Privacy Windscreen Roll (6' x 50')", "sales", "accessory", 85.0, 25.0, 40, 0, "HDPE mesh privacy screen for dust & wind control", "/assets/banner.jpg", "", false, true, true)
                    )
                }

                openCatalogDialogWithProducts(productsList)
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading catalog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCatalogDialogWithProducts(products: List<Product>) {
        val context = context ?: return
        val dialogBinding = DialogOrderCatalogBinding.inflate(LayoutInflater.from(context))

        val cartMap = mutableMapOf<Product, Int>()
        var isRentalOrder = false

        dialogBinding.rvCatalogProducts.layoutManager = LinearLayoutManager(context)

        fun updateCartSummaryBar() {
            val totalItems = cartMap.values.sum()
            val subtotal = cartMap.entries.sumOf { (product, qty) ->
                val price = if (isRentalOrder) product.rentalPriceMonthly else product.salePrice
                price * qty
            }
            dialogBinding.tvCartItemCount.text = "🛒 Cart: $totalItems item(s)"
            dialogBinding.tvCartTotalAmount.text = "Subtotal: $" + String.format("%.2f", subtotal)
        }

        class CatalogAdapter : RecyclerView.Adapter<CatalogAdapter.ViewHolder>() {
            inner class ViewHolder(val binding: ItemProductCatalogBinding) : RecyclerView.ViewHolder(binding.root)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val binding = ItemProductCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ViewHolder(binding)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val product = products[position]
                holder.binding.tvCatalogTitle.text = product.name
                holder.binding.tvCatalogDesc.text = product.description.ifEmpty { "High quality temporary fencing equipment" }
                holder.binding.tvCatalogStock.text = "In Stock: ${product.inStock} units"

                val priceVal = if (isRentalOrder) product.rentalPriceMonthly else product.salePrice
                val priceUnit = if (isRentalOrder) "/ mo" else "/ unit"
                holder.binding.tvCatalogPrice.text = "$" + String.format("%.2f", priceVal) + " " + priceUnit

                val currentQty = cartMap[product] ?: 0
                holder.binding.tvItemCartQty.text = currentQty.toString()

                if (!product.image.isNullOrEmpty()) {
                    val fullUrl = if (product.image.startsWith("/")) "https://secure-a-fence-backend.onrender.com${product.image}" else product.image
                    holder.binding.ivCatalogImage.load(fullUrl) {
                        placeholder(R.drawable.banner)
                        error(R.drawable.banner)
                    }
                } else {
                    holder.binding.ivCatalogImage.setImageResource(R.drawable.banner)
                }

                holder.binding.btnQtyPlus.setOnClickListener {
                    val count = (cartMap[product] ?: 0) + 1
                    cartMap[product] = count
                    holder.binding.tvItemCartQty.text = count.toString()
                    updateCartSummaryBar()
                }

                holder.binding.btnQtyMinus.setOnClickListener {
                    val count = (cartMap[product] ?: 0) - 1
                    if (count > 0) {
                        cartMap[product] = count
                        holder.binding.tvItemCartQty.text = count.toString()
                    } else {
                        cartMap.remove(product)
                        holder.binding.tvItemCartQty.text = "0"
                    }
                    updateCartSummaryBar()
                }

                holder.binding.btnAddToCart.setOnClickListener {
                    val count = (cartMap[product] ?: 0) + 1
                    cartMap[product] = count
                    holder.binding.tvItemCartQty.text = count.toString()
                    updateCartSummaryBar()
                    Toast.makeText(holder.itemView.context, "Added 1x ${product.name} to Cart", Toast.LENGTH_SHORT).show()
                }
            }

            override fun getItemCount(): Int = products.size
        }

        val catalogAdapter = CatalogAdapter()
        dialogBinding.rvCatalogProducts.adapter = catalogAdapter

        dialogBinding.rgOrderType.setOnCheckedChangeListener { _, checkedId ->
            isRentalOrder = (checkedId == R.id.rbRentalOrder)
            catalogAdapter.notifyDataSetChanged()
            updateCartSummaryBar()
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setNegativeButton("Close", null)
            .create()

        dialogBinding.btnCheckoutCart.setOnClickListener {
            if (cartMap.isEmpty() || cartMap.values.sum() == 0) {
                Toast.makeText(context, "Please add at least one item to your cart before checking out", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            showCartCheckoutDialog(isRentalOrder, cartMap)
        }

        dialog.show()
    }

    private fun showCartCheckoutDialog(isRentalOrder: Boolean, cartMap: Map<Product, Int>) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        val checkoutBinding = DialogCartCheckoutBinding.inflate(LayoutInflater.from(context))

        val orderTypeStr = if (isRentalOrder) "rental" else "sale"
        checkoutBinding.tvCheckoutTitle.text = "🛒 Review Order (${orderTypeStr.uppercase()})"

        val itemsSummary = StringBuilder()
        var subtotal = 0.0

        for ((product, qty) in cartMap) {
            val unitPrice = if (isRentalOrder) product.rentalPriceMonthly else product.salePrice
            val lineTotal = unitPrice * qty
            subtotal += lineTotal
            itemsSummary.append("• ${qty}x ${product.name} @ $${String.format("%.2f", unitPrice)} = $${String.format("%.2f", lineTotal)}\n")
        }

        checkoutBinding.tvCheckoutItemsList.text = itemsSummary.toString().trim()

        val deliveryFee = if (subtotal > 0) 50.0 else 0.0
        val tax = Math.round(subtotal * 0.08 * 100.0) / 100.0
        val grandTotal = subtotal + deliveryFee + tax

        checkoutBinding.tvCheckoutSubtotal.text = "Subtotal: $" + String.format("%.2f", subtotal)
        checkoutBinding.tvCheckoutDeliveryFee.text = "Delivery Transport Fee: $" + String.format("%.2f", deliveryFee)
        checkoutBinding.tvCheckoutTax.text = "Tax (8%): $" + String.format("%.2f", tax)
        checkoutBinding.tvCheckoutGrandTotal.text = "Grand Total: $" + String.format("%.2f", grandTotal)

        val dialog = AlertDialog.Builder(context)
            .setView(checkoutBinding.root)
            .setNegativeButton("Back to Cart", null)
            .create()

        checkoutBinding.btnSubmitOrderAndDispatch.setOnClickListener {
            val customerName = checkoutBinding.etCheckoutCustomerName.text.toString().trim()
            val company = checkoutBinding.etCheckoutCompany.text.toString().trim().ifEmpty { "Direct Client" }
            val phone = checkoutBinding.etCheckoutPhone.text.toString().trim().ifEmpty { "(279) 261-3890" }
            val email = checkoutBinding.etCheckoutEmail.text.toString().trim().ifEmpty { "sales@order.com" }
            val address = checkoutBinding.etCheckoutDeliveryAddress.text.toString().trim()

            if (customerName.isEmpty() || address.isEmpty()) {
                Toast.makeText(context, "Please enter Customer Name and Delivery Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val orderId = "ORD-" + (1000..9999).random()

            val orderItems = cartMap.map { (product, qty) ->
                val unitPrice = if (isRentalOrder) product.rentalPriceMonthly else product.salePrice
                OrderItem(
                    productId = product.id ?: ("prod-" + System.currentTimeMillis()),
                    name = product.name,
                    unitPrice = unitPrice,
                    quantity = qty,
                    deliveredQuantity = qty,
                    total = unitPrice * qty
                )
            }

            val newOrder = Order(
                id = orderId,
                customerId = "cust-" + System.currentTimeMillis(),
                customerName = customerName,
                customerCompany = company,
                customerEmail = email,
                customerPhone = phone,
                orderType = orderTypeStr,
                items = orderItems,
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                tax = tax,
                totalAmount = grandTotal,
                status = "Processing",
                deliveryAddress = address,
                jobsiteContact = customerName,
                deliveryDate = today,
                createdAt = today,
                paymentStatus = "Unpaid",
                paymentMethod = "None"
            )

            lifecycleScope.launch {
                try {
                    // 1. Create order in backend with status Processing
                    ApiClient.instance.createOrder("Bearer $token", newOrder)

                    // 2. Dispatch to Shipping Queue with status Processing
                    ApiClient.instance.schedulePickup(
                        "Bearer $token",
                        SchedulePickupRequest(
                            orderId = orderId,
                            driverName = "Dispatcher Fleet",
                            dispatchDate = today,
                            destination = address,
                            notes = "Status: PROCESSING - Order placed via Catalog (${orderItems.size} line items)"
                        )
                    )

                    // 3. Auto-generate Invoice for Order
                    ApiClient.instance.createInvoice(
                        "Bearer $token",
                        CreateInvoiceRequest(
                            orderId = orderId,
                            customerName = customerName,
                            amount = grandTotal,
                            status = "unpaid"
                        )
                    )

                    Toast.makeText(context, "🎉 Order #$orderId placed successfully!\nDispatched to Shipping Queue (Processing).", Toast.LENGTH_LONG).show()

                    dialog.dismiss()
                    loadInvoices()

                } catch (e: Exception) {
                    Toast.makeText(context, "Order created: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadInvoices()
                }
            }
        }

        dialog.show()
    }

    private fun showCreateInvoiceDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Create New Invoice")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputOrderId = EditText(context).apply { hint = "Order ID (e.g. ORD-101)" }
        val inputCustomer = EditText(context).apply { hint = "Customer Name" }
        val inputAmount = EditText(context).apply {
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
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
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.createInvoice(
                    "Bearer $token",
                    CreateInvoiceRequest(
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
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SessionManager.clearSession(context)
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
                            val itemsSummary = if (it.items.isNullOrEmpty()) "1x Custom Package" else it.items.joinToString(", ") { item ->
                                val delText = if (item.deliveredQuantity != null) " (Delivered: ${item.deliveredQuantity})" else ""
                                "${item.quantity}x ${item.name}$delText"
                            }
                            "Customer: ${it.customerName}\nItems: $itemsSummary\nAmount: $${it.totalAmount}"
                        },
                        statusProvider = { "Status: ${it.status} | Payment: ${it.paymentStatus ?: "Unpaid"}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showOrderDetailsDialog(it) }
                    )
                    binding.recyclerViewInvoices.adapter = adapter
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load invoices", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOrderDetailsDialog(order: Order) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Order & Invoice #${order.id}")

        val detailText = StringBuilder()
        detailText.append("Customer: ${order.customerName}\n")
        detailText.append("Company: ${order.customerCompany}\n")
        detailText.append("Phone: ${order.customerPhone}\n")
        detailText.append("Address: ${order.deliveryAddress}\n")
        detailText.append("Delivery Date: ${order.deliveryDate}\n")
        detailText.append("Status: ${order.status}\n")
        detailText.append("Payment Status: ${order.paymentStatus ?: "Unpaid"} (${order.paymentMethod ?: "None"})\n\n")

        detailText.append("--- Line Items (Ordered vs Delivered) ---\n")
        if (order.items.isNullOrEmpty()) {
            detailText.append("• 1x Custom Temporary Fence Package @ $${order.subtotal}\n")
        } else {
            for (item in order.items) {
                val delQty = item.deliveredQuantity ?: item.quantity
                detailText.append("• ${item.name}\n  Ordered: ${item.quantity} | Delivered: $delQty @ $${item.unitPrice} = $${item.total}\n")
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

    private fun showMarkPaidDialog(order: Order) {
        val context = context ?: return
        val options = arrayOf("Cash", "CashApp", "Credit Card", "Unpaid")
        AlertDialog.Builder(context)
            .setTitle("Select Payment Method")
            .setItems(options) { _, which ->
                val selectedMethod = options[which]
                val newPaymentStatus = if (selectedMethod == "Unpaid") "Unpaid" else "Paid"
                updateOrderPayment(order.id, newPaymentStatus, selectedMethod)
            }
            .show()
    }

    private fun showUpdateDeliveryStatusDialog(order: Order) {
        val context = context ?: return
        val options = arrayOf("Pending Dispatch", "In Route", "Delivered", "On Hold")
        AlertDialog.Builder(context)
            .setTitle("Select Delivery Status")
            .setItems(options) { _, which ->
                val selectedStatus = options[which]
                updateOrderDeliveryStatus(order.id, selectedStatus)
            }
            .show()
    }

    private fun updateOrderPayment(orderId: String, paymentStatus: String, paymentMethod: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateOrderPayment(
                    "Bearer $token",
                    orderId,
                    OrderPaymentUpdateRequest(
                        paymentStatus = paymentStatus,
                        paymentMethod = paymentMethod
                    )
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
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateOrderStatus(
                    "Bearer $token",
                    orderId,
                    StatusUpdateRequest(status = status)
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

    private fun generatePdfInvoice(order: Order) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Header
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("SECURE-A-FENCE RENTALS & SALES", 40f, 60f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("123 Perimeter Way, Sacramento, CA 95814 | Phone: 916-573-9543", 40f, 85f, paint)
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
        canvas.drawText("Ord Qty", 310f, 260f, paint)
        canvas.drawText("Del Qty", 370f, 260f, paint)
        canvas.drawText("Unit Price", 430f, 260f, paint)
        canvas.drawText("Total", 500f, 260f, paint)
        canvas.drawLine(40f, 275f, 555f, 275f, paint)

        // Items
        paint.isFakeBoldText = false
        var y = 300f
        if (order.items.isNullOrEmpty()) {
            canvas.drawText("Custom Temporary Fence Package", 40f, y, paint)
            canvas.drawText("1", 310f, y, paint)
            canvas.drawText("1", 370f, y, paint)
            canvas.drawText("$${order.subtotal}", 430f, y, paint)
            canvas.drawText("$${order.subtotal}", 500f, y, paint)
            y += 25f
        } else {
            for (item in order.items) {
                canvas.drawText(item.name.take(28), 40f, y, paint)
                canvas.drawText(item.quantity.toString(), 310f, y, paint)
                val delQty = item.deliveredQuantity ?: item.quantity
                canvas.drawText(delQty.toString(), 370f, y, paint)
                canvas.drawText("$${item.unitPrice}", 430f, y, paint)
                canvas.drawText("$${item.total}", 500f, y, paint)
                y += 25f
            }
        }

        // Totals
        canvas.drawLine(300f, y + 10, 555f, y + 10, paint)
        y += 40f
        canvas.drawText("Subtotal:", 430f, y, paint)
        canvas.drawText("$${order.subtotal}", 500f, y, paint)
        y += 25f
        canvas.drawText("Delivery Fee:", 430f, y, paint)
        canvas.drawText("$${order.deliveryFee}", 500f, y, paint)
        y += 25f
        canvas.drawText("Tax (8%):", 430f, y, paint)
        canvas.drawText("$${order.tax}", 500f, y, paint)
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Total:", 430f, y, paint)
        canvas.drawText("$${order.totalAmount}", 500f, y, paint)

        // Footer
        paint.isFakeBoldText = false
        canvas.drawText("Thank you for choosing Secure-A-Fence Temporary Perimeter Protection!", 40f, 780f, paint)

        pdfDocument.finishPage(page)

        val file = File("/sdcard/Download/Invoice_${order.id}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
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
