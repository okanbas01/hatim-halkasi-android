package com.example.sharedkhatm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class CreateHatimActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedFriendIds = ArrayList<String>()
    private lateinit var txtSelectedCount: TextView
    private lateinit var btnCreateAction: Button

    private lateinit var etHatimName: TextInputEditText
    private lateinit var etDescription: TextInputEditText

    private val PREFS = "AppGlobalPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_hatim)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        etHatimName = findViewById(R.id.etHatimName)
        etDescription = findViewById(R.id.etDescription)
        btnCreateAction = findViewById(R.id.btnCreateAction)
        val btnSelectFriends = findViewById<View>(R.id.btnSelectFriends)
        txtSelectedCount = findViewById(R.id.txtSelectedCount)

        // Başta butonları pasif yap, auth hazır olunca aç
        setUiEnabled(false)

        val selectFriendsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val ids = result.data?.getStringArrayListExtra("selectedIds")
                if (ids != null) {
                    selectedFriendIds = ids
                    txtSelectedCount.text = "${ids.size} kişi seçildi"
                }
            }
        }

        // ✅ Kullanıcı null geldiyse (misafir flag var ama auth düşmüş olabilir) anon login yap
        ensureSignedInThen {
            val user = auth.currentUser
            if (user != null) {
                val status = if (user.isAnonymous) "MİSAFİR" else "KAYITLI ÜYE"
                Toast.makeText(this, "Şu anki durum: $status", Toast.LENGTH_SHORT).show()
            }
            setUiEnabled(true)
        }

        // --- 1) HALKAYA DAVET ET ---
        btnSelectFriends.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Oturum bulunamadı. Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                ensureSignedInThen { setUiEnabled(true) }
                return@setOnClickListener
            }

            if (user.isAnonymous) {
                showGuestInviteDialog()
            } else {
                Toast.makeText(this, "Arkadaş Listesi açılıyor...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SelectFriendsActivity::class.java)
                selectFriendsLauncher.launch(intent)
            }
        }

        // --- 2) HATMİ BAŞLAT ---
        btnCreateAction.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Oturum bulunamadı. Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                ensureSignedInThen { setUiEnabled(true) }
                return@setOnClickListener
            }

            val hatimName = etHatimName.text?.toString()?.trim().orEmpty()
            val description = etDescription.text?.toString()?.trim().orEmpty()

            if (hatimName.isEmpty()) {
                etHatimName.error = "Hatim ismi gerekli"
                return@setOnClickListener
            }

            btnCreateAction.isEnabled = false
            btnCreateAction.text = "Oluşturuluyor..."

            val userName = user.displayName ?: "İsimsiz"
            val hatimData = hashMapOf(
                "name" to hatimName,
                "description" to description,
                "createdBy" to user.uid,
                "createdByName" to userName,
                "createdAt" to FieldValue.serverTimestamp(),
                "totalParts" to 30,
                "completedParts" to 0,
                "participants" to arrayListOf(user.uid)
            )

            if (user.isAnonymous) {
                // ✅ Misafir: sadece 1 hatim (doc id = uid)
                val docRef = db.collection("hatims").document(user.uid)
                docRef.get().addOnSuccessListener { snap ->
                    if (snap != null && snap.exists()) {
                        resetCreateButton()
                        showGuestLimitDialog()
                    } else {
                        docRef.set(hatimData).addOnSuccessListener {
                            // misafir flag’i garanti
                            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit().putBoolean("isGuest", true).apply()

                            navigateToDetail(user.uid, hatimName, description)
                        }.addOnFailureListener {
                            resetCreateButton()
                            Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.addOnFailureListener {
                    resetCreateButton()
                    Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
                }

            } else {
                // ✅ Üye: add()
                db.collection("hatims").add(hatimData)
                    .addOnSuccessListener { docRef ->
                        val hatimId = docRef.id
                        if (selectedFriendIds.isNotEmpty()) {
                            sendInvitations(hatimId, hatimName, userName, selectedFriendIds, description)
                        } else {
                            navigateToDetail(hatimId, hatimName, description)
                        }
                    }
                    .addOnFailureListener {
                        resetCreateButton()
                        Toast.makeText(this, "Kayıt Hatası: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun ensureSignedInThen(onReady: () -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            onReady()
            return
        }

        // Eğer daha önce misafir seçilmişse anon giriş yap
        val isGuest = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("isGuest", false)

        if (isGuest) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean("isGuest", true).apply()
                    onReady()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Misafir girişi başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                    onReady() // UI açılmasın istiyorsan burayı kaldırabiliriz
                }
        } else {
            // Yeni kullanıcı - burada otomatik anon açmak istemiyorsan sadece onReady
            onReady()
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        btnCreateAction.isEnabled = enabled
        findViewById<View>(R.id.btnSelectFriends)?.isEnabled = enabled
        if (enabled && btnCreateAction.text.toString() != "Hatmi Başlat") {
            btnCreateAction.text = "Hatmi Başlat"
        }
    }

    private fun resetCreateButton() {
        btnCreateAction.isEnabled = true
        btnCreateAction.text = "Hatmi Başlat"
    }

    private fun showGuestLimitDialog() {
        try {
            val view = layoutInflater.inflate(R.layout.dialog_guest_signup, null)
            val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            view.findViewById<TextView>(R.id.txtDialogTitle)?.text = "Üyelik Gerekli"
            view.findViewById<TextView>(R.id.txtDialogMessage)?.text =
                "Misafir olarak sadece 1 hatim oluşturabilirsin.\n\nİkinci hatimi başlatmak için ücretsiz üye olmalısın."

            view.findViewById<Button>(R.id.btnDialogRegister).setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            view.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showGuestInviteDialog() {
        try {
            val view = layoutInflater.inflate(R.layout.dialog_guest_signup, null)
            val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            view.findViewById<TextView>(R.id.txtDialogTitle)?.text = "Üyelik Gerekli"
            view.findViewById<TextView>(R.id.txtDialogMessage)?.text =
                "Dostlarını davet edebilmek için kayıt olman gerekiyor.\n\nÜye olarak arkadaş ekleyebilir ve birlikte hatim okuyabilirsin."

            view.findViewById<Button>(R.id.btnDialogRegister).setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            view.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun sendInvitations(
        hatimId: String,
        hatimName: String,
        inviterName: String,
        friendIds: ArrayList<String>,
        desc: String
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            Toast.makeText(this, "Oturum bulunamadı. Davet gönderilemedi.", Toast.LENGTH_SHORT).show()
            navigateToDetail(hatimId, hatimName, desc)
            return
        }

        val batch = db.batch()
        for (friendId in friendIds) {
            val inviteRef = db.collection("invitations").document()
            val inviteData = hashMapOf(
                "hatimId" to hatimId,
                "hatimName" to hatimName,
                "senderId" to currentUid,
                "senderName" to inviterName,
                "receiverId" to friendId,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(inviteRef, inviteData)
        }
        batch.commit()
            .addOnSuccessListener { navigateToDetail(hatimId, hatimName, desc) }
            .addOnFailureListener { navigateToDetail(hatimId, hatimName, desc) }
    }

    private fun navigateToDetail(id: String, name: String, desc: String?) {
        val intent = Intent(this, HatimDetailActivity::class.java)
        intent.putExtra("hatimId", id)
        intent.putExtra("hatimName", name)
        intent.putExtra("hatimDesc", desc)
        startActivity(intent)
        finish()
    }
}
