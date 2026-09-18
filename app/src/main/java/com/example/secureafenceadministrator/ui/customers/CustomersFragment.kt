package com.example.secureafenceadministrator.ui.customers

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.CreateCustomerRequest
import com.example.secureafenceadministrator.data.model.Customer
import com.example.secureafenceadministrator.data.model.Jobsite
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.Rental
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogCustomerBinding
import com.example.secureafenceadministrator.databinding.DialogCustomerDetailsBinding
import com.example.secureafenceadministrator.databinding.FragmentCustomersBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomersFragment : Fragment() {

    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!

    private var customersList = mutableListOf<Customer>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewCustomers.layoutManager = LinearLayoutManager(requireContext())

        loadCustomers()

        binding.fabAddCustomer.setOnClickListener {
            showCustomerDialog(null)
        }
    }

    private fun loadCustomers() {
        val context = context ?: return
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getCustomers("Bearer $token")
                val rentalsResponse = ApiClient.instance.getRentals("Bearer $token")
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")

                val rentals = if (rentalsResponse.isSuccessful && rentalsResponse.body() != null) rentalsResponse.body()!! else emptyList()
                val orders = if (salesResponse.isSuccessful && salesResponse.body() != null) salesResponse.body()!! else emptyList()

                customersList = if (response.isSuccessful && response.body() != null) response.body()!!.toMutableList() else mutableListOf()

                binding.tvTitle.text = "👥 Registered Customer Accounts (${customersList.size})"

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                val adapter = GenericAdapter(
                    customersList,
                    titleProvider = { "${it.name}${if (!it.company.isNullOrEmpty()) " (${it.company})" else ""}" },
                    subtitleProvider = {
                        val sitesCount = it.jobsites?.size ?: 0
                        val taxBadge = if (it.isTaxable) "Taxable (8%)" else "TAX EXEMPT"
                        val phoneStr = (it.phone ?: "").ifEmpty { "N/A" }
                        "Email: ${it.email}\nPhone: $phoneStr | Sites: $sitesCount | $taxBadge"
                    },
                    statusProvider = { cust ->
                        val custRentals = rentals.filter {
                            it.customerEmail.equals(cust.email, true) || (cust.id.isNotEmpty() && it.customerId == cust.id)
                        }
                        val custOrders = orders.filter {
                            it.customerEmail.equals(cust.email, true) || (cust.id.isNotEmpty() && it.customerId == cust.id)
                        }

                        val isOverdue = custRentals.any { !it.endDate.isNullOrEmpty() && it.endDate < todayStr && !it.status.equals("Returned", true) }

                        val unpaidOrdersTotal = custOrders.filter { !it.paymentStatus.equals("Paid", ignoreCase = true) }.sumOf { it.totalAmount }
                        val activeRentalsTotal = custRentals.filter { !it.status.equals("Returned", true) && !it.status.equals("Completed", true) }.sumOf { it.monthlyRateTotal }
                        val totalBalance = unpaidOrdersTotal + activeRentalsTotal

                        when {
                            isOverdue -> "⚠️ OVERDUE RENTAL"
                            totalBalance > 0 -> "💳 $" + String.format(Locale.US, "%.2f", totalBalance) + " DUE"
                            else -> "✅ CURRENT"
                        }
                    },
                    rightImageResIdProvider = { R.drawable.logo },
                    onItemClick = { showCustomerDetailsWithFinancials(it, rentals, orders, todayStr) }
                )
                binding.recyclerViewCustomers.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomerDetailsWithFinancials(
        customer: Customer,
        allRentals: List<Rental>,
        allOrders: List<Order>,
        todayStr: String
    ) {
        val context = context ?: return
        val dialogBinding = DialogCustomerDetailsBinding.inflate(LayoutInflater.from(context))

        dialogBinding.tvCustomerDialogTitle.text = "👤 ${customer.name.ifEmpty { "Customer Account" }}"
        dialogBinding.etCustomerName.setText(customer.name)
        dialogBinding.etCustomerCompany.setText(customer.company)
        dialogBinding.etCustomerPhone.setText(customer.phone)
        dialogBinding.etCustomerEmail.setText(customer.email)
        dialogBinding.etBusinessAddress.setText(customer.businessAddress)
        dialogBinding.cbCustomerTaxable.isChecked = customer.isTaxable

        val roles = arrayOf("customer", "admin")
        dialogBinding.spCustomerRole.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, roles)
        val roleIdx = roles.indexOf(customer.role)
        if (roleIdx >= 0) dialogBinding.spCustomerRole.setSelection(roleIdx)

        val custRentals = allRentals.filter {
            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
        }
        val custOrders = allOrders.filter {
            it.customerEmail.equals(customer.email, true) ||
            (customer.id.isNotEmpty() && it.customerId == customer.id) ||
            it.customerName.equals(customer.name, true)
        }

        val unpaidOrdersTotal = custOrders.filter {
            !it.paymentStatus.equals("Paid", ignoreCase = true)
        }.sumOf { it.totalAmount }

        val activeRentalsTotal = custRentals.filter {
            !it.status.equals("Returned", ignoreCase = true) && !it.status.equals("Completed", ignoreCase = true)
        }.sumOf { it.monthlyRateTotal }

        val totalBalance = unpaidOrdersTotal + activeRentalsTotal

        val balanceText = if (totalBalance > 0) {
            "Account Balance: $" + String.format(Locale.US, "%.2f", totalBalance) + " (PAST DUE / UNPAID)"
        } else {
            "Account Balance: $0.00 (Current / Paid in Full)"
        }
        dialogBinding.tvAccountBalance.text = balanceText

        var rentalsSummary = ""
        if (custRentals.isNotEmpty()) {
            custRentals.forEach { r ->
                val isOverdue = !r.endDate.isNullOrEmpty() && r.endDate < todayStr
                val statusBadge = if (isOverdue) "⚠️ OVERDUE" else "✅ CURRENT"
                rentalsSummary += "• Rental #${r.id} [$statusBadge]\n  Site: ${r.jobsiteAddress}\n  Term: ${r.startDate} ➔ ${r.endDate}\n  Rate: $${r.monthlyRateTotal}/mo | Status: ${r.status}\n\n"
            }
        } else {
            rentalsSummary = "No active rentals deployed for this customer."
        }
        dialogBinding.tvCustomerRentalsSummary.text = rentalsSummary.trim()

        // Populate Invoices & Order History for this Customer
        dialogBinding.llCustomerInvoicesContainer.removeAllViews()

        if (custOrders.isNotEmpty()) {
            for (order in custOrders) {
                val cardLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.parseColor("#F8FAFC"))
                    setPadding(16, 16, 16, 16)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 16) }
                    layoutParams = params
                }

                val isInvoiced = order.status.equals("Delivered", true) || order.status.equals("Picked Up / Returned", true) || order.status.equals("Completed", true)
                val statusBadge = if (isInvoiced) "📄 OFFICIALLY INVOICED [${order.status.uppercase(Locale.US)}]" else "📦 SALES ORDER [${order.status.uppercase(Locale.US)}]"
                val payBadge = if (order.paymentStatus.equals("Paid", true)) "💳 PAID (${order.paymentMethod ?: "Method N/A"})" else "⚠️ UNPAID"

                val tvHeader = TextView(context).apply {
                    text = "📄 ${order.orderType.uppercase(Locale.US)} #${order.id} | $statusBadge"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                }

                val itemsSummary = if (order.items.isNullOrEmpty()) "• 1x Custom Temporary Fence Package" else order.items.joinToString("\n") { "• ${it.quantity}x ${it.name} @ $${it.unitPrice}" }

                val tvDetails = TextView(context).apply {
                    text = "Date: ${order.deliveryDate.ifEmpty { "N/A" }}\nAddress: ${order.deliveryAddress.ifEmpty { "Sacramento Warehouse" }}\n$itemsSummary\n💰 Total: $${String.format(Locale.US, "%.2f", order.totalAmount)} | Payment: $payBadge"
                    textSize = 12f
                    setTextColor(Color.parseColor("#334155"))
                    setPadding(0, 4, 0, 8)
                }

                val btnPdf = Button(context).apply {
                    text = "🖨️ Export PDF Invoice"
                    textSize = 11f
                    setOnClickListener {
                        generatePdfInvoice(order)
                    }
                }

                cardLayout.addView(tvHeader)
                cardLayout.addView(tvDetails)
                cardLayout.addView(btnPdf)

                dialogBinding.llCustomerInvoicesContainer.addView(cardLayout)
            }
        } else {
            val tvEmpty = TextView(context).apply {
                text = "No invoices or orders on file for this customer."
                textSize = 13f
                setPadding(8, 8, 8, 8)
            }
            dialogBinding.llCustomerInvoicesContainer.addView(tvEmpty)
        }

        val jobsitesList = customer.jobsites
        val jobsitesSummary = if (!jobsitesList.isNullOrEmpty()) {
            jobsitesList.joinToString("\n") { "• ${it.name} (${it.address}) - Distance: ${it.deliveryDistanceMiles} mi" }
        } else {
            "0 jobsites registered."
        }
        dialogBinding.tvCustomerJobsitesSummary.text = jobsitesSummary

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        var isEditingEnabled = false

        fun toggleCustomerEditing(enable: Boolean) {
            isEditingEnabled = enable
            dialogBinding.etCustomerName.isEnabled = enable
            dialogBinding.etCustomerCompany.isEnabled = enable
            dialogBinding.etCustomerPhone.isEnabled = enable
            dialogBinding.etCustomerEmail.isEnabled = enable
            dialogBinding.etCustomerPassword.isEnabled = enable
            dialogBinding.etBusinessAddress.isEnabled = enable
            dialogBinding.spCustomerRole.isEnabled = enable
            dialogBinding.cbCustomerTaxable.isEnabled = enable
            dialogBinding.btnSaveCustomerChanges.isEnabled = enable

            if (enable) {
                dialogBinding.btnEnableCustomerEditing.text = "🔒 Lock"
                Toast.makeText(context, "✏️ Edit mode enabled. You can now modify customer account profile.", Toast.LENGTH_SHORT).show()
            } else {
                dialogBinding.btnEnableCustomerEditing.text = "✏️ Edit"
            }
        }

        dialogBinding.btnEnableCustomerEditing.setOnClickListener {
            toggleCustomerEditing(!isEditingEnabled)
        }

        dialogBinding.btnSaveCustomerChanges.setOnClickListener {
            val name = dialogBinding.etCustomerName.text.toString().trim()
            val email = dialogBinding.etCustomerEmail.text.toString().trim()
            val password = dialogBinding.etCustomerPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(context, "Name and Email are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = CreateCustomerRequest(
                name = name,
                email = email,
                company = dialogBinding.etCustomerCompany.text.toString().trim(),
                phone = dialogBinding.etCustomerPhone.text.toString().trim(),
                role = dialogBinding.spCustomerRole.selectedItem.toString(),
                isTaxable = dialogBinding.cbCustomerTaxable.isChecked,
                businessAddress = dialogBinding.etBusinessAddress.text.toString().trim(),
                password = password.ifEmpty { null },
                jobsites = customer.jobsites
            )

            saveCustomer(customer.id, request)
            dialog.dismiss()
        }

        dialogBinding.btnManageJobsites.setOnClickListener {
            showJobsitesListDialog(customer)
            dialog.dismiss()
        }

        dialogBinding.btnDeleteCustomer.setOnClickListener {
            confirmDelete(customer)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generatePdfInvoice(order: Order) {
        val context = context ?: return
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

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "Invoice_${order.id}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(context, "Invoice exported: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun showJobsitesListDialog(customer: Customer) {
        val context = context ?: return
        val jobsites = customer.jobsites ?: emptyList()

        val options = mutableListOf<String>()
        options.add("➕ Add New Jobsite")
        jobsites.forEach { j ->
            val activeRentalsCount = j.activeRentals?.size ?: 0
            options.add("${j.name} (${j.address}) - $activeRentalsCount Rentals")
        }

        AlertDialog.Builder(context)
            .setTitle("${customer.name}'s Jobsites")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showJobsiteDialog(customer, null)
                } else {
                    val selectedJobsite = jobsites[which - 1]
                    showJobsiteActionDialog(customer, selectedJobsite)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showJobsiteActionDialog(customer: Customer, jobsite: Jobsite) {
        val context = context ?: return
        val activeRentals = jobsite.activeRentals
        val activeRentalsSummary = if (!activeRentals.isNullOrEmpty()) {
            activeRentals.joinToString("\n") { "• Rental #${it.id}: ${it.items.joinToString { item -> "${item.quantity}x ${item.name}" }} (${it.startDate} to ${it.endDate})" }
        } else {
            "No active rentals deployed at this jobsite."
        }

        val details = """
            Address: ${jobsite.address}
            Site Contact: ${jobsite.contactName ?: "N/A"} (${jobsite.contactPhone ?: "N/A"})
            Delivery Distance: ${jobsite.deliveryDistanceMiles} Miles
            Instructions: ${jobsite.specialInstructions ?: "None"}

            --- ACTIVE RENTALS AT THIS JOBSITE ---
            $activeRentalsSummary
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("📍 ${jobsite.name}")
            .setMessage(details)
            .setPositiveButton("Edit Jobsite") { _, _ ->
                showJobsiteDialog(customer, jobsite)
            }
            .setNegativeButton("Delete Jobsite") { _, _ ->
                deleteJobsite(customer, jobsite)
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun showJobsiteDialog(customer: Customer, existingJobsite: Jobsite?) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (existingJobsite == null) "Add Jobsite Location" else "Edit Jobsite Location")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etName = EditText(context).apply { hint = "Jobsite Name (e.g. Tower B / Main Yard)"; setText(existingJobsite?.name) }
        val etAddress = EditText(context).apply { hint = "Jobsite Physical Address"; setText(existingJobsite?.address) }
        val etContactName = EditText(context).apply { hint = "Site Contact Person"; setText(existingJobsite?.contactName) }
        val etContactPhone = EditText(context).apply { hint = "Site Contact Phone"; setText(existingJobsite?.contactPhone) }
        val etNotes = EditText(context).apply { hint = "Gate / Delivery Instructions"; setText(existingJobsite?.specialInstructions) }
        val etDistance = EditText(context).apply {
            hint = "Delivery Distance from Yard (Miles)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existingJobsite?.deliveryDistanceMiles?.toString())
        }

        layout.addView(etName)
        layout.addView(etAddress)
        layout.addView(etContactName)
        layout.addView(etContactPhone)
        layout.addView(etNotes)
        layout.addView(etDistance)

        builder.setView(layout)
        builder.setPositiveButton("Save Jobsite") { _, _ ->
            val name = etName.text.toString().trim()
            val address = etAddress.text.toString().trim()

            if (name.isEmpty() || address.isEmpty()) {
                Toast.makeText(context, "Name and Address are required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val jobsite = Jobsite(
                id = existingJobsite?.id ?: ("site-" + System.currentTimeMillis()),
                name = name,
                address = address,
                contactName = etContactName.text.toString().trim(),
                contactPhone = etContactPhone.text.toString().trim(),
                specialInstructions = etNotes.text.toString().trim(),
                deliveryDistanceMiles = etDistance.text.toString().toDoubleOrNull() ?: 0.0
            )

            val token = SessionManager.getToken(context) ?: return@setPositiveButton

            lifecycleScope.launch {
                try {
                    val response = if (existingJobsite == null) {
                        ApiClient.instance.createJobsite("Bearer $token", customer.id, jobsite)
                    } else {
                        ApiClient.instance.updateJobsite("Bearer $token", customer.id, existingJobsite.id.orEmpty(), jobsite)
                    }
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Jobsite saved successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save jobsite", Toast.LENGTH_SHORT).show()
                    }
                    loadCustomers()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadCustomers()
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun deleteJobsite(customer: Customer, jobsite: Jobsite) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.deleteJobsite("Bearer $token", customer.id, jobsite.id.orEmpty())
                if (response.isSuccessful) {
                    Toast.makeText(context, "Jobsite deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete jobsite", Toast.LENGTH_SHORT).show()
                }
                loadCustomers()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                loadCustomers()
            }
        }
    }

    private fun showCustomerDialog(customer: Customer?) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (customer == null) "Add Customer Account" else "Edit Customer Account")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_customer, null)
        val etName = view.findViewById<EditText>(R.id.et_cust_name)
        val etEmail = view.findViewById<EditText>(R.id.et_cust_email)
        val etPassword = view.findViewById<EditText>(R.id.et_cust_password)
        val etCompany = view.findViewById<EditText>(R.id.et_cust_company)
        val etPhone = view.findViewById<EditText>(R.id.et_cust_phone)
        val etBusinessAddress = view.findViewById<EditText>(R.id.et_cust_business_address)
        val cbIsTaxable = view.findViewById<CheckBox>(R.id.cb_is_taxable)
        val spRole = view.findViewById<Spinner>(R.id.sp_cust_role)

        val etJobName = view.findViewById<EditText>(R.id.et_init_jobsite_name)
        val etJobAddr = view.findViewById<EditText>(R.id.et_init_jobsite_address)
        val etJobContact = view.findViewById<EditText>(R.id.et_init_jobsite_contact_name)
        val etJobPhone = view.findViewById<EditText>(R.id.et_init_jobsite_contact_phone)
        val etJobNotes = view.findViewById<EditText>(R.id.et_init_jobsite_instructions)
        val etJobDist = view.findViewById<EditText>(R.id.et_init_jobsite_distance)

        val roles = arrayOf("customer", "admin")
        spRole.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, roles)

        customer?.let {
            etName.setText(it.name)
            etEmail.setText(it.email)
            etCompany.setText(it.company)
            etPhone.setText(it.phone)
            etBusinessAddress.setText(it.businessAddress)
            cbIsTaxable.isChecked = it.isTaxable
            val roleIdx = roles.indexOf(it.role)
            if (roleIdx >= 0) spRole.setSelection(roleIdx)

            if (!it.jobsites.isNullOrEmpty()) {
                val firstJob = it.jobsites[0]
                etJobName.setText(firstJob.name)
                etJobAddr.setText(firstJob.address)
                etJobContact.setText(firstJob.contactName)
                etJobPhone.setText(firstJob.contactPhone)
                etJobNotes.setText(firstJob.specialInstructions)
                etJobDist.setText(firstJob.deliveryDistanceMiles.toString())
            }
        }

        builder.setView(view)
        builder.setPositiveButton("Save Customer Account") { _, _ ->
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(context, "Name and Email are required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val jobsitesList = (customer?.jobsites ?: emptyList()).toMutableList()
            val siteName = etJobName.text.toString().trim()
            val siteAddr = etJobAddr.text.toString().trim()

            if (siteName.isNotEmpty() || siteAddr.isNotEmpty()) {
                val initJobsite = Jobsite(
                    id = jobsitesList.firstOrNull()?.id ?: ("site-" + System.currentTimeMillis()),
                    name = siteName.ifEmpty { "Primary Jobsite" },
                    address = siteAddr,
                    contactName = etJobContact.text.toString().trim(),
                    contactPhone = etJobPhone.text.toString().trim(),
                    specialInstructions = etJobNotes.text.toString().trim(),
                    deliveryDistanceMiles = etJobDist.text.toString().toDoubleOrNull() ?: 0.0
                )
                if (jobsitesList.isNotEmpty()) {
                    jobsitesList[0] = initJobsite
                } else {
                    jobsitesList.add(initJobsite)
                }
            }

            val request = CreateCustomerRequest(
                name = name,
                email = email,
                company = etCompany.text.toString().trim(),
                phone = etPhone.text.toString().trim(),
                role = spRole.selectedItem.toString(),
                isTaxable = cbIsTaxable.isChecked,
                businessAddress = etBusinessAddress.text.toString().trim(),
                password = password.ifEmpty { null },
                jobsites = jobsitesList
            )

            saveCustomer(customer?.id, request)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun saveCustomer(customerId: String?, request: CreateCustomerRequest) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = if (customerId.isNullOrEmpty()) {
                    ApiClient.instance.createCustomer("Bearer $token", request)
                } else {
                    ApiClient.instance.updateCustomer("Bearer $token", customerId, request)
                }

                if (response.isSuccessful) {
                    Toast.makeText(context, "Customer account saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save customer account", Toast.LENGTH_SHORT).show()
                }
                loadCustomers()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                loadCustomers()
            }
        }
    }

    private fun confirmDelete(customer: Customer) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        AlertDialog.Builder(context)
            .setTitle("Delete Customer")
            .setMessage("Are you sure you want to delete ${customer.name}? This will remove their profile.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.instance.deleteCustomer("Bearer $token", customer.id)
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Customer deleted", Toast.LENGTH_SHORT).show()
                            loadCustomers()
                        } else {
                            Toast.makeText(context, "Failed to delete customer", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
