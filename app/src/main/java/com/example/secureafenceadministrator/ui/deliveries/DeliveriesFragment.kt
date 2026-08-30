package com.example.secureafenceadministrator.ui.deliveries

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
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Schedule Pickup")

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputOrderId = android.widget.EditText(context).apply { hint = "Order ID (e.g. ORD-101)" }
        val inputDriver = android.widget.EditText(context).apply { hint = "Driver Name" }
        val inputDate = android.widget.EditText(context).apply { hint = "Dispatch Date (YYYY-MM-DD)" }
        val inputDest = android.widget.EditText(context).apply { hint = "Destination Address" }
        val inputNotes = android.widget.EditText(context).apply { hint = "Notes" }

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
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.schedulePickup(
                    "Bearer $token",
                    com.example.secureafenceadministrator.data.model.SchedulePickupRequest(
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
        val token = com.example.secureafenceadministrator.data.network.SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getShipments("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val shipments = response.body()!!
                    val adapter = GenericAdapter(
                        shipments,
                        titleProvider = { "${it.type} #${it.id}" },
                        subtitleProvider = { "Dest: ${it.destination}\nDate: ${it.dispatchDate}" },
                        statusProvider = { "Status: ${it.status}" }
                    )
                    binding.recyclerViewDeliveries.adapter = adapter
                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    com.example.secureafenceadministrator.data.network.SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load deliveries", Toast.LENGTH_SHORT).show()
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
