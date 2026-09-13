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
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentCustomersBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch

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
                val response = ApiClient.instance.getCustomers("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val customers = response.body()!!
                    val adapter = GenericAdapter(
                        customers,
                        titleProvider = { it.name },
                        subtitleProvider = { 
                            val taxStatus = if (it.isTaxable) "Taxable (8%)" else "Tax Exempt"
                            "Company: ${it.company ?: "N/A"}\nBusiness Address: ${it.businessAddress ?: "N/A"}\nEmail: ${it.email}\nPhone: ${it.phone ?: "N/A"}\nTax: $taxStatus" 
                        },
                        statusProvider = { 
                            val jobsCount = it.jobsites?.size ?: 0
                            "Role: ${it.role.uppercase()} | $jobsCount Jobsites" 
                        },
                        onItemClick = { showCustomerDetails(it) }
                    )
                    binding.recyclerViewCustomers.adapter = adapter
                } else {
                    Toast.makeText(context, "Failed to load customers", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomerDetails(customer: Customer) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(customer.name)

        val taxStatus = if (customer.isTaxable) "Taxable (8% Sales Tax)" else "Tax Exempt (0% Tax)"
        val jobsitesList = customer.jobsites

        var jobsitesSummary = "\n--- JOBSITES (${jobsitesList?.size ?: 0}) ---"
        if (!jobsitesList.isNullOrEmpty()) {
            jobsitesList.forEach { j ->
                val activeCount = j.activeRentals?.size ?: 0
                jobsitesSummary += "\n• ${j.name} (${j.address})\n  Contact: ${j.contactName ?: "N/A"} (${j.contactPhone ?: "N/A"})\n  Distance: ${j.deliveryDistanceMiles} mi | Active Rentals: $activeCount\n  Instructions: ${j.specialInstructions ?: "None"}\n"
            }
        } else {
            jobsitesSummary += "\nNo jobsites created yet."
        }

        val details = """
            Email: ${customer.email}
            Company: ${customer.company ?: "N/A"}
            Phone: ${customer.phone ?: "N/A"}
            Business Address: ${customer.businessAddress ?: "N/A"}
            Tax Status: $taxStatus
            Role: ${customer.role.uppercase()}
            $jobsitesSummary
        """.trimIndent()

        builder.setMessage(details)

        builder.setPositiveButton("Edit Customer") { _, _ -> showCustomerDialog(customer) }
        builder.setNegativeButton("Manage Jobsites") { _, _ -> showJobsitesListDialog(customer) }
        builder.setNeutralButton("Delete") { _, _ -> confirmDelete(customer) }
        builder.show()
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
        val activeRentalsSummary = if (!jobsite.activeRentals.isNullOrEmpty()) {
            jobsite.activeRentals.joinToString("\n") { "• Rental #${it.id}: ${it.items.joinToString { item -> "${item.quantity}x ${item.name}" }} (${it.startDate} to ${it.endDate})" }
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
                if (jobsite.id != null) {
                    deleteJobsite(customer.id, jobsite.id)
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
        builder.setTitle(if (customer == null) "Add Customer" else "Edit Customer")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_customer, null)
        val etName = view.findViewById<EditText>(R.id.et_cust_name)
        val etEmail = view.findViewById<EditText>(R.id.et_cust_email)
        val etCompany = view.findViewById<EditText>(R.id.et_cust_company)
        val etPhone = view.findViewById<EditText>(R.id.et_cust_phone)
        val etBusinessAddress = view.findViewById<EditText>(R.id.et_cust_business_address)
        val cbIsTaxable = view.findViewById<CheckBox>(R.id.cb_is_taxable)
        val spRole = view.findViewById<Spinner>(R.id.sp_cust_role)

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
        }

        builder.setView(view)
        builder.setPositiveButton("Save") { _, _ ->
            val name = etName.text.toString()
            val email = etEmail.text.toString()
            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(context, "Name and Email are required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val request = CreateCustomerRequest(
                name = name,
                email = email,
                company = etCompany.text.toString(),
                phone = etPhone.text.toString(),
                role = spRole.selectedItem.toString(),
                isTaxable = cbIsTaxable.isChecked,
                businessAddress = etBusinessAddress.text.toString(),
                jobsites = customer?.jobsites
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
                    Toast.makeText(context, "Customer saved", Toast.LENGTH_SHORT).show()
                    loadCustomers()
                } else {
                    Toast.makeText(context, "Failed to save customer", Toast.LENGTH_SHORT).show()
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
