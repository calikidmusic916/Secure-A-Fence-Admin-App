package com.example.secureafenceadministrator.ui.rentals

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
import com.example.secureafenceadministrator.databinding.FragmentRentalsBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch

class RentalsFragment : Fragment() {

    private var _binding: FragmentRentalsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRentalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewRentals.layoutManager = LinearLayoutManager(requireContext())
        loadRentals()
    }

    private fun loadRentals() {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getRentals("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val rentals = response.body()!!
                    val adapter = GenericAdapter(
                        rentals,
                        titleProvider = { "Rental #${it.id} (${it.customerCompany})" },
                        subtitleProvider = { "Site: ${it.jobsiteAddress}\nEnd Date: ${it.endDate}" },
                        statusProvider = { "Status: ${it.status}" },
                        onItemClick = { showExtendDialog(it) }
                    )
                    binding.recyclerViewRentals.adapter = adapter
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load rentals", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExtendDialog(rental: com.example.secureafenceadministrator.data.model.Rental) {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Rental Details #${rental.id}")

        val detailText = StringBuilder()
        detailText.append("Customer: ${rental.customerName}\n")
        detailText.append("Company: ${rental.customerCompany}\n")
        detailText.append("Phone: ${rental.customerPhone}\n")
        detailText.append("Jobsite: ${rental.jobsiteAddress}\n")
        detailText.append("Start Date: ${rental.startDate}\n")
        detailText.append("End Date: ${rental.endDate}\n")
        detailText.append("Status: ${rental.status}\n\n")

        detailText.append("--- Rented Line Items ---\n")
        if (rental.items.isNullOrEmpty()) {
            detailText.append("• 1x Temporary Fence Package @ $${rental.monthlyRateTotal}/mo\n")
        } else {
            for (item in rental.items) {
                detailText.append("• ${item.quantity}x ${item.name} @ $${item.monthlyUnitPrice}/mo = $${item.subtotal}/mo\n")
            }
        }
        detailText.append("\n")
        detailText.append("Monthly Rate Total: $${rental.monthlyRateTotal}/mo\n")
        if (!rental.notes.isNullOrEmpty()) {
            detailText.append("Notes: ${rental.notes}\n")
        }

        builder.setMessage(detailText.toString())

        builder.setPositiveButton("Extend Rental Date") { _, _ ->
            showChangeEndDateDialog(rental)
        }

        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun showChangeEndDateDialog(rental: com.example.secureafenceadministrator.data.model.Rental) {
        val context = context ?: return
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Extend Rental Period #${rental.id}")

        val input = android.widget.EditText(context).apply {
            hint = "New End Date (YYYY-MM-DD)"
            setText(rental.endDate)
            setPadding(32, 32, 32, 32)
        }
        builder.setView(input)

        builder.setPositiveButton("Save New End Date") { _, _ ->
            val newDate = input.text.toString()
            if (newDate.isNotEmpty()) {
                extendRental(rental.id, newDate)
            } else {
                Toast.makeText(context, "Please enter a valid date", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun extendRental(rentalId: String, newEndDate: String) {
        val context = context ?: return
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.extendRental(
                    "Bearer $token",
                    rentalId,
                    com.example.secureafenceadministrator.data.model.ExtendRentalRequest(endDate = newEndDate)
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Rental extended successfully", Toast.LENGTH_SHORT).show()
                    loadRentals()
                } else {
                    Toast.makeText(context, "Failed to extend rental", Toast.LENGTH_SHORT).show()
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
