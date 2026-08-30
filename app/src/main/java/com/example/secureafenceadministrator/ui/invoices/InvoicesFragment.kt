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
                        subtitleProvider = { "Customer: ${it.customerName}\nAmount: $${it.totalAmount}" },
                        statusProvider = { "Status: ${it.status}" }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
