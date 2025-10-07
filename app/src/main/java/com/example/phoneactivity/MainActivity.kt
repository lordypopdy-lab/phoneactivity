package com.example.phoneactivity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAccessibility: Button = findViewById(R.id.btnAccessibility)
        val btnNotification: ImageButton = findViewById(R.id.btnNotification)
        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        val btnTrade: ImageButton = findViewById(R.id.btnTrade)
        val btnDeposit: ImageButton = findViewById(R.id.btnDeposit)
        val btnWithdraw: ImageButton = findViewById(R.id.btnWithdraw)
        val tvBalance: TextView = findViewById(R.id.tvBalance)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnNotification.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnSettings.setOnClickListener {
            Snackbar.make(it, "Settings coming soon...", Snackbar.LENGTH_SHORT).show()
        }


        btnTrade.setOnClickListener {
            Snackbar.make(it, "Starting new trade...", Snackbar.LENGTH_SHORT).show()
        }

        btnDeposit.setOnClickListener {
            Snackbar.make(it, "Deposit feature coming soon...", Snackbar.LENGTH_SHORT).show()
        }

        btnWithdraw.setOnClickListener {
            Snackbar.make(it, "Withdraw feature coming soon...", Snackbar.LENGTH_SHORT).show()
        }
    }
}
