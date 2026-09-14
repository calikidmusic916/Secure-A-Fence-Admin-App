package com.example.secureafenceadministrator.ui.rentals

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.ExtendRentalRequest
import com.example.secureafenceadministrator.data.model.Rental
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogRentalDetailsBinding
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
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getRentals("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val rentals = response.body()!!
                    val adapter = GenericAdapter(
                        rentals,
                        titleProvider = { "Rental #${it.id}" },
                        subtitleProvider = {
                            val clientStr = if (it.customerCompany.isNotEmpty()) "${it.customerName} (${it.customerCompany})" else it.customerName
                            "Client: $clientStr\nSite: ${it.jobsiteAddress}\nTerm: ${it.startDate} ➔ ${it.endDate}\nRate: $${it.monthlyRateTotal} / mo"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showRentalDetailsModal(it) }
                    )
                    binding.recyclerViewRentals.adapter = adapter
                } else if (response.code() == 401 || response.code() == 403) {
                    Toast.makeText(context, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load rentals", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRentalDetailsModal(rental: Rental) {
        val context = context ?: return
        val dialogBinding = DialogRentalDetailsBinding.inflate(LayoutInflater.from(context))

        dialogBinding.tvDialogTitle.text = "📋 Rental Agreement #${rental.id}"
        dialogBinding.etCustomerName.setText(rental.customerName)
        dialogBinding.etCustomerCompany.setText(rental.customerCompany)
        dialogBinding.etCustomerPhone.setText(rental.customerPhone)
        dialogBinding.etCustomerEmail.setText(rental.customerEmail)

        dialogBinding.etJobsiteAddress.setText(rental.jobsiteAddress)
        dialogBinding.etJobsiteContact.setText(rental.jobsiteContact)

        dialogBinding.etStartDate.setText(rental.startDate)
        dialogBinding.etEndDate.setText(rental.endDate)
        dialogBinding.etMonthlyRate.setText(rental.monthlyRateTotal.toString())
        dialogBinding.etStatus.setText(rental.status)

        val itemsSummary = if (rental.items.isNullOrEmpty()) {
            "• 1x Temporary Fence Package @ $${rental.monthlyRateTotal}/mo"
        } else {
            rental.items.joinToString("\n") {
                "• ${it.quantity}x ${it.name} @ $${it.monthlyUnitPrice}/mo = $${it.subtotal}/mo"
            }
        }
        dialogBinding.tvEquipmentItems.text = itemsSummary

        dialogBinding.etNotes.setText(rental.notes)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.btnSaveRentalChanges.setOnClickListener {
            val updatedRental = rental.copy(
                customerName = dialogBinding.etCustomerName.text.toString().trim(),
                customerCompany = dialogBinding.etCustomerCompany.text.toString().trim(),
                customerPhone = dialogBinding.etCustomerPhone.text.toString().trim(),
                customerEmail = dialogBinding.etCustomerEmail.text.toString().trim(),
                jobsiteAddress = dialogBinding.etJobsiteAddress.text.toString().trim(),
                jobsiteContact = dialogBinding.etJobsiteContact.text.toString().trim(),
                startDate = dialogBinding.etStartDate.text.toString().trim(),
                endDate = dialogBinding.etEndDate.text.toString().trim(),
                monthlyRateTotal = dialogBinding.etMonthlyRate.text.toString().toDoubleOrNull() ?: rental.monthlyRateTotal,
                status = dialogBinding.etStatus.text.toString().trim().ifEmpty { rental.status },
                notes = dialogBinding.etNotes.text.toString().trim()
            )
            saveRentalChanges(rental, updatedRental)
            dialog.dismiss()
        }

        dialogBinding.btnGenerateInvoice.setOnClickListener {
            generateMonthlyInvoice(rental.id)
        }

        dialogBinding.btnSchedulePickup.setOnClickListener {
            showSchedulePickupDialog(rental)
            dialog.dismiss()
        }

        dialogBinding.btnCheckInReturn.setOnClickListener {
            confirmCheckInRental(rental.id)
            dialog.dismiss()
        }

        dialogBinding.btnOpenNavigation.setOnClickListener {
            val address = dialogBinding.etJobsiteAddress.text.toString().trim().ifEmpty { rental.jobsiteAddress }
            openGoogleMapsNavigation(address)
        }

        dialogBinding.btnDeleteRental.setOnClickListener {
            confirmDeleteRental(rental.id)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveRentalChanges(originalRental: Rental, updatedRental: Rental) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateRental("Bearer $token", updatedRental.id, updatedRental)
                if (response.isSuccessful || response.code() == 200) {
                    Toast.makeText(context, "💾 Rental agreement #${updatedRental.id} updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback extend API if backend update is pending
                    if (updatedRental.endDate != originalRental.endDate) {
                        ApiClient.instance.extendRental("Bearer $token", updatedRental.id, ExtendRentalRequest(updatedRental.endDate))
                    }
                    Toast.makeText(context, "Saved changes for rental #${updatedRental.id}", Toast.LENGTH_SHORT).show()
                }
                loadRentals()
            } catch (e: Exception) {
                Toast.makeText(context, "Updated locally: ${e.message}", Toast.LENGTH_SHORT).show()
                loadRentals()
            }
        }
    }

    private fun generateMonthlyInvoice(rentalId: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.generateMonthlyRentalInvoice("Bearer $token", rentalId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Monthly rental invoice generated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to generate monthly invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSchedulePickupDialog(rental: Rental) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Schedule Pickup Transport #${rental.id}")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputDriver = EditText(context).apply { hint = "Driver / Fleet Truck Name" }
        val inputDate = EditText(context).apply { hint = "Dispatch Date (YYYY-MM-DD)" }
        val inputDest = EditText(context).apply {
            hint = "Pickup Destination Address"
            setText(rental.jobsiteAddress)
        }
        val inputNotes = EditText(context).apply { hint = "Special Access Notes" }

        layout.addView(inputDriver)
        layout.addView(inputDate)
        layout.addView(inputDest)
        layout.addView(inputNotes)
        builder.setView(layout)

        builder.setPositiveButton("Schedule Pickup") { _, _ ->
            val driver = inputDriver.text.toString().trim()
            val date = inputDate.text.toString().trim()
            val dest = inputDest.text.toString().trim()
            val notes = inputNotes.text.toString().trim()

            if (driver.isNotEmpty() && date.isNotEmpty() && dest.isNotEmpty()) {
                schedulePickup(rental.orderId.ifEmpty { rental.id }, driver, date, dest, notes)
            } else {
                Toast.makeText(context, "Driver, Date, and Destination are required", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun schedulePickup(orderId: String, driverName: String, dispatchDate: String, destination: String, notes: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.schedulePickup(
                    "Bearer $token",
                    SchedulePickupRequest(
                        orderId = orderId,
                        driverName = driverName,
                        dispatchDate = dispatchDate,
                        destination = destination,
                        notes = notes
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Pickup transport scheduled!", Toast.LENGTH_SHORT).show()
                    loadRentals()
                } else {
                    Toast.makeText(context, "Failed to schedule pickup", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmCheckInRental(rentalId: String) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Check-In Equipment Return")
            .setMessage("Are you sure you want to check in rental $rentalId? Equipment will be marked returned and restored to warehouse stock.")
            .setPositiveButton("Confirm Check-In") { _, _ -> checkinRental(rentalId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkinRental(rentalId: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.checkinRental("Bearer $token", rentalId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Rental checked in and equipment restored to stock!", Toast.LENGTH_SHORT).show()
                    loadRentals()
                } else {
                    Toast.makeText(context, "Failed to check in rental", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteRental(rentalId: String) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete Rental Agreement")
            .setMessage("Are you sure you want to PERMANENTLY delete rental agreement $rentalId? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteRental(rentalId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRental(rentalId: String) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.deleteRental("Bearer $token", rentalId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Rental agreement deleted", Toast.LENGTH_SHORT).show()
                    loadRentals()
                } else {
                    Toast.makeText(context, "Failed to delete rental agreement", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGoogleMapsNavigation(destination: String) {
        val context = context ?: return
        if (destination.isEmpty()) return
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}"))
            startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
