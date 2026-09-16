package com.example.secureafenceadministrator.ui.invoices

import android.app.AlertDialog
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.CreateInvoiceRequest
import com.example.secureafenceadministrator.data.model.Customer
import com.example.secureafenceadministrator.data.model.Jobsite
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.OrderItem
import com.example.secureafenceadministrator.data.model.OrderPaymentUpdateRequest
import com.example.secureafenceadministrator.data.model.Product
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.model.StatusUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogCartCheckoutBinding
import com.example.secureafenceadministrator.databinding.DialogInvoiceDetailsBinding
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

    private var salesOrdersList = mutableListOf<Order>()
    private var completedInvoicesList = mutableListOf<Order>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewSalesOrders.layoutManager = LinearLayoutManager(requireContext())
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
            dialogBinding.tvCartTotalAmount.text = "Subtotal: $" + String.format(Locale.US, "%.2f", subtotal)
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
                holder.binding.tvCatalogPrice.text = "$" + String.format(Locale.US, "%.2f", priceVal) + " " + priceUnit

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

        lifecycleScope.launch {
            try {
                val customersResponse = ApiClient.instance.getCustomers("Bearer $token")
                val customersList = if (customersResponse.isSuccessful && !customersResponse.body().isNullOrEmpty()) {
                    customersResponse.body()!!
                } else {
                    emptyList()
                }

                if (customersList.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("⚠️ Customer Account Required")
                        .setMessage("Customer must already exist in the system to place an order. Please navigate to the Customers tab to create the customer account first.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                openCheckoutWithCustomers(isRentalOrder, cartMap, customersList)

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading customers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCheckoutWithCustomers(
        isRentalOrder: Boolean,
        cartMap: Map<Product, Int>,
        customersList: List<Customer>
    ) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        val checkoutBinding = DialogCartCheckoutBinding.inflate(LayoutInflater.from(context))

        val orderTypeStr = if (isRentalOrder) "rental" else "sale"
        checkoutBinding.tvCheckoutTitle.text = "🛒 Review Order (${orderTypeStr.uppercase()})"

        val customerNamesList = customersList.map {
            val company = if (!it.company.isNullOrEmpty()) " (${it.company})" else ""
            "${it.name} - ${it.email}$company"
        }.toTypedArray()

        checkoutBinding.spCheckoutCustomer.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, customerNamesList)

        var selectedCustomer = customersList[0]

        val itemsSummary = StringBuilder()
        var subtotal = 0.0

        for ((product, qty) in cartMap) {
            val unitPrice = if (isRentalOrder) product.rentalPriceMonthly else product.salePrice
            val lineTotal = unitPrice * qty
            subtotal += lineTotal
            itemsSummary.append("• ${qty}x ${product.name} @ $${String.format(Locale.US, "%.2f", unitPrice)} = $${String.format(Locale.US, "%.2f", lineTotal)}\n")
        }

        checkoutBinding.tvCheckoutItemsList.text = itemsSummary.toString().trim()

        val deliveryFee = if (subtotal > 0) 50.0 else 0.0
        var currentTax = 0.0
        var currentGrandTotal = subtotal + deliveryFee

        fun updateFinancials(customer: Customer) {
            selectedCustomer = customer
            currentTax = if (customer.isTaxable) Math.round(subtotal * 0.08 * 100.0) / 100.0 else 0.0
            currentGrandTotal = subtotal + deliveryFee + currentTax

            checkoutBinding.tvCheckoutSubtotal.text = "Subtotal: $" + String.format(Locale.US, "%.2f", subtotal)
            checkoutBinding.tvCheckoutDeliveryFee.text = "Delivery Transport Fee: $" + String.format(Locale.US, "%.2f", deliveryFee)
            checkoutBinding.tvCheckoutTax.text = if (customer.isTaxable) "Tax (8%): $" + String.format(Locale.US, "%.2f", currentTax) else "Tax: $0.00 (TAX EXEMPT)"
            checkoutBinding.tvCheckoutGrandTotal.text = "Grand Total: $" + String.format(Locale.US, "%.2f", currentGrandTotal)
        }

        fun populateCustomerFields(customer: Customer) {
            updateFinancials(customer)
            checkoutBinding.etCheckoutCustomerName.setText(customer.name)
            checkoutBinding.etCheckoutCompany.setText(customer.company.orEmpty())
            checkoutBinding.etCheckoutPhone.setText(customer.phone.orEmpty())
            checkoutBinding.etCheckoutEmail.setText(customer.email)
            checkoutBinding.etCheckoutDeliveryAddress.setText(customer.businessAddress.orEmpty())

            val jobsites = customer.jobsites ?: emptyList()
            if (jobsites.isNotEmpty()) {
                val siteNames = jobsites.map { "${it.name} (${it.address})" }.toTypedArray()
                checkoutBinding.spCheckoutJobsite.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, siteNames)
            } else {
                checkoutBinding.spCheckoutJobsite.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("No registered jobsites"))
            }
        }

        populateCustomerFields(selectedCustomer)

        checkoutBinding.spCheckoutCustomer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                populateCustomerFields(customersList[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        checkoutBinding.spCheckoutJobsite.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val jobsites = selectedCustomer.jobsites ?: emptyList()
                if (position in jobsites.indices) {
                    val jobsite = jobsites[position]
                    checkoutBinding.etCheckoutDeliveryAddress.setText(jobsite.address)
                    if (!jobsite.contactName.isNullOrEmpty()) checkoutBinding.etCheckoutCustomerName.setText(jobsite.contactName)
                    if (!jobsite.contactPhone.isNullOrEmpty()) checkoutBinding.etCheckoutPhone.setText(jobsite.contactPhone)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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
                customerId = selectedCustomer.id,
                customerName = customerName,
                customerCompany = company,
                customerEmail = email,
                customerPhone = phone,
                orderType = orderTypeStr,
                items = orderItems,
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                tax = currentTax,
                totalAmount = currentGrandTotal,
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
                    // Create order in backend (backend automatically generates active dispatch shipment with matching Order ID)
                    ApiClient.instance.createOrder("Bearer $token", newOrder)

                    Toast.makeText(context, "🎉 Order #$orderId placed & added to Active Dispatches!", Toast.LENGTH_LONG).show()

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
                    val allOrders = response.body()!!

                    salesOrdersList = allOrders.filter {
                        !it.status.equals("Delivered", ignoreCase = true) &&
                        !it.status.equals("Picked Up / Returned", ignoreCase = true) &&
                        !it.status.equals("Completed", ignoreCase = true)
                    }.toMutableList()

                    completedInvoicesList = allOrders.filter {
                        it.status.equals("Delivered", ignoreCase = true) ||
                        it.status.equals("Picked Up / Returned", ignoreCase = true) ||
                        it.status.equals("Completed", ignoreCase = true)
                    }.toMutableList()

                    binding.tvSalesOrdersHeader.text = "📦 Active Sales Orders (Pending Delivery / Pickup) (${salesOrdersList.size})"
                    binding.tvCompletedInvoicesHeader.text = "📄 Invoices (Delivered & Completed Orders) (${completedInvoicesList.size})"

                    val salesAdapter = GenericAdapter(
                        salesOrdersList,
                        titleProvider = { "${it.orderType.uppercase()} #${it.id}" },
                        subtitleProvider = { 
                            val itemsSummary = if (it.items.isNullOrEmpty()) "1x Custom Package" else it.items.joinToString(", ") { item -> "${item.quantity}x ${item.name}" }
                            "Customer: ${it.customerName}\nItems: $itemsSummary\nAmount: $${it.totalAmount}"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()} [PENDING DELIVERY] | Payment: ${it.paymentStatus ?: "Unpaid"}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showOrderDetailsDialog(it) }
                    )
                    binding.recyclerViewSalesOrders.adapter = salesAdapter

                    val completedAdapter = GenericAdapter(
                        completedInvoicesList,
                        titleProvider = { "${it.orderType.uppercase()} #${it.id}" },
                        subtitleProvider = { 
                            val itemsSummary = if (it.items.isNullOrEmpty()) "1x Custom Package" else it.items.joinToString(", ") { item ->
                                val delText = if (item.deliveredQuantity != null) " (Delivered: ${item.deliveredQuantity})" else ""
                                "${item.quantity}x ${item.name}$delText"
                            }
                            "Customer: ${it.customerName}\nItems: $itemsSummary\nAmount: $${it.totalAmount}"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()} [INVOICED] | Payment: ${it.paymentStatus ?: "Unpaid"}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showOrderDetailsDialog(it) }
                    )
                    binding.recyclerViewInvoices.adapter = completedAdapter

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
        val token = SessionManager.getToken(context) ?: return
        val dialogBinding = DialogInvoiceDetailsBinding.inflate(LayoutInflater.from(context))

        dialogBinding.tvInvoiceDialogTitle.text = "📄 ${order.orderType.uppercase()} Details #${order.id}"

        val isInvoiced = order.status.equals("Delivered", ignoreCase = true) || order.status.equals("Picked Up / Returned", ignoreCase = true) || order.status.equals("Completed", ignoreCase = true)
        val statusText = if (isInvoiced) "Status: ${order.status.uppercase()} (OFFICIALLY INVOICED - LOCKED)" else "Status: ${order.status.uppercase()} (Invoice Issued Upon Delivery/Pickup)"
        dialogBinding.tvInvoiceStatusBadge.text = statusText

        dialogBinding.etInvoiceCustomerName.setText(order.customerName)
        dialogBinding.etInvoiceCompany.setText(order.customerCompany)
        dialogBinding.etInvoicePhone.setText(order.customerPhone)
        dialogBinding.etInvoiceEmail.setText(order.customerEmail)
        dialogBinding.etInvoiceDeliveryAddress.setText(order.deliveryAddress)

        dialogBinding.cbInvoiceTaxable.isChecked = order.tax > 0

        val paymentStatuses = arrayOf("Unpaid", "Paid")
        dialogBinding.spPaymentStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, paymentStatuses)
        val pIndex = paymentStatuses.indexOfFirst { it.equals(order.paymentStatus, ignoreCase = true) }
        if (pIndex >= 0) dialogBinding.spPaymentStatus.setSelection(pIndex)

        val paymentMethods = arrayOf("None", "Cash", "CashApp", "Credit Card", "Check", "ACH")
        dialogBinding.spPaymentMethod.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, paymentMethods)
        val mIndex = paymentMethods.indexOfFirst { it.equals(order.paymentMethod, ignoreCase = true) }
        if (mIndex >= 0) dialogBinding.spPaymentMethod.setSelection(mIndex)

        val itemQtyInputs = mutableMapOf<OrderItem, EditText>()
        val currentItemsList = if (!order.items.isNullOrEmpty()) order.items.toMutableList() else mutableListOf(
            OrderItem(productId = "prod-1", name = "Custom Temporary Fence Package", unitPrice = order.subtotal, quantity = 1, deliveredQuantity = 1, total = order.subtotal)
        )

        fun calculateInvoiceGrandTotal(): Double {
            var subtotal = 0.0
            for ((item, inputField) in itemQtyInputs) {
                val qty = inputField.text.toString().toIntOrNull() ?: item.quantity
                subtotal += (qty * item.unitPrice)
            }
            val deliveryFee = if (subtotal > 0) 50.0 else 0.0
            val isTaxable = dialogBinding.cbInvoiceTaxable.isChecked
            val tax = if (isTaxable) Math.round(subtotal * 0.08 * 100.0) / 100.0 else 0.0
            val discount = dialogBinding.etInvoiceDiscount.text.toString().toDoubleOrNull() ?: 0.0

            val grandTotal = Math.max(0.0, subtotal + deliveryFee + tax - discount)
            dialogBinding.tvInvoiceGrandTotal.text = "💰 Grand Total: $" + String.format(Locale.US, "%.2f", grandTotal) + " (Subtotal: $" + String.format(Locale.US, "%.2f", subtotal) + " + Tax: $" + String.format(Locale.US, "%.2f", tax) + " - Disc: $" + String.format(Locale.US, "%.2f", discount) + ")"

            return grandTotal
        }

        dialogBinding.llInvoiceItemsContainer.removeAllViews()
        itemQtyInputs.clear()

        for (item in currentItemsList) {
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val tvLabel = TextView(context).apply {
                val delStr = if (item.deliveredQuantity != null) " | Delivered: ${item.deliveredQuantity}" else ""
                text = "• ${item.name}\n  [Ordered: ${item.quantity}$delStr @ $${item.unitPrice}]"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            }

            val inputQty = EditText(context).apply {
                hint = "Quantity"
                setText((item.deliveredQuantity ?: item.quantity).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                doAfterTextChanged { calculateInvoiceGrandTotal() }
            }

            itemLayout.addView(tvLabel)
            itemLayout.addView(inputQty)
            dialogBinding.llInvoiceItemsContainer.addView(itemLayout)
            itemQtyInputs[item] = inputQty
        }

        dialogBinding.cbInvoiceTaxable.setOnCheckedChangeListener { _, _ -> calculateInvoiceGrandTotal() }
        dialogBinding.etInvoiceDiscount.doAfterTextChanged { calculateInvoiceGrandTotal() }

        calculateInvoiceGrandTotal()

        // Locking rule: If order is completed/invoiced, lock line items and fields to READ-ONLY
        if (isInvoiced) {
            dialogBinding.etInvoiceCustomerName.isEnabled = false
            dialogBinding.etInvoiceCompany.isEnabled = false
            dialogBinding.etInvoicePhone.isEnabled = false
            dialogBinding.etInvoiceEmail.isEnabled = false
            dialogBinding.etInvoiceDeliveryAddress.isEnabled = false
            dialogBinding.cbInvoiceTaxable.isEnabled = false
            dialogBinding.etInvoiceDiscount.isEnabled = false
            for (inputField in itemQtyInputs.values) {
                inputField.isEnabled = false
            }

            dialogBinding.btnSaveInvoiceChanges.text = "🔄 Change Status Back to Processing (Re-Open Order)"
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.btnPrintPdfInvoice.setOnClickListener {
            generatePdfInvoice(order)
        }

        dialogBinding.btnSaveInvoiceChanges.setOnClickListener {
            if (isInvoiced) {
                // Change status back to Processing
                lifecycleScope.launch {
                    try {
                        ApiClient.instance.updateOrderStatus("Bearer $token", order.id, StatusUpdateRequest("Processing"))
                        ApiClient.instance.updateOrder("Bearer $token", order.id, order.copy(status = "Processing"))

                        Toast.makeText(context, "🔄 Order #${order.id} status changed back to Processing (Re-Opened)!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        loadInvoices()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Re-opened: ${e.message}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadInvoices()
                    }
                }
            } else {
                val updatedCustomer = dialogBinding.etInvoiceCustomerName.text.toString().trim()
                val updatedCompany = dialogBinding.etInvoiceCompany.text.toString().trim()
                val updatedPhone = dialogBinding.etInvoicePhone.text.toString().trim()
                val updatedEmail = dialogBinding.etInvoiceEmail.text.toString().trim()
                val updatedAddress = dialogBinding.etInvoiceDeliveryAddress.text.toString().trim()
                val selectedPayStatus = dialogBinding.spPaymentStatus.selectedItem.toString()
                val selectedPayMethod = dialogBinding.spPaymentMethod.selectedItem.toString()

                val updatedItems = itemQtyInputs.map { (item, inputField) ->
                    val qty = inputField.text.toString().toIntOrNull() ?: item.quantity
                    item.copy(deliveredQuantity = qty, quantity = qty, total = qty * item.unitPrice)
                }

                val finalAmount = calculateInvoiceGrandTotal()
                val isTaxable = dialogBinding.cbInvoiceTaxable.isChecked
                val subtotal = updatedItems.sumOf { it.total }
                val taxAmount = if (isTaxable) Math.round(subtotal * 0.08 * 100.0) / 100.0 else 0.0

                val updatedOrder = order.copy(
                    customerName = updatedCustomer,
                    customerCompany = updatedCompany,
                    customerPhone = updatedPhone,
                    customerEmail = updatedEmail,
                    deliveryAddress = updatedAddress,
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = taxAmount,
                    paymentStatus = selectedPayStatus,
                    paymentMethod = selectedPayMethod,
                    totalAmount = finalAmount
                )

                lifecycleScope.launch {
                    try {
                        ApiClient.instance.updateOrder("Bearer $token", order.id, updatedOrder)
                        ApiClient.instance.updateOrderPayment("Bearer $token", order.id, OrderPaymentUpdateRequest(selectedPayStatus, selectedPayMethod))

                        Toast.makeText(context, "💾 Invoice/Order #${order.id} updated!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadInvoices()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Saved changes: ${e.message}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadInvoices()
                    }
                }
            }
        }

        dialogBinding.btnMarkInvoicePaid.setOnClickListener {
            val options = arrayOf("Cash", "CashApp", "Credit Card", "Check", "ACH")
            AlertDialog.Builder(context)
                .setTitle("Select Payment Method")
                .setItems(options) { _, which ->
                    val selectedMethod = options[which]
                    updateOrderPayment(order.id, "Paid", selectedMethod)
                    dialog.dismiss()
                }
                .show()
        }

        dialogBinding.btnDeleteInvoiceRecord.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Delete Invoice Record")
                .setMessage("Are you sure you want to PERMANENTLY delete invoice/order #${order.id}? This cannot be undone.")
                .setPositiveButton("Delete Record") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            ApiClient.instance.deleteOrder("Bearer $token", order.id)
                            ApiClient.instance.deleteInvoice("Bearer $token", order.id)
                            Toast.makeText(context, "🗑️ Invoice/Order #${order.id} deleted", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadInvoices()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Deleted: ${e.message}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadInvoices()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
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
