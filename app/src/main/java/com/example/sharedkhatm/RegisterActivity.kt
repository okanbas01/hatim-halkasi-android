package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnRegisterAction = findViewById<Button>(R.id.btnRegisterAction)
        val txtGoToLogin = findViewById<TextView>(R.id.txtGoToLogin)

        val cbKvkk = findViewById<CheckBox>(R.id.cbKvkk)
        val txtKvkk = findViewById<TextView>(R.id.txtKvkk)

        btnBack.setOnClickListener { finish() }

        txtGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        txtKvkk.setOnClickListener { showKvkkDialog(cbKvkk) }

        btnRegisterAction.setOnClickListener {
            val name = etName.text.toString().trim()
            val rawUsername = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (!cbKvkk.isChecked) {
                Toast.makeText(this, "Lütfen Kullanıcı Sözleşmesini onaylayınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (name.isEmpty()) { etName.error = "İsim boş olamaz"; return@setOnClickListener }
            if (rawUsername.isEmpty()) { etUsername.error = "Kullanıcı adı boş olamaz"; return@setOnClickListener }
            if (rawUsername.length < 3) { etUsername.error = "En az 3 karakter olmalı"; return@setOnClickListener }

            val usernameRegex = Regex("^[a-zA-Z0-9_.]+$")
            if (!rawUsername.matches(usernameRegex)) {
                etUsername.error = "Türkçe karakter ve boşluk kullanmayınız."
                return@setOnClickListener
            }

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Geçerli bir e-posta giriniz"
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Şifre en az 6 karakter olmalı"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                etConfirmPassword.error = "Şifreler eşleşmiyor"
                return@setOnClickListener
            }

            val finalUsername = rawUsername.lowercase(Locale.ENGLISH)
            Toast.makeText(this, "Kayıt yapılıyor...", Toast.LENGTH_SHORT).show()

            val current = auth.currentUser
            val credential = EmailAuthProvider.getCredential(email, password)

            // ✅ Misafir ise: aynı UID ile yükselt
            if (current != null && current.isAnonymous) {
                current.linkWithCredential(credential)
                    .addOnSuccessListener {
                        val user = auth.currentUser
                        val userId = user?.uid ?: return@addOnSuccessListener

                        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                        user.updateProfile(profileUpdates)

                        val userData = hashMapOf(
                            "uid" to userId,
                            "name" to name,
                            "username" to finalUsername,
                            "email" to email,
                            "friends" to arrayListOf<String>(),
                            "sentRequests" to arrayListOf<String>(),
                            "receivedRequests" to arrayListOf<String>()
                        )
                        db.collection("users").document(userId).set(userData)

                        // Mail doğrulama gönder (signOut YOK!)
                        user.sendEmailVerification()

                        showVerificationDialog(email)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Kayıt Hatası: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // Normal kayıt
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            val userId = user?.uid

                            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                            user?.updateProfile(profileUpdates)

                            if (userId != null) {
                                val userData = hashMapOf(
                                    "uid" to userId,
                                    "name" to name,
                                    "username" to finalUsername,
                                    "email" to email,
                                    "friends" to arrayListOf<String>(),
                                    "sentRequests" to arrayListOf<String>(),
                                    "receivedRequests" to arrayListOf<String>()
                                )
                                db.collection("users").document(userId).set(userData)
                            }

                            user?.sendEmailVerification()
                            showVerificationDialog(email)

                        } else {
                            Toast.makeText(this, "Kayıt Hatası: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    private fun showVerificationDialog(email: String) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_premium_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialog.findViewById<TextView>(R.id.txtDialogMessage)
        val btnAction = dialog.findViewById<Button>(R.id.btnDialogAction)

        txtTitle.text = "Kayıt Başarılı! 🎉"
        txtMessage.text =
            "Doğrulama bağlantısı e-posta adresinize ($email) gönderildi.\n\n⚠️ Gelen Kutusu ve SPAM/GEREKSİZ klasörünü kontrol edin.\n\nDoğruladıktan sonra giriş yapabilirsiniz."
        btnAction.text = "Giriş Yap"

        btnAction.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        dialog.show()
    }

    private fun showKvkkDialog(checkBox: CheckBox) {
        val contractText = """
            KULLANICI SÖZLEŞMESİ VE GİZLİLİK POLİTİKASI
            
            1. TARAFLAR
            İşbu sözleşme, "Hatim Halkası" mobil uygulaması ile kullanıcı arasında akdedilmiştir.
            
            2. GİZLİLİK VE VERİ GÜVENLİĞİ
            Ad, Soyad ve E-posta adresiniz, hizmetin sağlanması amacıyla Firebase altyapısında güvenle saklanır. Üçüncü şahıslarla paylaşılmaz.
            
            3. KULLANICI SORUMLULUKLARI
            Kullanıcı; dua köşesi ve diğer alanlarda genel ahlaka, yasalara ve İslami değerlere aykırı, hakaret, küfür veya siyasi içerik paylaşamaz. Tespiti halinde hesap askıya alınabilir.
            
            4. SORUMLULUK REDDİ
            Uygulama "olduğu gibi" sunulmaktadır. Veri kayıplarından veya kesintilerden geliştirici sorumlu tutulamaz.
            
            Uygulamaya kayıt olarak bu şartları kabul etmiş sayılırsınız.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Kullanıcı Sözleşmesi")
            .setMessage(contractText)
            .setPositiveButton("Okudum, Anladım") { d, _ ->
                checkBox.isChecked = true
                d.dismiss()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }
}
