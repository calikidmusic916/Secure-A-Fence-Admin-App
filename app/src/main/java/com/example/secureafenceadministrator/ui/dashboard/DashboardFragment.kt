package com.example.secureafenceadministrator.ui.dashboard

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadOverview()

        binding.btnRefresh.setOnClickListener {
            loadOverview()
        }

        binding.btnCreateCustomer.setOnClickListener {
            showCreateCustomerDialog()
        }

        binding.btnManageCustomers.setOnClickListener {
            showCustomersDialog()
        }
    }

    private fun showCreateCustomerDialog() {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Create Customer Login Account")

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputName = android.widget.EditText(context).apply { hint = "Full Name" }
        val inputEmail = android.widget.EditText(context).apply { hint = "Email Address" }
        val inputCompany = android.widget.EditText(context).apply { hint = "Company Name" }
        val inputPhone = android.widget.EditText(context).apply { hint = "Phone Number" }

        layout.addView(inputName)
        layout.addView(inputEmail)
        layout.addView(inputCompany)
        layout.addView(inputPhone)
        builder.setView(layout)

        builder.setPositiveButton("Create") { _, _ ->
            val name = inputName.text.toString()
            val email = inputEmail.text.toString()
            val company = inputCompany.text.toString()
            val phone = inputPhone.text.toString()

            if (name.isNotEmpty() && email.isNotEmpty()) {
                createCustomer(name, email, company, phone)
            } else {
                Toast.makeText(context, "Name and Email are required", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun createCustomer(name: String, email: String, company: String, phone: String) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.createCustomer(
                    "Bearer $token",
                    com.example.secureafenceadministrator.data.model.CreateCustomerRequest(
                        name = name,
                        email = email,
                        company = company,
                        phone = phone
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Customer account created successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to create customer account", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomersDialog() {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getCustomers("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val customers = response.body()!!
                    if (customers.isEmpty()) {
                        Toast.makeText(context, "No customers found", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val items = customers.map { "${it.name} - ${it.email} (${it.company ?: "Indiv"})" }.toTypedArray()
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Manage Customers (${customers.size})")
                        .setItems(items) { _, which ->
                            showCustomerDetailsDialog(customers[which])
                        }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(context, "Failed to load customers", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomerDetailsDialog(customer: com.example.secureafenceadministrator.data.model.Customer) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getSalesOrders("Bearer $token")
                val customerOrders = if (response.isSuccessful && response.body() != null) {
                    response.body()!!.filter { it.customerEmail.equals(customer.email, true) || it.customerId == customer.id }
                } else {
                    emptyList()
                }

                val details = StringBuilder()
                details.append("Name: ${customer.name}\n")
                details.append("Email: ${customer.email}\n")
                details.append("Company: ${customer.company ?: "N/A"}\n")
                details.append("Phone: ${customer.phone ?: "N/A"}\n")
                details.append("Role: ${customer.role}\n\n")
                details.append("Total Orders: ${customerOrders.size}\n")
                for (ord in customerOrders) {
                    details.append("• Order #${ord.id} (${ord.orderType}) - $${ord.totalAmount} [${ord.status}]\n")
                }

                android.app.AlertDialog.Builder(context)
                    .setTitle("Customer Account: ${customer.name}")
                    .setMessage(details.toString())
                    .setPositiveButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading orders: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadOverview() {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getAdminOverview("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val metrics = response.body()!!.metrics
                    binding.tvSalesRevenue.text = "$${metrics.totalSalesRevenue}"
                    binding.tvMonthlyRental.text = "$${metrics.monthlyRentalRevenue}"
                    binding.tvActiveRentals.text = "${metrics.activeRentalsCount}"
                    binding.tvStock.text = "${metrics.panelsInWarehouse}"
                } else {
                    android.util.Log.e("API_DEBUG", "Error: ${response.code()} Body: ${response.errorBody()?.string()}")
                    Toast.makeText(context, "Failed to load overview: ${response.code()}", Toast.LENGTH_SHORT).show()
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
