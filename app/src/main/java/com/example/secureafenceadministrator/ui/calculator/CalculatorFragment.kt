package com.example.secureafenceadministrator.ui.calculator

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.secureafenceadministrator.data.model.Order
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentCalculatorBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class CalculatorFragment : Fragment() {

    private var _binding: FragmentCalculatorBinding? = null
    private val binding get() = _binding!!

    private var currentQuoteData: QuoteData? = null

    data class QuoteData(
        val linearFeet: Double,
        val months: Int,
        val privacy: Int,
        val gates: Int,
        val delivery: Double,
        val totalMonthly: Double,
        val setupTotal: Double,
        val finalTotal: Double
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deliveryOptions = arrayOf("Local (Zone 1) = $125", "Regional (Zone 2) = $200")
        binding.spDeliveryZone.adapter = ArrayAdapter(requireContext(), R.layout.simple_spinner_dropdown_item, deliveryOptions)

        binding.btnCalculate.setOnClickListener {
            runCalculator()
        }

        binding.btnCreateOrder.setOnClickListener {
            if (currentQuoteData != null) {
                showCreateOrderDialog()
            } else {
                Toast.makeText(context, "Please calculate a quote first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runCalculator() {
        val L = binding.etLinearFeet.text.toString().toDoubleOrNull() ?: 0.0
        val M = binding.etMonths.text.toString().toIntOrNull() ?: 1
        val P = if (binding.cbPrivacyScreen.isChecked) 1 else 0
        val G = binding.etGates.text.toString().toIntOrNull() ?: 0
        val D = if (binding.spDeliveryZone.selectedItemPosition == 0) 125.0 else 200.0

        if (L <= 0) {
            Toast.makeText(context, "Linear feet must be greater than 0", Toast.LENGTH_SHORT).show()
            return
        }

        // Recurring Monthly Variables
        val monthlyFence = L * 1.35
        val monthlyPrivacy = L * 0.33 * P
        val monthlyGate = G * 25.00
        val totalMonthly = monthlyFence + monthlyPrivacy + monthlyGate

        // One-Time Variables
        val installLabor = L * 0.17
        val removalLabor = L * 0.17
        val totalLabor = installLabor + removalLabor
        val setupTotal = totalLabor + D

        // Grand Total Formula
        val rawTotal = (totalMonthly * M) + setupTotal

        // Minimum Order Logic
        val finalTotal = if (rawTotal < 300) 300.0 else rawTotal

        // Equipment breakdown based on 6x12 panels (12 feet width)
        val panelsCount = ceil(L / 12.0).toInt()
        val standsCount = if (panelsCount > 0) panelsCount + 1 else 0
        val clipsCount = panelsCount

        currentQuoteData = QuoteData(L, M, P, G, D, totalMonthly, setupTotal, finalTotal)

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
        binding.tvEquipmentBreakdown.text = "$panelsCount Panels | $standsCount Stands | $clipsCount Clips"
        binding.tvSetupTotal.text = currencyFormatter.format(setupTotal)
        binding.tvMonthlyRate.text = "${currencyFormatter.format(totalMonthly)} / month"
        binding.tvGrandTotalLabel.text = "Total Estimated Cost (for $M months)"
        binding.tvGrandTotal.text = currencyFormatter.format(finalTotal)
    }

    private fun showCreateOrderDialog() {
        val quote = currentQuoteData ?: return
        val context = context ?: return

        AlertDialog.Builder(context)
            .setTitle("Generate Contract & Order")
            .setMessage("This will create a new Rental Order and Dispatch Shipment for this quote. Do you want to proceed?")
            .setPositiveButton("Create Order") { _, _ ->
                submitQuoteAsOrder(quote)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitQuoteAsOrder(quote: QuoteData) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        val payload = mapOf(
            "orderType" to "rental",
            "isCustomQuote" to true,
            "quoteData" to quote,
            "deliveryAddress" to "TBD - Awaiting Customer Input",
            "startDate" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )

        lifecycleScope.launch {
            try {
                // We use generic Map post since AdminApiService.createOrder expects an Order object.
                // We will add a dedicated method to ApiClient.
                val response = ApiClient.instance.submitCustomQuoteOrder("Bearer $token", payload)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Quote Order Created Successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to create order", Toast.LENGTH_SHORT).show()
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
