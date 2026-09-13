package com.example.secureafenceadministrator.ui.deliveries

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
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.model.Shipment
import com.example.secureafenceadministrator.data.model.ShipmentUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentDeliveriesBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch

class DeliveriesFragment : Fragment() {

    private var _binding: FragmentDeliveriesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewDeliveries.layoutManager = LinearLayoutManager(requireContext())
        loadDeliveries()

        binding.btnSchedulePickup.setOnClickListener {
            showSchedulePickupDialog()
        }
    }

    private fun showSchedulePickupDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Schedule Pickup")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputOrderId = EditText(context).apply { hint = "Order ID (e.g. ORD-101)" }
        val inputDriver = EditText(context).apply { hint = "Driver / Truck Name" }
        val inputDate = EditText(context).apply { hint = "Dispatch Date (YYYY-MM-DD)" }
        val inputDest = EditText(context).apply { hint = "Destination Address" }
        val inputNotes = EditText(context).apply { hint = "Special Access Notes" }

        layout.addView(inputOrderId)
        layout.addView(inputDriver)
        layout.addView(inputDate)
        layout.addView(inputDest)
        layout.addView(inputNotes)
        builder.setView(layout)

        builder.setPositiveButton("Schedule") { _, _ ->
            val orderId = inputOrderId.text.toString()
            val driver = inputDriver.text.toString()
            val date = inputDate.text.toString()
            val dest = inputDest.text.toString()
            val notes = inputNotes.text.toString()

            if (orderId.isNotEmpty() && driver.isNotEmpty() && date.isNotEmpty() && dest.isNotEmpty()) {
                schedulePickup(orderId, driver, date, dest, notes)
            } else {
                Toast.makeText(context, "Please fill out required fields", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Pickup scheduled successfully", Toast.LENGTH_SHORT).show()
                    loadDeliveries()
                } else {
                    Toast.makeText(context, "Failed to schedule pickup", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDeliveries() {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getShipments("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val shipments = response.body()!!
                    val adapter = GenericAdapter(
                        shipments,
                        titleProvider = { "${it.type} #${it.id}" },
                        subtitleProvider = { "Driver: ${it.driverName}\nDest: ${it.destination}\nDate: ${it.dispatchDate}" },
                        statusProvider = { "Status: ${it.status}" },
                        onItemClick = { showShipmentDetailsDialog(it) }
                    )
                    binding.recyclerViewDeliveries.adapter = adapter
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load deliveries", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showShipmentDetailsDialog(shipment: Shipment) {
        val context = context ?: return
        val details = """
            Type: ${shipment.type}
            Order Ref: ${shipment.orderId}
            Driver / Fleet Truck: ${shipment.driverName}
            Dispatch Date: ${shipment.dispatchDate}
            Destination: ${shipment.destination}
            Status: ${shipment.status}
            Notes: ${shipment.notes ?: "None"}
        """.trimIndent()

        val options = arrayOf("Update Status", "Assign Driver / Truck", "📍 Open Google Maps Navigation")
        AlertDialog.Builder(context)
            .setTitle("Shipment ${shipment.id} (${shipment.type})")
            .setMessage(details)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUpdateStatusDialog(shipment)
                    1 -> showAssignDriverDialog(shipment)
                    2 -> openGoogleMapsNavigation(shipment.destination)
                }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openGoogleMapsNavigation(destination: String) {
        val context = context ?: return
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

    private fun showUpdateStatusDialog(shipment: Shipment) {
        val context = context ?: return
        val options = arrayOf("Scheduled", "In Route", "Delivered", "Returned")
        AlertDialog.Builder(context)
            .setTitle("Update Shipment Status")
            .setItems(options) { _, which ->
                val newStatus = options[which]
                updateShipment(shipment.id, ShipmentUpdateRequest(status = newStatus))
            }
            .show()
    }

    private fun showAssignDriverDialog(shipment: Shipment) {
        val context = context ?: return
        val drivers = arrayOf("Truck 1 - Mike", "Truck 2 - Dave", "Truck 3 - Alex", "Unassigned Dispatcher")
        AlertDialog.Builder(context)
            .setTitle("Assign Driver / Truck")
            .setItems(drivers) { _, which ->
                val selectedDriver = drivers[which]
                updateShipment(shipment.id, ShipmentUpdateRequest(driverName = selectedDriver))
            }
            .show()
    }

    private fun updateShipment(shipmentId: String, request: ShipmentUpdateRequest) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateShipment("Bearer $token", shipmentId, request)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Shipment updated successfully", Toast.LENGTH_SHORT).show()
                    loadDeliveries()
                } else {
                    Toast.makeText(context, "Failed to update shipment", Toast.LENGTH_SHORT).show()
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
