package com.example.secureafenceadministrator.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentDashboardBinding
import com.example.secureafenceadministrator.ui.calculator.CalculatorFragment
import com.example.secureafenceadministrator.ui.products.ProductsFragment
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadOverview()

        binding.btnRefresh.setOnClickListener {
            loadOverview()
        }

        binding.btnCalculator.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CalculatorFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnManageProducts.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProductsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadOverview() {
        val ctx = context ?: return
        val token = SessionManager.getToken(ctx)
        if (token.isNullOrEmpty()) {
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getAdminOverview("Bearer $token")
                val currentBinding = _binding ?: return@launch
                if (response.isSuccessful && response.body() != null) {
                    val metrics = response.body()?.metrics
                    if (metrics != null) {
                        currentBinding.tvSalesRevenue.text = "$${metrics.totalSalesRevenue}"
                        currentBinding.tvMonthlyRental.text = "$${metrics.monthlyRentalRevenue}"
                        currentBinding.tvActiveRentals.text = "${metrics.activeRentalsCount}"
                        currentBinding.tvStock.text = "${metrics.panelsInWarehouse}"
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    val validContext = context ?: return@launch
                    Toast.makeText(validContext, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
                } else {
                    val validContext = context ?: return@launch
                    Log.e("API_DEBUG", "Error: ${response.code()}")
                    Toast.makeText(validContext, "Failed to load overview: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val validContext = context ?: return@launch
                Toast.makeText(validContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
