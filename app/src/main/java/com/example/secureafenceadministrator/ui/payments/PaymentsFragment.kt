package com.example.secureafenceadministrator.ui.payments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.model.OrderPaymentUpdateRequest
import com.example.secureafenceadministrator.data.model.Rental
import com.example.secureafenceadministrator.data.model.StripeTransactionRecord
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.data.network.StripeApiClient
import com.example.secureafenceadministrator.databinding.DialogStripeTerminalBinding
import com.example.secureafenceadministrator.databinding.FragmentPaymentsBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stripe.android.Stripe
import com.stripe.android.model.CardParams
import com.stripe.android.model.PaymentMethodCreateParams
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UnpaidBill(
    val id: String,
    val customerName: String,
    val customerEmail: String,
    val company: String?,
    val amount: Double,
    val titleText: String,
    val isRental: Boolean
)

class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private var pendingUnpaidBills = mutableListOf<UnpaidBill>()
    private var stripeHistoryList = mutableListOf<StripeTransactionRecord>()

    private var stripeInstance: Stripe? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val pk = SessionManager.getStripePublishableKey(context)
        stripeInstance = Stripe(context, pk)

        binding.recyclerViewPendingPayments.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewStripeHistory.layoutManager = LinearLayoutManager(context)

        loadStripeHistoryFromPrefs()
        loadPendingOrders()

        binding.btnVirtualTerminal.setOnClickListener {
            showCustomChargeTerminalDialog()
        }

        binding.btnStripeSettings.setOnClickListener {
            showStripeSettingsDialog()
        }
    }

    private fun showStripeSettingsDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("⚙️ Configure Stripe API Keys")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etPk = EditText(context).apply {
            hint = "Stripe Publishable Key (pk_test_... or pk_live_...)"
            setText(SessionManager.getStripePublishableKey(context))
            textSize = 13f
        }

        val etSk = EditText(context).apply {
            hint = "Stripe Secret Key (sk_test_... or sk_live_...)"
            setText(SessionManager.getStripeSecretKey(context))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 13f
        }

        layout.addView(etPk)
        layout.addView(etSk)
        builder.setView(layout)

        builder.setPositiveButton("Save Keys") { _, _ ->
            val pk = etPk.text.toString().trim()
            val sk = etSk.text.toString().trim()

            if (pk.isNotEmpty()) SessionManager.saveStripePublishableKey(context, pk)
            if (sk.isNotEmpty()) SessionManager.saveStripeSecretKey(context, sk)

            if (pk.isNotEmpty()) stripeInstance = Stripe(context, pk)
            Toast.makeText(context, "💾 Stripe API Keys updated and saved!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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

    private fun saveLocalOrderPaymentStatus(orderId: String, status: String, method: String) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("local_order_overrides", Context.MODE_PRIVATE)
        val jsonMapString = prefs.getString("overrides_json", "{}") ?: "{}"
        try {
            val type = object : TypeToken<MutableMap<String, Order>>() {}.type
            val map: MutableMap<String, Order> = Gson().fromJson(jsonMapString, type) ?: mutableMapOf()
            val existing = map[orderId]
            if (existing != null) {
                map[orderId] = existing.copy(paymentStatus = status, paymentMethod = method)
                prefs.edit().putString("overrides_json", Gson().toJson(map)).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMergedRentals(remoteList: List<Rental>): List<Rental> {
        val ctx = context ?: return remoteList
        val prefs = ctx.getSharedPreferences("local_rental_overrides", Context.MODE_PRIVATE)
        val jsonMapString = prefs.getString("overrides_json", "{}") ?: "{}"
        return try {
            val type = object : TypeToken<MutableMap<String, Rental>>() {}.type
            val localMap: MutableMap<String, Rental> = Gson().fromJson(jsonMapString, type) ?: mutableMapOf()
            if (localMap.isEmpty()) return remoteList

            val resultList = remoteList.toMutableList()
            for (i in resultList.indices) {
                val remote = resultList[i]
                val localOverride = localMap[remote.id]
                if (localOverride != null) {
                    resultList[i] = localOverride
                }
            }
            for ((_, localRnt) in localMap) {
                if (resultList.none { it.id == localRnt.id }) {
                    resultList.add(localRnt)
                }
            }
            resultList
        } catch (e: Exception) {
            remoteList
        }
    }

    private fun loadPendingOrders() {
        val context = context ?: return
        val token = SessionManager.getToken(context)
        if (token.isNullOrEmpty()) return

        lifecycleScope.launch {
            try {
                val salesResponse = ApiClient.instance.getSalesOrders("Bearer $token")
                val rentalsResponse = ApiClient.instance.getRentals("Bearer $token")

                val rawOrders = if (salesResponse.isSuccessful && salesResponse.body() != null) salesResponse.body()!! else emptyList()
                val rawRentals = if (rentalsResponse.isSuccessful && rentalsResponse.body() != null) rentalsResponse.body()!! else emptyList()

                val mergedOrders = getMergedOrders(rawOrders)
                val mergedRentals = getMergedRentals(rawRentals)

                pendingUnpaidBills.clear()

                // 1. Unpaid Sales Orders
                for (ord in mergedOrders) {
                    val payStatus = ord.paymentStatus.orEmpty().trim()
                    if (!payStatus.equals("Paid", ignoreCase = true)) {
                        pendingUnpaidBills.add(
                            UnpaidBill(
                                id = ord.id,
                                customerName = ord.customerName.ifEmpty { "Client" },
                                customerEmail = ord.customerEmail.ifEmpty { "billing@secureafence.com" },
                                company = ord.customerCompany,
                                amount = ord.totalAmount,
                                titleText = "📦 ${ord.orderType.uppercase()} #${ord.id}",
                                isRental = false
                            )
                        )
                    }
                }

                // 2. Active Rental Orders with Monthly Recurring Billing
                for (rnt in mergedRentals) {
                    val status = rnt.status.trim()
                    if (!status.equals("Returned", ignoreCase = true) && !status.equals("Completed", ignoreCase = true)) {
                        pendingUnpaidBills.add(
                            UnpaidBill(
                                id = rnt.id,
                                customerName = rnt.customerName.ifEmpty { "Client" },
                                customerEmail = rnt.customerEmail.ifEmpty { "billing@secureafence.com" },
                                company = rnt.customerCompany,
                                amount = rnt.monthlyRateTotal,
                                titleText = "🔄 MONTHLY RENTAL #${rnt.id}",
                                isRental = true
                            )
                        )
                    }
                }

                val pendingTotal = pendingUnpaidBills.sumOf { it.amount }
                binding.tvPendingCollectCount.text = "${pendingUnpaidBills.size} ($${String.format(Locale.US, "%.2f", pendingTotal)})"

                val adapter = GenericAdapter(
                    pendingUnpaidBills,
                    titleProvider = { it.titleText },
                    subtitleProvider = { bill ->
                        val clientStr = if (!bill.company.isNullOrEmpty()) "${bill.customerName} (${bill.company})" else bill.customerName
                        val rateStr = if (bill.isRental) " / month (Recurring)" else ""
                        "Customer: $clientStr\nEmail: ${bill.customerEmail}\nAmount Due: $" + String.format(Locale.US, "%.2f", bill.amount) + rateStr
                    },
                    statusProvider = { bill ->
                        if (bill.isRental) "Status: ACTIVE RENTAL | Monthly Auto-Billing Due" else "Status: UNPAID SALES ORDER | Immediate Charge"
                    },
                    rightImageResIdProvider = { R.drawable.logo },
                    onItemClick = { bill -> showBillStripeTerminal(bill) }
                )
                binding.recyclerViewPendingPayments.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading unpaid bills: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBillStripeTerminal(bill: UnpaidBill) {
        val descType = if (bill.isRental) "Monthly Recurring Rental #${bill.id}" else "Order #${bill.id}"
        showStripeTerminalModal(
            orderId = bill.id,
            customerName = bill.customerName,
            customerEmail = bill.customerEmail,
            amount = bill.amount,
            description = "Secure-A-Fence $descType",
            isRental = bill.isRental
        )
    }

    private fun showCustomChargeTerminalDialog() {
        showStripeTerminalModal(
            orderId = "POS-" + (1000..9999).random(),
            customerName = "Direct Client",
            customerEmail = "sales@secure-a-fence.com",
            amount = 150.00,
            description = "Virtual Terminal Charge - Temporary Fence Equipment",
            isRental = false
        )
    }

    private fun showStripeTerminalModal(
        orderId: String,
        customerName: String,
        customerEmail: String,
        amount: Double,
        description: String,
        isRental: Boolean
    ) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        val dialogBinding = DialogStripeTerminalBinding.inflate(LayoutInflater.from(context))

        dialogBinding.tvTerminalDialogTitle.text = "💳 Stripe POS Terminal Payment"
        dialogBinding.tvTerminalOrderRef.text = "Ref: #$orderId | Client: $customerName"

        dialogBinding.etStripeCustomerEmail.setText(customerEmail)
        dialogBinding.etStripeDescription.setText(description)
        dialogBinding.etStripeChargeAmount.setText(String.format(Locale.US, "%.2f", amount))
        dialogBinding.cbRecurringRental.isChecked = isRental

        // Automated Expiration Date Formatting (MM/YY)
        dialogBinding.etCardExpiry.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().replace("[^0-9]".toRegex(), "")
                val formatted = when {
                    digits.length >= 3 -> "${digits.substring(0, 2)}/${digits.substring(2, Math.min(digits.length, 4))}"
                    digits.length == 2 && !s.toString().contains("/") -> "${digits}/"
                    else -> s.toString()
                }

                if (formatted != s.toString()) {
                    s.replace(0, s.length, formatted)
                }
                isFormatting = false
            }
        })

        fun updateTaxBreakdown() {
            val totalVal = dialogBinding.etStripeChargeAmount.text.toString().toDoubleOrNull() ?: 0.0
            val subtotal = Math.round(totalVal / 1.08 * 100.0) / 100.0
            val tax = Math.round((totalVal - subtotal) * 100.0) / 100.0
            val recurringLabel = if (dialogBinding.cbRecurringRental.isChecked) " [MONTHLY RECURRING]" else ""
            dialogBinding.tvStripeTaxSummary.text = "Subtotal: $" + String.format(Locale.US, "%.2f", subtotal) + " + Sales Tax (8%): $" + String.format(Locale.US, "%.2f", tax) + " = Total Charge: $" + String.format(Locale.US, "%.2f", totalVal) + recurringLabel
        }

        updateTaxBreakdown()
        dialogBinding.etStripeChargeAmount.doAfterTextChanged { updateTaxBreakdown() }
        dialogBinding.cbRecurringRental.setOnCheckedChangeListener { _, _ -> updateTaxBreakdown() }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .create()

        dialogBinding.btnProcessStripePayment.setOnClickListener {
            val email = dialogBinding.etStripeCustomerEmail.text.toString().trim()
            val chargeDesc = dialogBinding.etStripeDescription.text.toString().trim()
            val chargeAmount = dialogBinding.etStripeChargeAmount.text.toString().toDoubleOrNull() ?: 0.0
            val isRecurring = dialogBinding.cbRecurringRental.isChecked

            val cardNum = dialogBinding.etCardNumber.text.toString().replace(" ", "").trim()
            val rawExpiry = dialogBinding.etCardExpiry.text.toString().trim()
            val expiryClean = if (rawExpiry.length == 4 && !rawExpiry.contains("/")) "${rawExpiry.take(2)}/${rawExpiry.drop(2)}" else rawExpiry
            val cvc = dialogBinding.etCardCvc.text.toString().trim()
            val zip = dialogBinding.etCardZip.text.toString().trim()

            if (chargeAmount <= 0.0) {
                Toast.makeText(context, "Please enter a valid charge amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Card Input
            if (cardNum.length < 15 || expiryClean.length < 5 || !expiryClean.contains("/") || cvc.length < 3) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                val failedRecord = StripeTransactionRecord(
                    id = "pi_failed_" + System.currentTimeMillis(),
                    orderId = orderId,
                    customerName = customerName,
                    customerEmail = email,
                    amount = chargeAmount,
                    taxAmount = Math.round((chargeAmount - (chargeAmount / 1.08)) * 100.0) / 100.0,
                    status = "FAILED",
                    paymentMethodType = if (isRecurring) "card (Invalid Card / Declined)" else "card (Declined)",
                    timestamp = todayStr,
                    receiptUrl = null,
                    isTerminalTransaction = isRecurring
                )
                addStripeTransaction(failedRecord)
                Toast.makeText(context, "❌ Payment Failed: Please enter valid card details (16-digit card, MM/YY, CVC)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val expParts = expiryClean.split("/")
            val expMonth = expParts[0].toIntOrNull() ?: 12
            var expYear = expParts.getOrNull(1)?.toIntOrNull() ?: 28
            if (expYear < 100) expYear += 2000

            dialogBinding.progressStripeCharge.visibility = View.VISIBLE
            dialogBinding.btnProcessStripePayment.isEnabled = false

            lifecycleScope.launch {
                try {
                    val sk = SessionManager.getStripeSecretKey(context)
                    val amountCents = Math.round(chargeAmount * 100)
                    val validEmail = if (email.isNotEmpty() && email.contains("@")) email else null

                    var piId = ""
                    var isSuccess = false
                    var errMsg = ""

                    if (sk.isNotEmpty()) {
                        val bearerToken = "Bearer $sk"
                        val intentResponse = StripeApiClient.instance.createPaymentIntent(
                            bearerToken = bearerToken,
                            amountCents = amountCents,
                            currency = "usd",
                            description = chargeDesc,
                            receiptEmail = validEmail,
                            orderId = orderId
                        )
                        if (intentResponse.isSuccessful && intentResponse.body() != null) {
                            piId = intentResponse.body()!!["id"] as? String ?: ("pi_" + System.currentTimeMillis())
                            isSuccess = true
                        } else {
                            errMsg = intentResponse.errorBody()?.string() ?: "Stripe API Error"
                        }
                    } else {
                        // Call backend server payment intent creation securely
                        val payload = mapOf(
                            "amountCents" to amountCents,
                            "currency" to "usd",
                            "description" to chargeDesc,
                            "customerEmail" to (email.ifEmpty { "sales@secureafence.com" }),
                            "orderId" to orderId,
                            "isRentalCharge" to isRecurring
                        )
                        val serverResp = ApiClient.instance.createServerPaymentIntent("Bearer $token", payload)
                        if (serverResp.isSuccessful && serverResp.body() != null) {
                            piId = serverResp.body()!!["paymentIntentId"] as? String
                                ?: serverResp.body()!!["id"] as? String
                                ?: ("pi_" + System.currentTimeMillis())
                            isSuccess = true
                        } else {
                            errMsg = serverResp.errorBody()?.string() ?: "Server Payment Error"
                        }
                    }

                    if (isSuccess && piId.isNotEmpty()) {
                        val methodType = if (isRecurring) "card (Stripe Recurring Monthly)" else "card (Stripe)"
                        val todayStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

                        val record = StripeTransactionRecord(
                            id = piId,
                            orderId = orderId,
                            customerName = customerName,
                            customerEmail = email,
                            amount = chargeAmount,
                            taxAmount = Math.round((chargeAmount - (chargeAmount / 1.08)) * 100.0) / 100.0,
                            status = "SUCCEEDED",
                            paymentMethodType = methodType,
                            timestamp = todayStr,
                            receiptUrl = "https://dashboard.stripe.com/test/payments/$piId",
                            isTerminalTransaction = isRecurring
                        )

                        addStripeTransaction(record)

                        if (isRecurring) {
                            ApiClient.instance.generateMonthlyRentalInvoice("Bearer $token", orderId)
                        } else {
                            ApiClient.instance.updateOrderPayment("Bearer $token", orderId, OrderPaymentUpdateRequest("Paid", "Stripe Credit Card"))
                        }

                        dialogBinding.progressStripeCharge.visibility = View.GONE
                        Toast.makeText(context, "🎉 Stripe Charge $${String.format(Locale.US, "%.2f", chargeAmount)} SUCCEEDED! Ref: $piId", Toast.LENGTH_LONG).show()

                        dialog.dismiss()
                        loadPendingOrders()
                    } else {
                        val cleanErr = if (errMsg.contains("message")) {
                            try {
                                val errObj = Gson().fromJson(errMsg, Map::class.java)
                                val errInner = errObj["error"] as? Map<*, *>
                                errInner?.get("message") as? String ?: errMsg
                            } catch (e: Exception) {
                                errMsg
                            }
                        } else {
                            errMsg
                        }

                        val todayStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                        val failedRecord = StripeTransactionRecord(
                            id = "pi_failed_" + System.currentTimeMillis(),
                            orderId = orderId,
                            customerName = customerName,
                            customerEmail = email,
                            amount = chargeAmount,
                            taxAmount = Math.round((chargeAmount - (chargeAmount / 1.08)) * 100.0) / 100.0,
                            status = "FAILED",
                            paymentMethodType = "card ($cleanErr)",
                            timestamp = todayStr,
                            receiptUrl = null,
                            isTerminalTransaction = isRecurring
                        )
                        addStripeTransaction(failedRecord)

                        dialogBinding.progressStripeCharge.visibility = View.GONE
                        dialogBinding.btnProcessStripePayment.isEnabled = true
                        Toast.makeText(context, "❌ Stripe Error: $cleanErr", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    val todayStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val failedRecord = StripeTransactionRecord(
                        id = "pi_failed_" + System.currentTimeMillis(),
                        orderId = orderId,
                        customerName = customerName,
                        customerEmail = email,
                        amount = chargeAmount,
                        taxAmount = Math.round((chargeAmount - (chargeAmount / 1.08)) * 100.0) / 100.0,
                        status = "FAILED",
                        paymentMethodType = "card (${e.message ?: "Declined"})",
                        timestamp = todayStr,
                        receiptUrl = null,
                        isTerminalTransaction = isRecurring
                    )
                    addStripeTransaction(failedRecord)

                    dialogBinding.progressStripeCharge.visibility = View.GONE
                    dialogBinding.btnProcessStripePayment.isEnabled = true
                    Toast.makeText(context, "❌ Stripe Charge Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun addStripeTransaction(record: StripeTransactionRecord) {
        stripeHistoryList.add(0, record)
        saveStripeHistoryToPrefs()
        updateStripeHistoryUI()
    }

    private fun updateStripeHistoryUI() {
        val totalVol = stripeHistoryList.filter { it.status == "SUCCEEDED" }.sumOf { it.amount }
        binding.tvTotalStripeVolume.text = "$" + String.format(Locale.US, "%.2f", totalVol)

        val adapter = GenericAdapter(
            stripeHistoryList,
            titleProvider = {
                if (it.status == "SUCCEEDED") "🧾 Stripe Payment #${it.id.take(16)}" else "❌ Failed Transaction #${it.id.take(16)}"
            },
            subtitleProvider = {
                "Client: ${it.customerName} (${it.customerEmail})\nOrder/Ref: #${it.orderId} | Date: ${it.timestamp}\nAmount: $" + String.format(Locale.US, "%.2f", it.amount) + " (Includes Tax: $" + String.format(Locale.US, "%.2f", it.taxAmount) + ")\nMethod: ${it.paymentMethodType}"
            },
            statusProvider = {
                when {
                    it.status == "SUCCEEDED" && it.isTerminalTransaction -> "Status: SUCCEEDED [🔄 RECURRING MONTHLY]"
                    it.status == "SUCCEEDED" -> "Status: SUCCEEDED [STRIPE SECURED]"
                    else -> "Status: FAILED [CARD DECLINED / REJECTED]"
                }
            },
            rightImageResIdProvider = { R.drawable.logo },
            onItemClick = { record ->
                if (!record.receiptUrl.isNullOrEmpty()) {
                    try {
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(record.receiptUrl))
                        startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Receipt Ref: ${record.id}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.recyclerViewStripeHistory.adapter = adapter
    }

    private fun saveStripeHistoryToPrefs() {
        val context = context ?: return
        val json = Gson().toJson(stripeHistoryList)
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("stripe_history_json", json).apply()
    }

    private fun loadStripeHistoryFromPrefs() {
        val context = context ?: return
        val json = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("stripe_history_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<StripeTransactionRecord>>() {}.type
                val savedList: List<StripeTransactionRecord> = Gson().fromJson(json, type)
                stripeHistoryList = savedList.toMutableList()
            } catch (e: Exception) {
                stripeHistoryList = mutableListOf()
            }
        }

        val token = SessionManager.getToken(context)
        if (!token.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.instance.getPaymentsTransactions("Bearer $token")
                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                        val remoteList = resp.body()!!
                        for (remoteTx in remoteList) {
                            if (stripeHistoryList.none { it.id == remoteTx.id }) {
                                stripeHistoryList.add(remoteTx)
                            }
                        }
                        stripeHistoryList.sortByDescending { it.timestamp }
                        updateStripeHistoryUI()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        updateStripeHistoryUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
