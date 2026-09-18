package com.example.secureafenceadministrator.ui.deliveries

import android.app.AlertDialog
import android.content.Context
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
import com.example.secureafenceadministrator.data.model.Shipment
import com.example.secureafenceadministrator.data.model.ShipmentUpdateRequest
import com.example.secureafenceadministrator.data.model.StatusUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.DialogDeliveryDetailsBinding
import com.example.secureafenceadministrator.databinding.FragmentDeliveriesBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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

    private fun getMergedOrders(remoteList: List<Order>): List<Order> {
        val ctx = context ?: return remoteList
        val prefs = ctx.getSharedPreferences("local_order_overrides", Context.MODE_PRIVATE)
        val jsonMapString = prefs.getString("overrides_json", "{}") ?: "{}"
        return try {
            val type = object : TypeToken<MutableMap<String, Order>>() {}.type
            val localMap: MutableMap<String, Order> = Gson().fromJson(jsonMapString, type) ?: mutableMapOf()
            if (localMap.isEmpty()) return remoteList

            val resultList = remoteList.toMutableList()
            for (i in resultList.indices) {
                val remote = resultList[i]
                val localOverride = localMap[remote.id]
                if (localOverride != null) {
                    resultList[i] = localOverride
                }
            }
            for ((_, localOrd) in localMap) {
                if (resultList.none { it.id == localOrd.id }) {
                    resultList.add(localOrd)
                }
            }
            resultList
        } catch (e: Exception) {
            remoteList
        }
    }

    private fun saveLocalShipmentOverride(shipment: Shipment) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("local_shipment_overrides", Context.MODE_PRIVATE)
        val jsonMapString = prefs.getString("overrides_json", "{}") ?: "{}"
        try {
            val type = object : TypeToken<MutableMap<String, Shipment>>() {}.type
            val map: MutableMap<String, Shipment> = Gson().fromJson(jsonMapString, type) ?: mutableMapOf()
            if (shipment.id.isNotEmpty()) {
                map[shipment.id] = shipment
            }
            if (shipment.orderId.isNotEmpty()) {
                map[shipment.orderId] = shipment
            }
            prefs.edit().putString("overrides_json", Gson().toJson(map)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMergedShipments(remoteList: List<Shipment>): List<Shipment> {
        val ctx = context ?: return remoteList
        val prefs = ctx.getSharedPreferences("local_shipment_overrides", Context.MODE_PRIVATE)
        val jsonMapString = prefs.getString("overrides_json", "{}") ?: "{}"
        return try {
            val type = object : TypeToken<MutableMap<String, Shipment>>() {}.type
            val localMap: MutableMap<String, Shipment> = Gson().fromJson(jsonMapString, type) ?: mutableMapOf()
            if (localMap.isEmpty()) return remoteList

            val resultList = remoteList.toMutableList()
            for (i in resultList.indices) {
                val remote = resultList[i]
                val localOverride = localMap[remote.id] ?: localMap[remote.orderId]
                if (localOverride != null) {
                    resultList[i] = localOverride
                }
            }
            for ((_, localShp) in localMap) {
                if (resultList.none { it.id == localShp.id || (localShp.orderId.isNotEmpty() && it.orderId == localShp.orderId) }) {
                    resultList.add(localShp)
                }
            }
            resultList
        } catch (e: Exception) {
            remoteList
        }
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
                val shipmentsResp = ApiClient.instance.getShipments("Bearer $token")
                val ordersResp = ApiClient.instance.getSalesOrders("Bearer $token")

                val rawShipments = if (shipmentsResp.isSuccessful && shipmentsResp.body() != null) shipmentsResp.body()!! else emptyList()
                val rawOrders = if (ordersResp.isSuccessful && ordersResp.body() != null) ordersResp.body()!! else emptyList()

                val mergedOrders = getMergedOrders(rawOrders)
                val mergedShipments = getMergedShipments(rawShipments)

                val mergedShipmentsMap = mutableMapOf<String, Shipment>()

                // 1. Convert all merged orders into baseline Shipment dispatches
                for (order in mergedOrders) {
                    val orderIdStr = order.id.orEmpty()
                    val dispatchType = if (order.orderType.orEmpty().equals("rental", ignoreCase = true)) "Rental Delivery" else "Sales Delivery"
                    val isTaxable = order.tax > 0 || order.isTaxable
                    val baseShipment = Shipment(
                        id = orderIdStr,
                        orderId = orderIdStr,
                        type = dispatchType,
                        driverName = "Unassigned Dispatcher",
                        dispatchDate = order.deliveryDate.orEmpty().ifEmpty { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                        status = order.status.orEmpty().ifEmpty { "Scheduled" },
                        destination = order.deliveryAddress.orEmpty().ifEmpty { "Sacramento Warehouse" },
                        notes = "Jobsite Contact: ${order.customerName.orEmpty()}",
                        deliveredItems = order.items,
                        isTaxable = isTaxable,
                        discountAmount = order.discountAmount,
                        overrideTotal = order.overrideTotal
                    )
                    if (orderIdStr.isNotEmpty()) {
                        mergedShipmentsMap[orderIdStr] = baseShipment
                    }
                }

                // 2. Merge driver name, ETA, notes, and photos from shipments table
                for (shp in mergedShipments) {
                    val key = (shp.orderId ?: "").ifEmpty { shp.id ?: "" }
                    if (key.isNotEmpty()) {
                        val existing = mergedShipmentsMap[key]
                        if (existing != null) {
                            mergedShipmentsMap[key] = existing.copy(
                                driverName = (shp.driverName ?: "").ifEmpty { existing.driverName },
                                dispatchDate = (shp.dispatchDate ?: "").ifEmpty { existing.dispatchDate },
                                status = if (!shp.status.isNullOrEmpty()) shp.status else existing.status,
                                destination = (shp.destination ?: "").ifEmpty { existing.destination },
                                notes = (shp.notes ?: "").ifEmpty { existing.notes },
                                eta = shp.eta.orEmpty(),
                                deliveryPhotos = shp.deliveryPhotos ?: emptyList(),
                                deliveredItems = if (!shp.deliveredItems.isNullOrEmpty()) shp.deliveredItems else existing.deliveredItems
                            )
                        } else {
                            mergedShipmentsMap[shp.id.orEmpty().ifEmpty { "SHP-${System.currentTimeMillis()}" }] = shp
                        }
                    }
                }

                val allMerged = mergedShipmentsMap.values.toList()

                activeShipmentsList = allMerged.filter {
                    val status = (it.status ?: "").trim()
                    !status.equals("Delivered", ignoreCase = true) &&
                    !status.equals("Picked Up / Returned", ignoreCase = true) &&
                    !status.equals("Completed", ignoreCase = true) &&
                    !status.equals("Cancelled", ignoreCase = true)
                }.toMutableList()

                completedShipmentsList = allMerged.filter {
                    val status = (it.status ?: "").trim()
                    status.equals("Delivered", ignoreCase = true) ||
                    status.equals("Picked Up / Returned", ignoreCase = true) ||
                    status.equals("Completed", ignoreCase = true)
                }.toMutableList()

                binding.tvActiveDeliveriesHeader.text = "🚚 Active Dispatches & Deliveries (${activeShipmentsList.size})"
                binding.tvCompletedDeliveriesHeader.text = "✅ Completed Dispatches (${completedShipmentsList.size})"

                setupActiveAdapter()
                setupCompletedAdapter()

            } catch (e: Exception) {
                val errMsg = e.message.orEmpty().ifEmpty { "Unable to load dispatches" }
                Toast.makeText(context, "Dispatches: $errMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupActiveAdapter() {
        activeAdapter = GenericAdapter(
            activeShipmentsList,
            titleProvider = {
                val typeStr = (it.type ?: "Delivery").ifEmpty { "Delivery" }
                "🚚 Priority #${activeShipmentsList.indexOf(it) + 1} - ${typeStr.uppercase(Locale.US)}"
            },
            subtitleProvider = {
                val etaStr = if (!it.eta.isNullOrEmpty()) " | ETA/Time: ${it.eta}" else ""
                val idStr = (it.id ?: "SHP-101").ifEmpty { "SHP-101" }
                val refOrderId = (it.orderId ?: "").ifEmpty { idStr }
                val destStr = (it.destination ?: "Sacramento Warehouse").ifEmpty { "Sacramento Warehouse" }
                val driverStr = (it.driverName ?: "Unassigned Dispatcher").ifEmpty { "Unassigned Dispatcher" }
                "Order: #$refOrderId\nDestination: $destStr\nDriver: $driverStr$etaStr"
            },
            statusProvider = {
                val statusStr = (it.status ?: "Scheduled").ifEmpty { "Scheduled" }
                "Status: ${statusStr.uppercase(Locale.US)}"
            },
            rightImageResIdProvider = { R.drawable.logo },
            onItemClick = { showShipmentDetailsDialog(it) }
        )
        binding.recyclerViewActiveDeliveries.adapter = activeAdapter
    }

    private fun setupCompletedAdapter() {
        completedAdapter = GenericAdapter(
            completedShipmentsList,
            titleProvider = {
                val typeStr = (it.type ?: "Delivery").ifEmpty { "Delivery" }
                "✅ COMPLETED - ${typeStr.uppercase(Locale.US)}"
            },
            subtitleProvider = {
                val idStr = (it.id ?: "SHP-101").ifEmpty { "SHP-101" }
                val refOrderId = (it.orderId ?: "").ifEmpty { idStr }
                val destStr = (it.destination ?: "Sacramento Warehouse").ifEmpty { "Sacramento Warehouse" }
                val driverStr = (it.driverName ?: "Unassigned Dispatcher").ifEmpty { "Unassigned Dispatcher" }
                "Order: #$refOrderId\nDestination: $destStr\nDriver: $driverStr"
            },
            statusProvider = {
                val statusStr = (it.status ?: "Delivered").ifEmpty { "Delivered" }
                "Status: ${statusStr.uppercase(Locale.US)} [DELIVERED]"
            },
            rightImageResIdProvider = { R.drawable.logo },
            onItemClick = { showShipmentDetailsDialog(it) }
        )
        binding.recyclerViewCompletedDeliveries.adapter = completedAdapter
    }

    private fun showShipmentDetailsDialog(shipment: Shipment) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        val dialogBinding = DialogDeliveryDetailsBinding.inflate(LayoutInflater.from(context))

        val shipId = (shipment.id ?: "SHP-101").ifEmpty { "SHP-101" }
        val targetOrderId = (shipment.orderId ?: "").ifEmpty { shipId }
        val dispatchType = (shipment.type ?: "Delivery").ifEmpty { "Delivery" }

        dialogBinding.tvDeliveryDialogTitle.text = "🚚 Dispatch Details #$shipId"
        dialogBinding.tvDeliveryOrderIdAndType.text = "Ref Order #$targetOrderId | Dispatch Type: ${dispatchType.uppercase(Locale.US)}"

        dialogBinding.etDriverName.setText(shipment.driverName.orEmpty())
        dialogBinding.etDispatchDate.setText(shipment.dispatchDate.orEmpty())
        dialogBinding.etDestinationAddress.setText(shipment.destination.orEmpty())

        val statuses = arrayOf("Scheduled", "In Route", "Pickup Scheduled", "Delivered", "Picked Up / Returned", "Cancelled")
        dialogBinding.spDeliveryStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, statuses)
        val currentStatus = shipment.status.orEmpty()
        val statusIndex = statuses.indexOfFirst { it.equals(currentStatus, ignoreCase = true) }
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

        dialogBinding.etEtaOrPickupTime.setText(shipment.eta.orEmpty())
        dialogBinding.etDeliveryNotes.setText(shipment.notes.orEmpty())

        dialogBinding.btnUploadProofPhoto.text = "📸 Snap / Upload Proof Photo (${shipment.deliveryPhotos?.size ?: 0} Attached)"

        val deliveredQtyInputs = mutableMapOf<OrderItem, EditText>()
        var currentItemsList = mutableListOf<OrderItem>()

        // Fetch Customer & Order details safely
        lifecycleScope.launch {
            try {
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                val custResponse = ApiClient.instance.getCustomers("Bearer $token")

                val customersList = if (custResponse.isSuccessful && custResponse.body() != null) custResponse.body()!! else emptyList()

                val rawOrders = if (salesResponse.isSuccessful && salesResponse.body() != null) salesResponse.body()!! else emptyList()
                val mergedOrders = getMergedOrders(rawOrders)

                val matchingOrder = mergedOrders.find {
                    it.id.equals(targetOrderId, ignoreCase = true) || it.id.equals(shipId, ignoreCase = true)
                }

                if (matchingOrder != null) {
                    val matchingCustomer = customersList.find {
                        it.id == matchingOrder.customerId || it.email.equals(matchingOrder.customerEmail, true)
                    }

                    val companyStr = if (!matchingOrder.customerCompany.isNullOrEmpty()) " (${matchingOrder.customerCompany})" else ""
                    val custName = matchingOrder.customerName.orEmpty().ifEmpty { "Direct Client" }
                    val custPhone = matchingOrder.customerPhone.orEmpty().ifEmpty { "(279) 261-3890" }
                    val custEmail = matchingOrder.customerEmail.orEmpty().ifEmpty { "sales@secureafence.com" }

                    dialogBinding.tvCustomerNameAndCompany.text = "Client: $custName$companyStr"
                    dialogBinding.tvCustomerPhoneAndEmail.text = "Phone: $custPhone | Email: $custEmail"

                    dialogBinding.btnCallCustomer.setOnClickListener {
                        val phoneIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$custPhone"))
                        startActivity(phoneIntent)
                    }

                    if (!matchingOrder.items.isNullOrEmpty()) {
                        currentItemsList = matchingOrder.items.toMutableList()
                    }

                    val subtotal = matchingOrder.subtotal
                    val deliveryFee = matchingOrder.deliveryFee
                    val tax = matchingOrder.tax
                    val discount = matchingOrder.discountAmount
                    val total = matchingOrder.totalAmount
                    val taxLabel = if (matchingOrder.isTaxable) "Tax (8%): $" + String.format(Locale.US, "%.2f", tax) else "Tax: $0.00 (EXEMPT)"

                    dialogBinding.tvAdjustedTotalPreview.text = "💰 Order Financial Total: $" + String.format(Locale.US, "%.2f", total) +
                        "\n(Subtotal: $" + String.format(Locale.US, "%.2f", subtotal) +
                        " + Delivery Transport: $" + String.format(Locale.US, "%.2f", deliveryFee) +
                        " + $taxLabel - Discount: $" + String.format(Locale.US, "%.2f", discount) + ")"
                } else {
                    dialogBinding.tvAdjustedTotalPreview.text = "💰 Total Order Value: $" + String.format(Locale.US, "%.2f", shipment.overrideTotal ?: 0.0)
                }

                if (currentItemsList.isEmpty()) {
                    currentItemsList = (shipment.deliveredItems ?: emptyList()).toMutableList()
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
                    }

                    itemLayout.addView(tvLabel)
                    itemLayout.addView(inputDeliveredQty)
                    dialogBinding.llDeliveredItemsContainer.addView(itemLayout)
                    deliveredQtyInputs[item] = inputDeliveredQty
                }

            } catch (e: Exception) {
                // Fallback
            }
        }

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
            dialogBinding.btnSaveDeliveryChanges.isEnabled = enable
            dialogBinding.btnUploadProofPhoto.isEnabled = enable

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

            val updatedShipment = shipment.copy(
                driverName = updatedDriver,
                dispatchDate = updatedDate,
                destination = updatedDest,
                status = selectedStatus,
                eta = updatedEta,
                notes = updatedNotes,
                deliveredItems = updatedItems
            )
            saveLocalShipmentOverride(updatedShipment)

            val updateRequest = ShipmentUpdateRequest(
                driverName = updatedDriver,
                dispatchDate = updatedDate,
                destination = updatedDest,
                notes = updatedNotes,
                status = selectedStatus,
                eta = updatedEta,
                deliveredItems = updatedItems
            )

            lifecycleScope.launch {
                try {
                    val resp = ApiClient.instance.updateShipment("Bearer $token", shipId, updateRequest)
                    if (selectedStatus.equals("Delivered", true)) {
                        ApiClient.instance.updateOrderStatus("Bearer $token", targetOrderId, StatusUpdateRequest(status = "Delivered"))
                    }
                    if (resp.isSuccessful) {
                        Toast.makeText(context, "💾 Dispatch #$shipId updated!", Toast.LENGTH_SHORT).show()
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
                        "Bearer $token", shipId,
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
                    ApiClient.instance.updateShipment("Bearer $token", shipId, ShipmentUpdateRequest(status = "Cancelled"))
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
                .setMessage("Are you sure you want to PERMANENTLY delete dispatch #$shipId? This cannot be undone.")
                .setPositiveButton("Delete Record") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            ApiClient.instance.deleteShipment("Bearer $token", shipId)
                            Toast.makeText(context, "🗑️ Dispatch #$shipId deleted", Toast.LENGTH_SHORT).show()
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
