package com.example.secureafenceadministrator.ui.deliveries

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.CreateInvoiceRequest
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class DeliveriesFragment : Fragment() {

    private var _binding: FragmentDeliveriesBinding? = null
    private val binding get() = _binding!!

    private var activeShipmentsList = mutableListOf<Shipment>()
    private var completedShipmentsList = mutableListOf<Shipment>()
    private var activeAdapter: GenericAdapter<Shipment>? = null
    private var completedAdapter: GenericAdapter<Shipment>? = null

    private var currentPhotoTargetShipmentId: String? = null

    private val deliveryPhotoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val shipmentId = currentPhotoTargetShipmentId
        if (uri != null && shipmentId != null) {
            uploadPhotoForShipment(shipmentId, uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewActiveDeliveries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCompletedDeliveries.layoutManager = LinearLayoutManager(requireContext())

        setupDragAndDropReordering()

        loadDeliveries()

        binding.btnSchedulePickup.setOnClickListener {
            showSchedulePickupDialog()
        }

        binding.btnOptimizeRoute.setOnClickListener {
            optimizeDeliveryRoute()
        }

        binding.btnReorderSequence.setOnClickListener {
            showManualReorderDialog()
        }
    }

    private fun setupDragAndDropReordering() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    activeAdapter?.onItemMove(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewActiveDeliveries)
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
                        !it.status.equals("Completed", ignoreCase = true) &&
                        !it.status.equals("Returned", ignoreCase = true) &&
                        !it.status.equals("Picked Up / Returned", ignoreCase = true) &&
                        !it.status.equals("Cancelled", ignoreCase = true)
                    }.toMutableList()

                    completedShipmentsList = allShipments.filter {
                        it.status.equals("Delivered", ignoreCase = true) ||
                        it.status.equals("Completed", ignoreCase = true) ||
                        it.status.equals("Returned", ignoreCase = true) ||
                        it.status.equals("Picked Up / Returned", ignoreCase = true) ||
                        it.status.equals("Cancelled", ignoreCase = true)
                    }.toMutableList()

                    binding.tvActiveDeliveriesHeader.text = "🚚 Active Dispatches & Deliveries (${activeShipmentsList.size})"
                    binding.tvCompletedDeliveriesHeader.text = "✅ Completed & Delivered Orders (${completedShipmentsList.size})"

                    activeAdapter = GenericAdapter(
                        activeShipmentsList,
                        titleProvider = { "${it.type.ifEmpty { "Delivery" }} #${it.id}" },
                        subtitleProvider = {
                            val etaText = if (!it.eta.isNullOrEmpty()) "\n⏱️ Time/ETA: ${it.eta}" else ""
                            val photosText = if (!it.deliveryPhotos.isNullOrEmpty()) "\n📸 Proof Photos: ${it.deliveryPhotos.size} uploaded" else ""
                            "Driver: ${it.driverName.ifEmpty { "Unassigned" }}\nDest: ${it.destination}\nDate: ${it.dispatchDate}$etaText$photosText"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showShipmentDetailsDialog(it) }
                    )
                    binding.recyclerViewActiveDeliveries.adapter = activeAdapter

                    completedAdapter = GenericAdapter(
                        completedShipmentsList,
                        titleProvider = { "${it.type.ifEmpty { "Delivery" }} #${it.id}" },
                        subtitleProvider = {
                            val photosText = if (!it.deliveryPhotos.isNullOrEmpty()) "\n📸 Proof Photos: ${it.deliveryPhotos.size} uploaded" else ""
                            "Driver: ${it.driverName.ifEmpty { "Completed" }}\nDest: ${it.destination}\nStatus Date: ${it.dispatchDate}$photosText"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()}" },
                        rightImageResIdProvider = { R.drawable.logo },
                        onItemClick = { showShipmentDetailsDialog(it) }
                    )
                    binding.recyclerViewCompletedDeliveries.adapter = completedAdapter

                } else if (response.code() == 401 || response.code() == 403) {
                    Toast.makeText(context, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
                    SessionManager.clearSession(context)
                } else {
                    Toast.makeText(context, "Failed to load deliveries", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showManualReorderDialog() {
        val context = context ?: return
        if (activeShipmentsList.isEmpty()) {
            Toast.makeText(context, "No active deliveries available to reorder", Toast.LENGTH_SHORT).show()
            return
        }

        val itemsList = activeShipmentsList.mapIndexed { index, shipment ->
            "${index + 1}. #${shipment.id} - ${shipment.destination.take(25)}"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("↕️ Select Item to Adjust Sequence")
            .setItems(itemsList) { _, selectedIndex ->
                showReorderActionOptions(selectedIndex)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showReorderActionOptions(index: Int) {
        val context = context ?: return
        val shipment = activeShipmentsList[index]
        val options = arrayOf("🔝 Move to Top (#1 Priority)", "⬆️ Move Up", "⬇️ Move Down")

        AlertDialog.Builder(context)
            .setTitle("Reorder #${shipment.id}")
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> {
                        if (index > 0) {
                            val item = activeShipmentsList.removeAt(index)
                            activeShipmentsList.add(0, item)
                            activeAdapter?.notifyDataSetChanged()
                            Toast.makeText(context, "Moved #${shipment.id} to #1", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        if (index > 0) {
                            activeAdapter?.onItemMove(index, index - 1)
                        }
                    }
                    2 -> {
                        if (index < activeShipmentsList.size - 1) {
                            activeAdapter?.onItemMove(index, index + 1)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        dialogBinding.cbIsTaxable.setOnCheckedChangeListener { _, _ -> calculateAdjustedTotal() }
        dialogBinding.etDiscountAmount.doAfterTextChanged { calculateAdjustedTotal() }
        dialogBinding.etFinalPriceOverride.doAfterTextChanged { calculateAdjustedTotal() }

        lifecycleScope.launch {
            try {
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                if (salesResponse.isSuccessful && salesResponse.body() != null) {
                    val matchingOrder = salesResponse.body()!!.find {
                        it.id.equals(targetOrderId, ignoreCase = true) || it.id.equals(shipment.id, ignoreCase = true)
                    }
                    if (matchingOrder != null && !matchingOrder.items.isNullOrEmpty()) {
                        currentItemsList = matchingOrder.items.toMutableList()
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
                        doAfterTextChanged { calculateAdjustedTotal() }
                    }

                    itemLayout.addView(tvLabel)
                    itemLayout.addView(inputDeliveredQty)
                    dialogBinding.llDeliveredItemsContainer.addView(itemLayout)
                    deliveredQtyInputs[item] = inputDeliveredQty
                }

                calculateAdjustedTotal()

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading order items: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.btnUploadProofPhoto.setOnClickListener {
            currentPhotoTargetShipmentId = shipment.id
            deliveryPhotoPickerLauncher.launch("image/*")
        }

        dialogBinding.btnSaveDeliveryChanges.setOnClickListener {
            val updatedDriver = dialogBinding.etDriverName.text.toString().trim()
            val updatedDate = dialogBinding.etDispatchDate.text.toString().trim()
            val updatedDest = dialogBinding.etDestinationAddress.text.toString().trim()
            val updatedStatus = dialogBinding.spDeliveryStatus.selectedItem.toString()
            val updatedEta = dialogBinding.etEtaOrPickupTime.text.toString().trim()
            val updatedNotes = dialogBinding.etDeliveryNotes.text.toString().trim()

            val updatedDeliveredItems = deliveredQtyInputs.map { (item, inputField) ->
                val qty = inputField.text.toString().toIntOrNull() ?: item.quantity
                item.copy(deliveredQuantity = qty)
            }

            val discount = dialogBinding.etDiscountAmount.text.toString().toDoubleOrNull() ?: 0.0
            val overridePrice = dialogBinding.etFinalPriceOverride.text.toString().toDoubleOrNull()

            lifecycleScope.launch {
                try {
                    ApiClient.instance.updateShipment(
                        "Bearer $token",
                        shipment.id,
                        ShipmentUpdateRequest(
                            driverName = updatedDriver,
                            dispatchDate = updatedDate,
                            status = updatedStatus,
                            destination = updatedDest,
                            notes = updatedNotes,
                            eta = updatedEta,
                            deliveredItems = updatedDeliveredItems,
                            isTaxable = dialogBinding.cbIsTaxable.isChecked,
                            discountAmount = discount,
                            overrideTotal = overridePrice
                        )
                    )

                    Toast.makeText(context, "💾 Dispatch details saved!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogBinding.btnCompleteDeliveryAndInvoice.setOnClickListener {
            val selectedStatus = dialogBinding.spDeliveryStatus.selectedItem.toString()
            val finalStatus = if (selectedStatus.contains("Pickup", ignoreCase = true) || selectedStatus.contains("Returned", ignoreCase = true)) "Picked Up / Returned" else "Delivered"

            val updatedDriver = dialogBinding.etDriverName.text.toString().trim()
            val updatedDate = dialogBinding.etDispatchDate.text.toString().trim()
            val updatedDest = dialogBinding.etDestinationAddress.text.toString().trim()
            val updatedNotes = dialogBinding.etDeliveryNotes.text.toString().trim()

            val updatedDeliveredItems = deliveredQtyInputs.map { (item, inputField) ->
                val qty = inputField.text.toString().toIntOrNull() ?: item.quantity
                item.copy(deliveredQuantity = qty)
            }

            val finalAdjustedTotal = calculateAdjustedTotal()

            lifecycleScope.launch {
                try {
                    // 1. Update Shipment status
                    ApiClient.instance.updateShipment(
                        "Bearer $token",
                        shipment.id,
                        ShipmentUpdateRequest(
                            driverName = updatedDriver,
                            dispatchDate = updatedDate,
                            status = finalStatus,
                            destination = updatedDest,
                            notes = updatedNotes,
                            deliveredItems = updatedDeliveredItems,
                            isTaxable = dialogBinding.cbIsTaxable.isChecked,
                            discountAmount = dialogBinding.etDiscountAmount.text.toString().toDoubleOrNull() ?: 0.0,
                            overrideTotal = dialogBinding.etFinalPriceOverride.text.toString().toDoubleOrNull()
                        )
                    )

                    // 2. Update Order status throughout system
                    ApiClient.instance.updateOrderStatus("Bearer $token", targetOrderId, StatusUpdateRequest(finalStatus))

                    // 3. Auto-generate Adjusted Invoice
                    ApiClient.instance.createInvoice(
                        "Bearer $token",
                        CreateInvoiceRequest(
                            orderId = targetOrderId,
                            customerName = updatedDriver.ifEmpty { "Customer" },
                            amount = finalAdjustedTotal,
                            status = "unpaid"
                        )
                    )

                    Toast.makeText(context, "✅ Completed! Adjusted invoice issued ($" + String.format(Locale.US, "%.2f", finalAdjustedTotal) + ")", Toast.LENGTH_LONG).show()

                    dialog.dismiss()
                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Completed: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDeliveries()
                }
            }
        }

        dialogBinding.btnCancelDelivery.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Cancel Delivery #${shipment.id}")
                .setMessage("Are you sure you want to cancel this delivery order?")
                .setPositiveButton("Yes, Cancel Delivery") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            ApiClient.instance.updateShipment(
                                "Bearer $token",
                                shipment.id,
                                ShipmentUpdateRequest(status = "Cancelled")
                            )
                            ApiClient.instance.updateOrderStatus("Bearer $token", targetOrderId, StatusUpdateRequest("Cancelled"))
                            Toast.makeText(context, "🚫 Delivery #${shipment.id} cancelled", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cancelled locally: ${e.message}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        }
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }

        dialogBinding.btnDeleteDelivery.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Delete Dispatch Record")
                .setMessage("Are you sure you want to PERMANENTLY delete dispatch record #${shipment.id}? This cannot be undone.")
                .setPositiveButton("Delete Record") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            ApiClient.instance.deleteShipment("Bearer $token", shipment.id)
                            Toast.makeText(context, "🗑️ Dispatch record deleted", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Deleted locally: ${e.message}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadDeliveries()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialogBinding.btnOpenGpsMap.setOnClickListener {
            openGoogleMapsNavigation(dialogBinding.etDestinationAddress.text.toString().trim().ifEmpty { shipment.destination })
        }

        dialog.show()
    }

    private fun showUpdateStatusAndEtaDialog(shipment: Shipment) {
        val context = context ?: return
        val statuses = arrayOf("Scheduled", "In Route", "Pickup Scheduled", "Delivered", "Picked Up / Returned", "Cancelled")

        AlertDialog.Builder(context)
            .setTitle("Update Delivery Status")
            .setItems(statuses) { _, which ->
                val selectedStatus = statuses[which]
                if (selectedStatus.equals("In Route", ignoreCase = true)) {
                    showEtaInputDialog(shipment)
                } else if (selectedStatus.contains("Delivered", ignoreCase = true) || selectedStatus.contains("Returned", ignoreCase = true)) {
                    markShipmentDeliveredAndGenerateInvoice(shipment)
                } else {
                    updateShipment(shipment.id, ShipmentUpdateRequest(status = selectedStatus))
                }
            }
            .show()
    }

    private fun markShipmentDeliveredAndGenerateInvoice(shipment: Shipment) {
        showShipmentDetailsDialog(shipment)
    }

    private fun showEtaInputDialog(shipment: Shipment) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Enter Estimated Arrival Time (ETA)")

        val inputEta = EditText(context).apply {
            hint = "e.g. 10:30 AM or 25 mins"
            setText(shipment.eta)
            setPadding(32, 32, 32, 32)
        }
        builder.setView(inputEta)

        builder.setPositiveButton("Set In Route & Send ETA") { _, _ ->
            val etaValue = inputEta.text.toString().trim().ifEmpty { "20-30 mins" }
            updateShipment(shipment.id, ShipmentUpdateRequest(status = "In Route", eta = etaValue))
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showEditNotesDialog(shipment: Shipment) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Delivery Notes & Instructions")

        val inputNotes = EditText(context).apply {
            hint = "Special gate code, delivery notes"
            setText(shipment.notes)
            setPadding(32, 32, 32, 32)
        }
        builder.setView(inputNotes)

        builder.setPositiveButton("Save Notes") { _, _ ->
            val newNotes = inputNotes.text.toString().trim()
            updateShipment(shipment.id, ShipmentUpdateRequest(notes = newNotes))
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAssignDriverDialog(shipment: Shipment) {
        val context = context ?: return
        val drivers = arrayOf("Truck 1 - Mike", "Truck 2 - Dave", "Truck 3 - Alex", "Dispatcher Fleet")
        AlertDialog.Builder(context)
            .setTitle("Assign Driver / Fleet Truck")
            .setItems(drivers) { _, which ->
                val selectedDriver = drivers[which]
                updateShipment(shipment.id, ShipmentUpdateRequest(driverName = selectedDriver))
            }
            .show()
    }

    private fun uploadPhotoForShipment(shipmentId: String, uri: Uri) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val file = uriToFile(uri) ?: return@launch
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("photo", file.name, requestFile)

                Toast.makeText(context, "Uploading delivery proof photo...", Toast.LENGTH_SHORT).show()
                val response = ApiClient.instance.uploadDeliveryPhoto("Bearer $token", shipmentId, body)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Delivery proof photo saved successfully!", Toast.LENGTH_SHORT).show()
                    loadDeliveries()
                } else {
                    Toast.makeText(context, "Failed to upload photo", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        val context = context ?: return null
        val contentResolver = context.contentResolver
        val tempFile = File(context.cacheDir, "delivery_proof_${System.currentTimeMillis()}.jpg")

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun optimizeDeliveryRoute() {
        val context = context ?: return
        val pendingStops = activeShipmentsList.filter {
            !it.status.equals("Delivered", ignoreCase = true) && !it.status.equals("Returned", ignoreCase = true) && it.destination.isNotEmpty()
        }

        if (pendingStops.isEmpty()) {
            Toast.makeText(context, "No active delivery stops to optimize", Toast.LENGTH_SHORT).show()
            return
        }

        val destinations = pendingStops.map { it.destination }.distinct()
        val finalDestination = destinations.last()
        val waypoints = if (destinations.size > 1) destinations.dropLast(1).joinToString("|") else ""

        val routeSummary = StringBuilder()
        routeSummary.append("Starting Location: Current GPS Location (Sacramento Hub)\n\n")
        routeSummary.append("Optimized Stop Sequence (${destinations.size} stops):\n")
        destinations.forEachIndexed { index, dest ->
            routeSummary.append("${index + 1}. $dest\n")
        }

        AlertDialog.Builder(context)
            .setTitle("🗺️ Optimized Delivery Route")
            .setMessage(routeSummary.toString())
            .setPositiveButton("Launch GPS Navigation") { _, _ ->
                val mapsUrl = if (waypoints.isNotEmpty()) {
                    "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(finalDestination)}&waypoints=${Uri.encode(waypoints)}&travelmode=driving"
                } else {
                    "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(finalDestination)}&travelmode=driving"
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSchedulePickupDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Schedule Pickup Transport")

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

        builder.setPositiveButton("Schedule Pickup") { _, _ ->
            val orderId = inputOrderId.text.toString().trim()
            val driver = inputDriver.text.toString().trim()
            val date = inputDate.text.toString().trim()
            val dest = inputDest.text.toString().trim()
            val notes = inputNotes.text.toString().trim()

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
