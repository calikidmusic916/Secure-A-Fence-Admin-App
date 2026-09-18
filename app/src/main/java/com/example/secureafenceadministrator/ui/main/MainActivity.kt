package com.example.secureafenceadministrator.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.databinding.ActivityMainBinding
import com.example.secureafenceadministrator.ui.customers.CustomersFragment
import com.example.secureafenceadministrator.ui.dashboard.DashboardFragment
import com.example.secureafenceadministrator.ui.deliveries.DeliveriesFragment
import com.example.secureafenceadministrator.ui.invoices.InvoicesFragment
import com.example.secureafenceadministrator.ui.rentals.RentalsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        
        // Initial fragment
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_rentals -> {
                    loadFragment(RentalsFragment())
                    true
                }
                R.id.nav_deliveries -> {
                    loadFragment(DeliveriesFragment())
                    true
                }
                R.id.nav_invoices -> {
                    loadFragment(InvoicesFragment())
                    true
                }
                R.id.nav_customers -> {
                    loadFragment(CustomersFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
