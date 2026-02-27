package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLoginAction = findViewById<Button>(R.id.btnLoginAction)
        val txtGoToRegister = findViewById<TextView>(R.id.txtGoToRegister)
        val txtForgotPassword = findViewById<TextView>(R.id.txtForgotPassword)

        btnBack.setOnClickListener { finish() }

        txtGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }

        txtForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // GİRİŞ YAP BUTONU
        btnLoginAction.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) { etEmail.error = "E-posta gerekli"; return@setOnClickListener }
            if (password.isEmpty()) { etPassword.error = "Şifre gerekli"; return@setOnClickListener }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // *** MAİL ONAY KONTROLÜ ***
                        if (user != null && user.isEmailVerified) {
                            Toast.makeText(this, "Giriş Başarılı", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, HomeActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        } else {
                            // Onaylanmamışsa çıkış yap ve uyar
                            auth.signOut()
                            showEmailNotVerifiedDialog(user)
                        }
                    } else {
                        Toast.makeText(this, "Giriş Başarısız: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    // --- UYARI: MAİL ONAYLANMAMIŞ (PREMIUM DIALOG - ALTIN SARISI) ---
    private fun showEmailNotVerifiedDialog(user: FirebaseUser?) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_premium_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialog.findViewById<TextView>(R.id.txtDialogMessage)
        val btnAction = dialog.findViewById<Button>(R.id.btnDialogAction)
        val imgIcon = dialog.findViewById<ImageView>(R.id.imgDialogIcon)

        // Uyarı olduğu için renkleri değiştirelim (Altın Sarısı)
        val goldColor = getColor(R.color.accent_gold)

        imgIcon.setImageResource(android.R.drawable.ic_dialog_alert)
        imgIcon.setColorFilter(goldColor)

        txtTitle.text = "E-posta Onayı Gerekli"
        txtTitle.setTextColor(goldColor)

        txtMessage.text = "Giriş yapabilmek için lütfen e-posta adresinize gelen doğrulama linkine tıklayın.\n\n⚠️ Lütfen SPAM/GEREKSİZ klasörünü kontrol etmeyi unutmayın."

        btnAction.text = "Tekrar Gönder"
        btnAction.setBackgroundColor(goldColor)

        btnAction.setOnClickListener {
            user?.sendEmailVerification()
            Toast.makeText(this, "Doğrulama maili tekrar gönderildi.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- ŞİFREMİ UNUTTUM: E-POSTA GİRME (STANDART INPUT) ---
    // --- YENİLENMİŞ ŞİFRE SIFIRLAMA PENCERESİ ---
    private fun showForgotPasswordDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_forgot_password)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false) // Dışına basınca kapanmasın

        // Viewleri Bul
        val etEmail = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etResetEmail)
        val btnSend = dialog.findViewById<Button>(R.id.btnResetSend)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnResetCancel)

        // Gönder Butonu
        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialog.dismiss() // Pencereyi kapat
                sendResetEmail(email) // Gönderme fonksiyonunu çağır
            } else {
                etEmail.error = "Geçerli bir e-posta giriniz"
            }
        }

        // Vazgeç Butonu
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- ŞİFRE SIFIRLAMA BAŞARILI (PREMIUM DIALOG - YEŞİL) ---
    private fun sendResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                val dialog = android.app.Dialog(this)
                dialog.setContentView(R.layout.dialog_premium_message)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogTitle)
                val txtMessage = dialog.findViewById<TextView>(R.id.txtDialogMessage)
                val btnAction = dialog.findViewById<Button>(R.id.btnDialogAction)

                txtTitle.text = "E-posta Gönderildi"
                txtMessage.text = "Şifre sıfırlama bağlantısı $email adresine yollandı.\n\n⚠️ Lütfen SPAM/GEREKSİZ klasörünü kontrol etmeyi unutmayın."
                btnAction.text = "Tamam"

                btnAction.setOnClickListener { dialog.dismiss() }
                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}