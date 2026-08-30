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
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load overview", Toast.LENGTH_SHORT).show()
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
