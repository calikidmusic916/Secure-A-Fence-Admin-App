package com.example.secureafenceadministrator.ui.dashboard

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.secureafenceadministrator.data.model.CreateCustomerRequest
import com.example.secureafenceadministrator.data.model.Customer
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
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
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Create Customer Login Account")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputName = EditText(context).apply { hint = "Full Name" }
        val inputEmail = EditText(context).apply { hint = "Email Address" }
        val inputCompany = EditText(context).apply { hint = "Company Name" }
        val inputPhone = EditText(context).apply { hint = "Phone Number" }

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
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.createCustomer(
                    "Bearer $token",
                    CreateCustomerRequest(
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
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val customersResponse = ApiClient.instance.getCustomers("Bearer $token")
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                val rentalsResponse = ApiClient.instance.getRentals("Bearer $token")

                val customerMap = mutableMapOf<String, Customer>()

                if (customersResponse.isSuccessful && customersResponse.body() != null) {
                    customersResponse.body()!!.forEach { 
                        val emailKey = it.email.orEmpty().lowercase()
                        if (emailKey.isNotEmpty()) {
                            customerMap[emailKey] = it
                        }
                    }
                }

                if (salesResponse.isSuccessful && salesResponse.body() != null) {
                    salesResponse.body()!!.forEach { order ->
                        val email = order.customerEmail.orEmpty().lowercase()
                        if (email.isNotEmpty() && !customerMap.containsKey(email)) {
                            customerMap[email] = Customer(
                                id = order.customerId.orEmpty(),
                                name = order.customerName.orEmpty(),
                                email = order.customerEmail.orEmpty(),
                                role = "customer",
                                company = order.customerCompany,
                                phone = order.customerPhone
                            )
                        }
                    }
                }

                if (rentalsResponse.isSuccessful && rentalsResponse.body() != null) {
                    rentalsResponse.body()!!.forEach { rental ->
                        val email = rental.customerEmail.orEmpty().lowercase()
                        if (email.isNotEmpty() && !customerMap.containsKey(email)) {
                            customerMap[email] = Customer(
                                id = rental.customerId.orEmpty(),
                                name = rental.customerName.orEmpty(),
                                email = rental.customerEmail.orEmpty(),
                                role = "customer",
                                company = rental.customerCompany,
                                phone = rental.customerPhone
                            )
                        }
                    }
                }

                val customers = customerMap.values.toList().sortedBy { it.name }

                if (customers.isEmpty()) {
                    Toast.makeText(context, "No customers found in system", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val items = customers.map { "${it.name ?: "Customer"} - ${it.email ?: "N/A"} (${it.company ?: "Indiv"})" }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle("Manage Customers (${customers.size})")
                    .setItems(items) { _, which ->
                        showCustomerDetailsDialog(customers[which])
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomerDetailsDialog(customer: Customer) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                val rentalsResponse = ApiClient.instance.getRentals("Bearer $token")

                val customerOrders = if (salesResponse.isSuccessful && salesResponse.body() != null) {
                    salesResponse.body()!!.filter { it.customerEmail.equals(customer.email, true) || it.customerId == customer.id }
                } else {
                    emptyList()
                }

                val customerRentals = if (rentalsResponse.isSuccessful && rentalsResponse.body() != null) {
                    rentalsResponse.body()!!.filter { it.customerEmail.equals(customer.email, true) || it.customerId == customer.id }
                } else {
                    emptyList()
                }

                val details = StringBuilder()
                details.append("Name: ${customer.name ?: "N/A"}\n")
                details.append("Email: ${customer.email ?: "N/A"}\n")
                details.append("Company: ${customer.company ?: "N/A"}\n")
                details.append("Phone: ${customer.phone ?: "N/A"}\n")
                details.append("Role: ${customer.role ?: "customer"}\n\n")
                
                details.append("--- Sales History (${customerOrders.size}) ---\n")
                if (customerOrders.isEmpty()) details.append("No sales orders found.\n")
                for (ord in customerOrders) {
                    details.append("• #${ord.id ?: "N/A"} - $${ord.totalAmount ?: 0.0} [${ord.status ?: "Processing"}]\n")
                }

                details.append("\n--- Rental History (${customerRentals.size}) ---\n")
                if (customerRentals.isEmpty()) details.append("No active rentals found.\n")
                for (rnt in customerRentals) {
                    details.append("• #${rnt.id ?: "N/A"} - ${rnt.items?.size ?: 0} items [${rnt.status ?: "Active"}]\n")
                }

                AlertDialog.Builder(context)
                    .setTitle("Customer Account: ${customer.name ?: "Details"}")
                    .setMessage(details.toString())
                    .setPositiveButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadOverview() {
        val context = context ?: return
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getAdminOverview("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val metrics = response.body()?.metrics
                    if (metrics != null) {
                        binding.tvSalesRevenue.text = "$${metrics.totalSalesRevenue ?: 0.0}"
                        binding.tvMonthlyRental.text = "$${metrics.monthlyRentalRevenue ?: 0.0}"
                        binding.tvActiveRentals.text = "${metrics.activeRentalsCount ?: 0}"
                        binding.tvStock.text = "${metrics.panelsInWarehouse ?: 0}"
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    Toast.makeText(context, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Log.e("API_DEBUG", "Error: ${response.code()} Body: ${response.errorBody()?.string()}")
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
