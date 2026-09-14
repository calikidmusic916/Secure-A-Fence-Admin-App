package com.example.secureafenceadministrator.ui.rentals

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.data.model.ExtendRentalRequest
import com.example.secureafenceadministrator.data.model.Rental
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
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
        val builder = AlertDialog.Builder(context)
        builder.setTitle("📋 Rental Agreement #${rental.id}")

        val detailText = StringBuilder()
        detailText.append("--- CLIENT INFORMATION ---\n")
        detailText.append("Name: ${rental.customerName}\n")
        if (rental.customerCompany.isNotEmpty()) detailText.append("Company: ${rental.customerCompany}\n")
        if (rental.customerEmail.isNotEmpty()) detailText.append("Email: ${rental.customerEmail}\n")
        if (rental.customerPhone.isNotEmpty()) detailText.append("Phone: ${rental.customerPhone}\n\n")

        detailText.append("--- JOBSITE LOCATION ---\n")
        detailText.append("Address: ${rental.jobsiteAddress}\n")
        if (rental.jobsiteContact.isNotEmpty()) detailText.append("On-Site Contact: ${rental.jobsiteContact}\n")
        detailText.append("\n")

        detailText.append("--- RENTAL TERM & RATE ---\n")
        detailText.append("Start Date: ${rental.startDate}\n")
        detailText.append("End Date: ${rental.endDate}\n")
        detailText.append("Status: ${rental.status.uppercase()}\n")
        detailText.append("Total Monthly Rate: $${rental.monthlyRateTotal} / month\n\n")

        detailText.append("--- DEPLOYED EQUIPMENT ---\n")
        if (rental.items.isNullOrEmpty()) {
            detailText.append("• 1x Temporary Fence Package @ $${rental.monthlyRateTotal}/mo\n")
        } else {
            for (item in rental.items) {
                detailText.append("• ${item.quantity}x ${item.name} @ $${item.monthlyUnitPrice}/mo = $${item.subtotal}/mo\n")
            }
        }

        if (rental.notes.isNotEmpty()) {
            detailText.append("\n--- NOTES & ACCESS CODES ---\n")
            detailText.append("${rental.notes}\n")
        }

        builder.setMessage(detailText.toString())

        val options = arrayOf(
            "📅 Extend Rental Date",
            "➕ Generate Monthly Billing Invoice",
            "🚚 Schedule Pickup Transport",
            "📥 Check-In Equipment Return",
            "📍 Open Google Maps Navigation",
            "🗑️ Delete Rental Record"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showExtendRentalDialog(rental)
                1 -> generateMonthlyInvoice(rental.id)
                2 -> showSchedulePickupDialog(rental)
                3 -> confirmCheckInRental(rental.id)
                4 -> openGoogleMapsNavigation(rental.jobsiteAddress)
                5 -> confirmDeleteRental(rental.id)
            }
        }

        builder.setPositiveButton("Close", null)
        builder.show()
    }

    private fun showExtendRentalDialog(rental: Rental) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Extend Rental Term #${rental.id}")

        val input = EditText(context).apply {
            hint = "New End Date (YYYY-MM-DD)"
            setText(rental.endDate)
            setPadding(32, 32, 32, 32)
        }
        builder.setView(input)

        builder.setPositiveButton("Save New End Date") { _, _ ->
            val newDate = input.text.toString().trim()
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
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.extendRental(
                    "Bearer $token",
                    rentalId,
                    ExtendRentalRequest(endDate = newEndDate)
                )
                if (response.isSuccessful) {
                    Toast.makeText(context, "Rental extended to $newEndDate successfully!", Toast.LENGTH_SHORT).show()
                    loadRentals()
                } else {
                    Toast.makeText(context, "Failed to extend rental", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
                )
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
