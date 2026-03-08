package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.lang.ref.WeakReference

/**
 * Lifecycle-safe Login: BadTokenException önlemi için tüm dialog/Toast
 * WeakReference + isFinishing/isDestroyed kontrolü ile gösterilir.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private fun isSafeForUi(): Boolean = !isFinishing && !isDestroyed

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
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        txtForgotPassword.setOnClickListener { showForgotPasswordDialog() }

        val activityRef = WeakReference(this)
        btnLoginAction.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isEmpty()) { etEmail.error = "E-posta gerekli"; return@setOnClickListener }
            if (password.isEmpty()) { etPassword.error = "Şifre gerekli"; return@setOnClickListener }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    val act = activityRef.get() ?: return@addOnCompleteListener
                    if (!act.isSafeForUi()) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            safeToast(act, "Giriş Başarılı")
                            startActivity(Intent(act, HomeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            act.finish()
                        } else {
                            auth.signOut()
                            showEmailNotVerifiedDialog(act, user)
                        }
                    } else {
                        safeToast(act, "Giriş Başarısız: ${task.exception?.message}")
                    }
                }
        }
    }

    private fun safeToast(activity: AppCompatActivity, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }

    private fun showEmailNotVerifiedDialog(activity: AppCompatActivity, user: FirebaseUser?) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("E-posta Onayı Gerekli")
            .setMessage("Giriş yapabilmek için lütfen e-posta adresinize gelen doğrulama linkine tıklayın.\n\n⚠️ Lütfen SPAM/GEREKSİZ klasörünü kontrol etmeyi unutmayın.")
            .setPositiveButton("Tekrar Gönder") { _, _ ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    user?.sendEmailVerification()
                    safeToast(activity, "Doğrulama maili tekrar gönderildi.")
                }
            }
            .setCancelable(true)
            .show()
    }

    private fun showForgotPasswordDialog() {
        if (!isSafeForUi()) return
        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = view.findViewById<TextInputEditText>(R.id.etResetEmail)
        val btnSend = view.findViewById<Button>(R.id.btnResetSend)
        val btnCancel = view.findViewById<TextView>(R.id.btnResetCancel)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val activityRef = WeakReference(this)
        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialog.dismiss()
                sendResetEmail(activityRef, email)
            } else {
                etEmail.error = "Geçerli bir e-posta giriniz"
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        if (isSafeForUi()) dialog.show()
    }

    /** Callback'te Activity WeakReference ile kullanılır; BadTokenException önlenir. */
    private fun sendResetEmail(activityRef: WeakReference<LoginActivity>, email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                val act = activityRef.get() ?: return@addOnSuccessListener
                if (!act.isSafeForUi()) return@addOnSuccessListener
                AlertDialog.Builder(act)
                    .setTitle("E-posta Gönderildi")
                    .setMessage("Şifre sıfırlama bağlantısı $email adresine yollandı.\n\n⚠️ Lütfen SPAM/GEREKSİZ klasörünü kontrol etmeyi unutmayın.")
                    .setPositiveButton("Tamam", null)
                    .setCancelable(true)
                    .show()
            }
            .addOnFailureListener { e ->
                val act = activityRef.get() ?: return@addOnFailureListener
                if (act.isSafeForUi()) safeToast(act, "Hata: ${e.message}")
            }
    }
}
