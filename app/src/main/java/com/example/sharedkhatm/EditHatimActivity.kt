package com.example.sharedkhatm

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditHatimActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Lateinit değişkenler (Global erişim için)
    private lateinit var etName: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var layoutName: TextInputLayout
    private lateinit var layoutDesc: TextInputLayout
    private lateinit var btnSave: Button

    private var hatimId: String? = null
    private var adminId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_hatim)

        try {
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            // View Elemanlarını Bağlama
            etName = findViewById(R.id.etEditHatimName)
            etDesc = findViewById(R.id.etEditHatimDesc)
            layoutName = findViewById(R.id.layoutEditHatimName)
            layoutDesc = findViewById(R.id.layoutEditHatimDesc)
            btnSave = findViewById(R.id.btnSaveChanges)

            // DÜZELTİLEN KISIM: Türleri belirttik (<Button> ve <ImageView>)
            val btnCancel = findViewById<Button>(R.id.btnCancelEdit)
            val btnBack = findViewById<ImageView>(R.id.btnBackEdit)

            // Butonu başta pasif yapalım (Admin kontrolü bitene kadar)
            btnSave.isEnabled = false

            // Intent'ten ID al
            hatimId = intent.getStringExtra("hatimId")

            if (hatimId == null) {
                Toast.makeText(this, "Hatim bulunamadı.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Geri Butonları - Artık hata vermeyecek
            btnBack.setOnClickListener { finish() }
            btnCancel.setOnClickListener { finish() }

            // VERİYİ ÇEK VE ADMİN Mİ KONTROL ET
            checkAdminAndLoadData()

            // KAYDET BUTONU
            btnSave.setOnClickListener {
                saveChanges()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkAdminAndLoadData() {
        if (hatimId == null) return

        db.collection("hatims").document(hatimId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    val desc = document.getString("description")
                    adminId = document.getString("createdBy")

                    // GÜVENLİK KONTROLÜ: SADECE ADMİN DÜZENLEYEBİLİR
                    val currentUserId = auth.currentUser?.uid
                    if (adminId != currentUserId) {
                        Toast.makeText(this, "Bu hatimi sadece oluşturan kişi düzenleyebilir.", Toast.LENGTH_LONG).show()
                        finish() // Yetkisiz giriş, sayfayı kapat
                        return@addOnSuccessListener
                    }

                    // Admin ise verileri doldur ve butonu aç
                    etName.setText(name)
                    etDesc.setText(desc)
                    btnSave.isEnabled = true
                } else {
                    Toast.makeText(this, "Hatim silinmiş veya bulunamadı.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Veri yüklenemedi: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun saveChanges() {
        val newName = etName.text.toString().trim()
        val newDesc = etDesc.text.toString().trim()

        // 1. Validasyon: Boş olamaz
        if (newName.isEmpty()) {
            layoutName.error = "Hatim başlığı boş olamaz"
            return
        } else {
            layoutName.error = null
        }

        // 2. Validasyon: Karakter Sınırı
        if (newName.length > 100) {
            layoutName.error = "Başlık en fazla 100 karakter olabilir"
            return
        }
        if (newDesc.length > 500) {
            layoutDesc.error = "Açıklama en fazla 500 karakter olabilir"
            return
        }

        // Kaydetme İşlemi
        btnSave.isEnabled = false
        btnSave.text = "Kaydediliyor..."

        val updates = mapOf(
            "name" to newName,
            "description" to newDesc
        )

        db.collection("hatims").document(hatimId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Hatim başarıyla güncellendi!", Toast.LENGTH_SHORT).show()
                finish() // Detay sayfasına dön
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Güncelleme başarısız: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
                btnSave.text = "Değişiklikleri Kaydet"
            }
    }
}