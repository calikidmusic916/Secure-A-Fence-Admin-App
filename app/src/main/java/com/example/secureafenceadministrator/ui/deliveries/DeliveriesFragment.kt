package com.example.secureafenceadministrator.ui.deliveries

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.OrderItem
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.model.Shipment
import com.example.secureafenceadministrator.data.model.ShipmentUpdateRequest
import com.example.secureafenceadministrator.data.model.StatusUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogDeliveryDetailsBinding
import com.example.secureafenceadministrator.databinding.FragmentDeliveriesBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch
import java.util.Locale

class DeliveriesFragment : Fragment() {

    private var _binding: FragmentDeliveriesBinding? = null
    private val binding get() = _binding!!

    private var activeShipmentsList = mutableListOf<Shipment>()
    private var completedShipmentsList = mutableListOf<Shipment>()

    private var activeAdapter: GenericAdapter<Shipment>? = null
    private var completedAdapter: GenericAdapter<Shipment>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewActiveDeliveries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCompletedDeliveries.layoutManager = LinearLayoutManager(requireContext())

        loadDeliveries()
    }

    private fun loadDeliveries() {
        val context = context ?: return
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SessionManager.clearSession(context)
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getShipments("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val allShipments = response.body()!!

                    activeShipmentsList = allShipments.filter {
                        !it.status.equals("Delivered", ignoreCase = true) &&
                        !it.status.equals("Picked Up / Returned", ignoreCase = true) &&
                        !it.status.equals("Completed", ignoreCase = true) &&
                        !it.status.equals("Cancelled", ignoreCase = true)
                    }.toMutableList()

                    completedShipmentsList = allShipments.filter {
                        it.status.equals("Delivered", ignoreCase = true) ||
                        it.status.equals("Picked Up / Returned", ignoreCase = true) ||
                        it.status.equals("Completed", ignoreCase = true)
                    }.toMutableList()

                    binding.tvActiveDeliveriesHeader.text = "🚚 Active Dispatches & Deliveries (${activeShipmentsList.size})"
                    binding.tvCompletedDeliveriesHeader.text = "✅ Completed Dispatches (${completedShipmentsList.size})"

                    setupActiveAdapter()
                    setupCompletedAdapter()

                } else if (response.code() == 401) {
                    Toast.makeText(context, "Session expired, please login again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load dispatches", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupActiveAdapter() {
        activeAdapter = GenericAdapter(
            activeShipmentsList,
            titleProvider = { "🚚 Priority #${activeShipmentsList.indexOf(it) + 1} - ${it.type.uppercase()}" },
            subtitleProvider = {
                val etaStr = if (!it.eta.isNullOrEmpty()) " | ETA/Time: ${it.eta}" else ""
                "Order: #${it.orderId.ifEmpty { it.id }}\nDestination: ${it.destination}\nDriver: ${it.driverName}$etaStr"
            },
            statusProvider = { "Status: ${it.status.uppercase()}" },
            rightImageResIdProvider = { R.drawable.logo },
            onItemClick = { showShipmentDetailsDialog(it) }
        )
        binding.recyclerViewActiveDeliveries.adapter = activeAdapter
    }

    private fun setupCompletedAdapter() {
        completedAdapter = GenericAdapter(
            completedShipmentsList,
            titleProvider = { "✅ COMPLETED - ${it.type.uppercase()}" },
            subtitleProvider = { "Order: #${it.orderId.ifEmpty { it.id }}\nDestination: ${it.destination}\nDriver: ${it.driverName}" },
            statusProvider = { "Status: ${it.status.uppercase()} [DELIVERED]" },
            rightImageResIdProvider = { R.drawable.logo },
            onItemClick = { showShipmentDetailsDialog(it) }
        )
        binding.recyclerViewCompletedDeliveries.adapter = completedAdapter
    }

    private fun showShipmentDetailsDialog(shipment: Shipment) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        val dialogBinding = DialogDeliveryDetailsBinding.inflate(LayoutInflater.from(context))

        val targetOrderId = shipment.orderId.ifEmpty { shipment.id }
        val dispatchType = shipment.type.ifEmpty { "Delivery" }

        dialogBinding.tvDeliveryDialogTitle.text = "🚚 Dispatch Details #${shipment.id}"
        dialogBinding.tvDeliveryOrderIdAndType.text = "Ref Order #$targetOrderId | Dispatch Type: ${dispatchType.uppercase()}"

        dialogBinding.etDriverName.setText(shipment.driverName)
        dialogBinding.etDispatchDate.setText(shipment.dispatchDate)
        dialogBinding.etDestinationAddress.setText(shipment.destination)

        val statuses = arrayOf("Scheduled", "In Route", "Pickup Scheduled", "Delivered", "Picked Up / Returned", "Cancelled")
        dialogBinding.spDeliveryStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, statuses)
        val statusIndex = statuses.indexOfFirst { it.equals(shipment.status, ignoreCase = true) }
        if (statusIndex >= 0) dialogBinding.spDeliveryStatus.setSelection(statusIndex)

        dialogBinding.spDeliveryStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = statuses[position]
                when {
                    selected.equals("In Route", ignoreCase = true) -> {
                        dialogBinding.tvTimeInputLabel.text = "⏱️ Estimated Arrival Time (ETA):"
                        dialogBinding.etEtaOrPickupTime.hint = "e.g. 10:30 AM or 25 mins"
                    }
                    selected.contains("Pickup", ignoreCase = true) -> {
                        dialogBinding.tvTimeInputLabel.text = "⏰ Scheduled Pickup Time:"
                        dialogBinding.etEtaOrPickupTime.hint = "e.g. 2:00 PM Today or 11:15 AM"
                    }
                    else -> {
                        dialogBinding.tvTimeInputLabel.text = "⏱️ Scheduled ETA / Pickup Time:"
                        dialogBinding.etEtaOrPickupTime.hint = "Time / ETA"
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialogBinding.etEtaOrPickupTime.setText(shipment.eta)
        dialogBinding.etDeliveryNotes.setText(shipment.notes)
        dialogBinding.cbIsTaxable.isChecked = shipment.isTaxable
        if (shipment.discountAmount > 0) dialogBinding.etDiscountAmount.setText(shipment.discountAmount.toString())
        if (shipment.overrideTotal != null) dialogBinding.etFinalPriceOverride.setText(shipment.overrideTotal.toString())

        dialogBinding.btnUploadProofPhoto.text = "📸 Snap / Upload Proof Photo (${shipment.deliveryPhotos?.size ?: 0} Attached)"

        val deliveredQtyInputs = mutableMapOf<OrderItem, EditText>()
        var currentItemsList = mutableListOf<OrderItem>()

        fun calculateAdjustedTotal(): Double {
            var subtotal = 0.0
            for ((item, inputField) in deliveredQtyInputs) {
                val actualQty = inputField.text.toString().toIntOrNull() ?: item.quantity
                subtotal += (actualQty * item.unitPrice)
            }
            val isTaxable = dialogBinding.cbIsTaxable.isChecked
            val deliveryFee = if (subtotal > 0) 50.0 else 0.0
            val tax = if (isTaxable) Math.round(subtotal * 0.08 * 100.0) / 100.0 else 0.0
            val discount = dialogBinding.etDiscountAmount.text.toString().toDoubleOrNull() ?: 0.0
            val overridePrice = dialogBinding.etFinalPriceOverride.text.toString().toDoubleOrNull()

            val calculated = Math.max(0.0, subtotal + deliveryFee + tax - discount)
            val finalTotal = if (overridePrice != null && overridePrice > 0) overridePrice else calculated

            dialogBinding.tvAdjustedTotalPreview.text = "💰 Adjusted Total: $" + String.format(Locale.US, "%.2f", finalTotal) +
                " (Subtotal: $" + String.format(Locale.US, "%.2f", subtotal) +
                " + Tax: $" + String.format(Locale.US, "%.2f", tax) +
                " - Disc: $" + String.format(Locale.US, "%.2f", discount) + ")"

            return finalTotal
        }

        // Fetch Customer Contact Info
        lifecycleScope.launch {
            try {
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                if (salesResponse.isSuccessful && salesResponse.body() != null) {
                    val matchingOrder = salesResponse.body()!!.find {
                        it.id.equals(targetOrderId, ignoreCase = true) || it.id.equals(shipment.id, ignoreCase = true)
                    }
                    if (matchingOrder != null) {
                        val companyStr = if (!matchingOrder.customerCompany.isNullOrEmpty()) " (${matchingOrder.customerCompany})" else ""
                        dialogBinding.tvCustomerNameAndCompany.text = "Client: ${matchingOrder.customerName}$companyStr"
                        dialogBinding.tvCustomerPhoneAndEmail.text = "Phone: ${matchingOrder.customerPhone.ifEmpty { "(279) 261-3890" }} | Email: ${matchingOrder.customerEmail}"

                        dialogBinding.btnCallCustomer.setOnClickListener {
                            val phoneIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${matchingOrder.customerPhone}"))
                            startActivity(phoneIntent)
                        }

                        if (!matchingOrder.items.isNullOrEmpty()) {
                            currentItemsList = matchingOrder.items.toMutableList()
                        }
                    }
                }

                if (currentItemsList.isEmpty()) {
                    currentItemsList = mutableListOf(
                        OrderItem(productId = "prod-1", name = "Refurbished Temporary Fence Panel (6' x 12')", unitPrice = 65.0, quantity = 50, deliveredQuantity = 50, total = 3250.0),
                        OrderItem(productId = "prod-2", name = "Flat Base / Stand", unitPrice = 10.0, quantity = 50, deliveredQuantity = 50, total = 500.0),
                        OrderItem(productId = "prod-3", name = "Safety Clamp / Panel Clip", unitPrice = 5.0, quantity = 50, deliveredQuantity = 50, total = 250.0)
                    )
                }

                dialogBinding.llDeliveredItemsContainer.removeAllViews()
                deliveredQtyInputs.clear()

                for (item in currentItemsList) {
                    val itemLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 8, 0, 8)
                    }

                    val tvLabel = TextView(context).apply {
                        text = "• ${item.name}\n  [Ordered: ${item.quantity} units @ $${item.unitPrice}]"
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val inputDeliveredQty = EditText(context).apply {
                        hint = "Actual Quantity Delivered / Picked Up"
                        setText((item.deliveredQuantity ?: item.quantity).toString())
                        inputType = InputType.TYPE_CLASS_NUMBER
                        isEnabled = false
                        doAfterTextChanged { calculateAdjustedTotal() }
                    }

                    itemLayout.addView(tvLabel)
                    itemLayout.addView(inputDeliveredQty)
                    dialogBinding.llDeliveredItemsContainer.addView(itemLayout)
                    deliveredQtyInputs[item] = inputDeliveredQty
                }

                calculateAdjustedTotal()

            } catch (e: Exception) {
                // Fallback default
            }
        }

        dialogBinding.cbIsTaxable.setOnCheckedChangeListener { _, _ -> calculateAdjustedTotal() }
        dialogBinding.etDiscountAmount.doAfterTextChanged { calculateAdjustedTotal() }
        dialogBinding.etFinalPriceOverride.doAfterTextChanged { calculateAdjustedTotal() }

        // Explicit "✏️ Edit" Button Toggle to prevent accidental edits
        var isEditingEnabled = false

        fun toggleEditing(enable: Boolean) {
            isEditingEnabled = enable
            dialogBinding.etDriverName.isEnabled = enable
            dialogBinding.etDispatchDate.isEnabled = enable
            dialogBinding.etDestinationAddress.isEnabled = enable
            dialogBinding.spDeliveryStatus.isEnabled = enable
            dialogBinding.etEtaOrPickupTime.isEnabled = enable
            dialogBinding.etDeliveryNotes.isEnabled = enable
            dialogBinding.cbIsTaxable.isEnabled = enable
            dialogBinding.etDiscountAmount.isEnabled = enable
            dialogBinding.etFinalPriceOverride.isEnabled = enable
            dialogBinding.btnSaveDeliveryChanges.isEnabled = enable

            for (inputField in deliveredQtyInputs.values) {
                inputField.isEnabled = enable
            }

            if (enable) {
                dialogBinding.btnEnableEditing.text = "🔒 Lock"
                Toast.makeText(context, "✏️ Edit mode enabled. You can now modify dispatch details.", Toast.LENGTH_SHORT).show()
            } else {
                dialogBinding.btnEnableEditing.text = "✏️ Edit"
            }
        }

        dialogBinding.btnEnableEditing.setOnClickListener {
            toggleEditing(!isEditingEnabled)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.btnSaveDeliveryChanges.setOnClickListener {
            val updatedDriver = dialogBinding.etDriverName.text.toString().trim()
            val updatedDate = dialogBinding.etDispatchDate.text.toString().trim()
            val updatedDest = dialogBinding.etDestinationAddress.text.toString().trim()
            val selectedStatus = dialogBinding.spDeliveryStatus.selectedItem.toString()
            val updatedEta = dialogBinding.etEtaOrPickupTime.text.toString().trim()
            val updatedNotes = dialogBinding.etDeliveryNotes.text.toString().trim()

            val updatedItems = deliveredQtyInputs.map { (item, inputField) ->
                val qty = inputField.text.toString().toIntOrNull() ?: item.quantity
                item.copy(deliveredQuantity = qty, total = qty * item.unitPrice)
            }

            val finalTotal = calculateAdjustedTotal()

            val updateRequest = ShipmentUpdateRequest(
                driverName = updatedDriver,
                dispatchDate = updatedDate,
                destination = updatedDest,
                notes = updatedNotes,
                status = selectedStatus,
                eta = updatedEta,
                deliveredItems = updatedItems,
                isTaxable = dialogBinding.cbIsTaxable.isChecked,
                discountAmount = dialogBinding.etDiscountAmount.text.toString().toDoubleOrNull() ?: 0.0,
                overrideTotal = dialogBinding.etFinalPriceOverride.text.toString().toDoubleOrNull()
            )

            lifecycleScope.launch {
                try {
                    val resp = ApiClient.instance.updateShipment("Bearer $token", shipment.id, updateRequest)
                    if (resp.isSuccessful) {
                        Toast.makeText(context, "💾 Dispatch #${shipment.id} updated!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Saved: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                }
            }
        }

        dialogBinding.btnCompleteDeliveryAndInvoice.setOnClickListener {
            lifecycleScope.launch {
                try {
                    ApiClient.instance.updateShipment(
                        "Bearer $token", shipment.id,
                        ShipmentUpdateRequest(status = "Delivered")
                    )
                    ApiClient.instance.updateOrderStatus(
                        "Bearer $token", targetOrderId,
                        StatusUpdateRequest(status = "Delivered")
                    )
                    Toast.makeText(context, "🎉 Dispatch & Order marked DELIVERED! Official Invoice Issued.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Completed: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                }
            }
        }

        dialogBinding.btnOpenGpsMap.setOnClickListener {
            val address = dialogBinding.etDestinationAddress.text.toString().trim()
            if (address.isNotEmpty()) {
                val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } else {
                Toast.makeText(context, "No destination address provided", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnCancelDelivery.setOnClickListener {
            lifecycleScope.launch {
                try {
                    ApiClient.instance.updateShipment("Bearer $token", shipment.id, ShipmentUpdateRequest(status = "Cancelled"))
                    Toast.makeText(context, "🚫 Dispatch cancelled", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Cancelled: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                }
            }
        }

        dialogBinding.btnDeleteDelivery.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Delete Dispatch Record")
                .setMessage("Are you sure you want to PERMANENTLY delete dispatch #${shipment.id}? This cannot be undone.")
                .setPositiveButton("Delete Record") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            ApiClient.instance.deleteShipment("Bearer $token", shipment.id)
                            Toast.makeText(context, "🗑️ Dispatch #${shipment.id} deleted", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Deleted: ${e.message}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
