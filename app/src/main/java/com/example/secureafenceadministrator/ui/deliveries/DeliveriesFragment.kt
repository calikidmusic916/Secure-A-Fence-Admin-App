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
