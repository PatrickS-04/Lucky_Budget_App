package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import com.google.android.material.button.MaterialButton

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "Edit Profile" button → CreateAccActivity (profile editing screen)
        findViewById<MaterialButton>(R.id.editProfileBtn)?.setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        // "Invite Collaborator" button → toast placeholder (no dedicated screen yet)
        findViewById<MaterialButton>(R.id.inviteCollaboratorBtn)?.setOnClickListener {
            Toast.makeText(this, "Invite Collaborator coming soon", Toast.LENGTH_SHORT).show()
        }

        // Settings is the current screen
        setupBottomNav(R.id.navSettings)
    }
}