package com.example.secureafenceadministrator.ui.customers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
import com.example.secureafenceadministrator.databinding.DialogCustomerDetailsBinding
import com.example.secureafenceadministrator.databinding.FragmentCustomersBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomersFragment : Fragment() {

    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewCustomers.layoutManager = LinearLayoutManager(requireContext())

        binding.fabAddCustomer.setOnClickListener {
            showCustomerDialog(null)
        }

        loadCustomers()
    }

    private fun loadCustomers() {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val customersResponse = ApiClient.instance.getCustomers("Bearer $token")
                val rentalsResponse = ApiClient.instance.getRentals("Bearer $token")
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")

                val customers = if (customersResponse.isSuccessful && customersResponse.body() != null) customersResponse.body()!! else emptyList()
                val rentals = if (rentalsResponse.isSuccessful && rentalsResponse.body() != null) rentalsResponse.body()!! else emptyList()
                val orders = if (salesResponse.isSuccessful && salesResponse.body() != null) salesResponse.body()!! else emptyList()

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                val adapter = GenericAdapter(
                    customers,
                    titleProvider = { it.name.ifEmpty { "Unnamed Customer" } },
                    subtitleProvider = { customer ->
                        val custRentals = rentals.filter {
                            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
                        }
                        val custOrders = orders.filter {
                            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
                        }

                        val unpaidOrdersTotal = custOrders.filter {
                            !it.paymentStatus.equals("Paid", ignoreCase = true)
                        }.sumOf { it.totalAmount }

                        val activeRentalsTotal = custRentals.filter {
                            !it.status.equals("Returned", ignoreCase = true) && !it.status.equals("Completed", ignoreCase = true)
                        }.sumOf { it.monthlyRateTotal }

                        val totalBalance = unpaidOrdersTotal + activeRentalsTotal

                        val activeRentals = custRentals.filter {
                            !it.status.equals("Returned", ignoreCase = true) && !it.status.equals("Completed", ignoreCase = true)
                        }

                        val rentalReturnStatus = if (activeRentals.isEmpty()) {
                            "No Active Rentals"
                        } else {
                            val overdueRental = activeRentals.find { it.endDate.isNotEmpty() && it.endDate < todayStr }
                            if (overdueRental != null) {
                                "⚠️ OVERDUE (Return Date: ${overdueRental.endDate})"
                            } else {
                                val nearestReturn = activeRentals.minByOrNull { it.endDate }?.endDate ?: "N/A"
                                "✅ CURRENT (Next Return: $nearestReturn)"
                            }
                        }

                        val balanceText = if (totalBalance > 0) "$${String.format(Locale.US, "%.2f", totalBalance)} DUE" else "$0.00 (Paid in Full)"
                        val companyStr = if (!customer.company.isNullOrEmpty()) "Company: ${customer.company}\n" else ""

                        "${companyStr}Email: ${customer.email} | Phone: ${customer.phone ?: "N/A"}\n💰 Account Balance: $balanceText\n📅 Rental Return: $rentalReturnStatus"
                    },
                    statusProvider = { customer ->
                        val custRentals = rentals.filter {
                            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
                        }
                        val custOrders = orders.filter {
                            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
                        }

                        val unpaidOrdersTotal = custOrders.filter {
                            !it.paymentStatus.equals("Paid", ignoreCase = true)
                        }.sumOf { it.totalAmount }

                        val activeRentalsTotal = custRentals.filter {
                            !it.status.equals("Returned", ignoreCase = true) && !it.status.equals("Completed", ignoreCase = true)
                        }.sumOf { it.monthlyRateTotal }

                        val totalBalance = unpaidOrdersTotal + activeRentalsTotal

                        val activeRentals = custRentals.filter {
                            !it.status.equals("Returned", ignoreCase = true) && !it.status.equals("Completed", ignoreCase = true)
                        }
                        val isOverdue = activeRentals.any { !it.endDate.isNullOrEmpty() && it.endDate < todayStr }

                        when {
                            isOverdue -> "⚠️ OVERDUE"
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
            it.customerEmail.equals(customer.email, true) || (customer.id.isNotEmpty() && it.customerId == customer.id)
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
            .setTitle("Jobsite: ${jobsite.name}")
            .setMessage(details)
            .setPositiveButton("Edit Jobsite") { _, _ -> showJobsiteDialog(customer, jobsite) }
            .setNegativeButton("Delete Jobsite") { _, _ -> confirmDeleteJobsite(customer, jobsite) }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun confirmDeleteJobsite(customer: Customer, jobsite: Jobsite) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete Jobsite")
            .setMessage("Are you sure you want to remove jobsite ${jobsite.name}?")
            .setPositiveButton("Yes") { _, _ ->
                val jobId = jobsite.id
                val custId = customer.id
                if (jobId != null && custId.isNotEmpty()) {
                    deleteJobsite(custId, jobId)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteJobsite(customerId: String, jobsiteId: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.deleteJobsite("Bearer $token", customerId, jobsiteId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Jobsite deleted", Toast.LENGTH_SHORT).show()
                    loadCustomers()
                } else {
                    Toast.makeText(context, "Failed to delete jobsite", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showJobsiteDialog(customer: Customer, jobsite: Jobsite?) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (jobsite == null) "Add Jobsite for ${customer.name}" else "Edit Jobsite ${jobsite.name}")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_jobsite, null)
        val etName = view.findViewById<EditText>(R.id.et_jobsite_name)
        val etAddress = view.findViewById<EditText>(R.id.et_jobsite_address)
        val etContactName = view.findViewById<EditText>(R.id.et_jobsite_contact_name)
        val etContactPhone = view.findViewById<EditText>(R.id.et_jobsite_contact_phone)
        val etInstructions = view.findViewById<EditText>(R.id.et_jobsite_instructions)
        val etDistance = view.findViewById<EditText>(R.id.et_jobsite_distance)

        jobsite?.let {
            etName.setText(it.name)
            etAddress.setText(it.address)
            etContactName.setText(it.contactName)
            etContactPhone.setText(it.contactPhone)
            etInstructions.setText(it.specialInstructions)
            etDistance.setText(it.deliveryDistanceMiles.toString())
        }

        builder.setView(view)
        builder.setPositiveButton("Save Jobsite") { _, _ ->
            val siteName = etName.text.toString()
            val siteAddress = etAddress.text.toString()

            if (siteName.isEmpty() || siteAddress.isEmpty()) {
                Toast.makeText(context, "Jobsite Name and Address are required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val jobsiteReq = Jobsite(
                id = jobsite?.id,
                name = siteName,
                address = siteAddress,
                contactName = etContactName.text.toString(),
                contactPhone = etContactPhone.text.toString(),
                specialInstructions = etInstructions.text.toString(),
                deliveryDistanceMiles = etDistance.text.toString().toDoubleOrNull() ?: 0.0
            )

            saveJobsite(customer.id, jobsite?.id, jobsiteReq)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun saveJobsite(customerId: String, jobsiteId: String?, jobsite: Jobsite) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = if (jobsiteId == null) {
                    ApiClient.instance.createJobsite("Bearer $token", customerId, jobsite)
                } else {
                    ApiClient.instance.updateJobsite("Bearer $token", customerId, jobsiteId, jobsite)
                }

                if (response.isSuccessful) {
                    Toast.makeText(context, "Jobsite saved successfully", Toast.LENGTH_SHORT).show()
                    loadCustomers()
                } else {
                    Toast.makeText(context, "Failed to save jobsite", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(customer: Customer) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete Customer")
            .setMessage("Are you sure you want to delete ${customer.name}?")
            .setPositiveButton("Yes") { _, _ -> deleteCustomer(customer) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteCustomer(customer: Customer) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

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
                val response = if (customerId == null) {
                    ApiClient.instance.createCustomer("Bearer $token", request)
                } else {
                    ApiClient.instance.updateCustomer("Bearer $token", customerId, request)
                }

                if (response.isSuccessful) {
                    Toast.makeText(context, "Customer account saved successfully", Toast.LENGTH_SHORT).show()
                    loadCustomers()
                } else {
                    Toast.makeText(context, "Failed to save customer account", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
