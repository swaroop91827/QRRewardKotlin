package com.swaroopsingh.qrreward.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.swaroopsingh.qrreward.R
import com.swaroopsingh.qrreward.ui.fragments.ScannerFragment
import com.swaroopsingh.qrreward.ui.fragments.HistoryFragment
import com.swaroopsingh.qrreward.ui.fragments.RewardsFragment

class MainActivity : AppCompatActivity() {

    lateinit var uid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        uid = intent.getStringExtra("uid") ?: ""

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Default fragment
        loadFragment(ScannerFragment.newInstance(uid))

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scanner -> loadFragment(ScannerFragment.newInstance(uid))
                R.id.nav_history -> loadFragment(HistoryFragment())
                R.id.nav_rewards -> loadFragment(RewardsFragment.newInstance(uid))
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
