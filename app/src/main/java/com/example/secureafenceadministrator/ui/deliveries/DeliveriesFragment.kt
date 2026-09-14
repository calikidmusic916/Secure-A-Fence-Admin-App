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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.CreateInvoiceRequest
import com.example.secureafenceadministrator.data.model.OrderItem
import com.example.secureafenceadministrator.data.model.SchedulePickupRequest
import com.example.secureafenceadministrator.data.model.Shipment
import com.example.secureafenceadministrator.data.model.ShipmentUpdateRequest
import com.example.secureafenceadministrator.data.model.StatusUpdateRequest
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentDeliveriesBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

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
                        !it.status.equals("Returned", ignoreCase = true)
                    }.toMutableList()

                    completedShipmentsList = allShipments.filter {
                        it.status.equals("Delivered", ignoreCase = true) ||
                        it.status.equals("Completed", ignoreCase = true) ||
                        it.status.equals("Returned", ignoreCase = true)
                    }.toMutableList()

                    binding.tvActiveDeliveriesHeader.text = "🚚 Active Dispatches & Deliveries (${activeShipmentsList.size})"
                    binding.tvCompletedDeliveriesHeader.text = "✅ Completed & Delivered Orders (${completedShipmentsList.size})"

                    activeAdapter = GenericAdapter(
                        activeShipmentsList,
                        titleProvider = { "${it.type} #${it.id}" },
                        subtitleProvider = {
                            val etaText = if (!it.eta.isNullOrEmpty()) "\n⏱️ ETA: ${it.eta}" else ""
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
                        titleProvider = { "${it.type} #${it.id}" },
                        subtitleProvider = {
                            val photosText = if (!it.deliveryPhotos.isNullOrEmpty()) "\n📸 Proof Photos: ${it.deliveryPhotos.size} uploaded" else ""
                            "Driver: ${it.driverName.ifEmpty { "Completed" }}\nDest: ${it.destination}\nDelivered On: ${it.dispatchDate}$photosText"
                        },
                        statusProvider = { "Status: ${it.status.uppercase()} (Invoiced)" },
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
        val details = StringBuilder()
        details.append("🚚 Type: ${shipment.type}\n")
        details.append("Ref Order ID: ${shipment.orderId}\n")
        details.append("Driver / Truck: ${shipment.driverName.ifEmpty { "Unassigned Dispatcher" }}\n")
        details.append("Dispatch Date: ${shipment.dispatchDate}\n")
        details.append("Destination: ${shipment.destination}\n")
        details.append("Status: ${shipment.status.uppercase()}\n")
        if (!shipment.eta.isNullOrEmpty()) details.append("⏱️ ETA: ${shipment.eta}\n")
        if (!shipment.notes.isNullOrEmpty()) details.append("📝 Notes: ${shipment.notes}\n")
        if (!shipment.deliveryPhotos.isNullOrEmpty()) {
            details.append("📸 Delivery Proof Photos: ${shipment.deliveryPhotos.size} photo(s) attached\n")
        }

        val options = arrayOf(
            "⏱️ Mark Status & Set Customer ETA",
            "📸 Upload Delivery Proof Photo",
            "📝 Edit Notes & Access Instructions",
            "👤 Assign Driver / Fleet Truck",
            "📍 Navigate to Destination (Google Maps GPS)"
        )

        AlertDialog.Builder(context)
            .setTitle("Shipment #${shipment.id}")
            .setMessage(details.toString())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUpdateStatusAndEtaDialog(shipment)
                    1 -> {
                        currentPhotoTargetShipmentId = shipment.id
                        deliveryPhotoPickerLauncher.launch("image/*")
                    }
                    2 -> showEditNotesDialog(shipment)
                    3 -> showAssignDriverDialog(shipment)
                    4 -> openGoogleMapsNavigation(shipment.destination)
                }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showUpdateStatusAndEtaDialog(shipment: Shipment) {
        val context = context ?: return
        val statuses = arrayOf("Scheduled", "In Route", "Delivered", "Returned")

        AlertDialog.Builder(context)
            .setTitle("Update Delivery Status")
            .setItems(statuses) { _, which ->
                val selectedStatus = statuses[which]
                if (selectedStatus.equals("In Route", ignoreCase = true)) {
                    showEtaInputDialog(shipment)
                } else if (selectedStatus.equals("Delivered", ignoreCase = true)) {
                    markShipmentDeliveredAndGenerateInvoice(shipment)
                } else {
                    updateShipment(shipment.id, ShipmentUpdateRequest(status = selectedStatus))
                }
            }
            .show()
    }

    private fun markShipmentDeliveredAndGenerateInvoice(shipment: Shipment) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val targetOrderId = shipment.orderId.ifEmpty { shipment.id }
                var orderItems = listOf<OrderItem>()
                var customerName = shipment.driverName.ifEmpty { "Customer" }
                var orderTotalAmount = 250.0

                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                if (salesResponse.isSuccessful && salesResponse.body() != null) {
                    val matchingOrder = salesResponse.body()!!.find {
                        it.id.equals(targetOrderId, ignoreCase = true) || it.id.equals(shipment.id, ignoreCase = true)
                    }
                    if (matchingOrder != null) {
                        if (!matchingOrder.items.isNullOrEmpty()) orderItems = matchingOrder.items
                        if (matchingOrder.customerName.isNotEmpty()) customerName = matchingOrder.customerName
                        if (matchingOrder.totalAmount > 0) orderTotalAmount = matchingOrder.totalAmount
                    }
                }

                if (orderItems.isEmpty()) {
                    orderItems = listOf(
                        OrderItem(productId = "prod-1", name = "Refurbished Temporary Fence Panel (6' x 12')", unitPrice = 65.0, quantity = 50, deliveredQuantity = 50, total = 3250.0),
                        OrderItem(productId = "prod-2", name = "Flat Base / Stand", unitPrice = 10.0, quantity = 50, deliveredQuantity = 50, total = 500.0),
                        OrderItem(productId = "prod-3", name = "Safety Clamp / Panel Clip", unitPrice = 5.0, quantity = 50, deliveredQuantity = 50, total = 250.0)
                    )
                }

                showDeliveryConfirmationDialog(shipment, targetOrderId, customerName, orderTotalAmount, orderItems)
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading delivery details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeliveryConfirmationDialog(
        shipment: Shipment,
        targetOrderId: String,
        customerName: String,
        orderTotalAmount: Double,
        orderItems: List<OrderItem>
    ) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        val builder = AlertDialog.Builder(context)
        builder.setTitle("📸 Confirm Delivery & Quantities")

        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val tvHeader = TextView(context).apply {
            text = "🚚 Shipment #${shipment.id} (Order #$targetOrderId)\nClient: $customerName"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvHeader)

        // Proof Photo Button
        val photosCount = shipment.deliveryPhotos?.size ?: 0
        val btnPhoto = Button(context).apply {
            text = "📸 Snap / Upload Proof Photo ($photosCount attached)"
            setOnClickListener {
                currentPhotoTargetShipmentId = shipment.id
                deliveryPhotoPickerLauncher.launch("image/*")
            }
        }
        layout.addView(btnPhoto)

        // Notes Input
        val tvNotesHeader = TextView(context).apply {
            text = "\n📝 Driver Delivery Notes & Drop-Off Instructions:"
            setTypeface(null, Typeface.BOLD)
        }
        val inputNotes = EditText(context).apply {
            hint = "e.g. Delivered by North gate per site manager request"
            setText(shipment.notes)
        }
        layout.addView(tvNotesHeader)
        layout.addView(inputNotes)

        // Item Quantities Section
        val tvQtyHeader = TextView(context).apply {
            text = "\n📋 ENTER DELIVERED QUANTITIES (REQUIRED):"
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLUE)
        }
        layout.addView(tvQtyHeader)

        val qtyInputMap = mutableMapOf<OrderItem, EditText>()
        for (item in orderItems) {
            val tvItemLabel = TextView(context).apply {
                text = "• ${item.name}\n  [Ordered Quantity: ${item.quantity}]"
                textSize = 13f
                setPadding(0, 12, 0, 4)
            }
            val inputDeliveredQty = EditText(context).apply {
                hint = "Enter delivered quantity"
                setText((item.deliveredQuantity ?: item.quantity).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            layout.addView(tvItemLabel)
            layout.addView(inputDeliveredQty)
            qtyInputMap[item] = inputDeliveredQty
        }

        scrollView.addView(layout)
        builder.setView(scrollView)

        builder.setPositiveButton("✅ Confirm Delivery & Issue Invoice") { _, _ ->
            val updatedNotes = inputNotes.text.toString().trim()
            val deliveredItems = mutableListOf<OrderItem>()

            for ((item, inputField) in qtyInputMap) {
                val deliveredQty = inputField.text.toString().toIntOrNull() ?: item.quantity
                deliveredItems.add(item.copy(deliveredQuantity = deliveredQty))
            }

            lifecycleScope.launch {
                try {
                    // 1. Update Shipment status & items
                    ApiClient.instance.updateShipment(
                        "Bearer $token",
                        shipment.id,
                        ShipmentUpdateRequest(
                            status = "Delivered",
                            notes = updatedNotes,
                            deliveredItems = deliveredItems
                        )
                    )

                    // 2. Update Order throughout system
                    ApiClient.instance.updateOrderStatus("Bearer $token", targetOrderId, StatusUpdateRequest("Delivered"))

                    // 3. Auto-generate Invoice
                    val invoiceRequest = CreateInvoiceRequest(
                        orderId = targetOrderId,
                        customerName = customerName,
                        amount = orderTotalAmount,
                        status = "unpaid"
                    )
                    ApiClient.instance.createInvoice("Bearer $token", invoiceRequest)

                    Toast.makeText(context, "✅ Order #$targetOrderId marked Delivered. Invoice auto-generated!", Toast.LENGTH_LONG).show()

                    loadDeliveries()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving delivery confirmation: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
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
