package com.example.sharedkhatm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val PREFS = "AppGlobalPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        auth = FirebaseAuth.getInstance()

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnSkip = findViewById<TextView>(R.id.btnSkip)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnSkip.setOnClickListener {
            btnSkip.isEnabled = false
            Toast.makeText(this, "Misafir girişi yapılıyor...", Toast.LENGTH_SHORT).show()

            // ✅ Zaten kullanıcı varsa tekrar sign-in yapma
            val current = auth.currentUser
            if (current != null) {
                // misafir ise flag’i yaz
                if (current.isAnonymous) {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("isGuest", true).apply()
                }
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@setOnClickListener
            }

            auth.signInAnonymously()
                .addOnSuccessListener {
                    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("isGuest", true).apply()

                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    btnSkip.isEnabled = true
                    Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
