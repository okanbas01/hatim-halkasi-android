package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import androidx.annotation.Keep

class SelectFriendsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listSelectFriends: ListView
    private lateinit var adapter: SelectFriendsAdapter

    // Arkadaş Listeleri
    private val friendList = ArrayList<UserModel>() // Tüm arkadaşlar
    private val filteredList = ArrayList<UserModel>() // Ekranda gösterilecekler

    private var hatimId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_friends)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Intent'ten Hatim ID'yi al
        hatimId = intent.getStringExtra("hatimId")

        listSelectFriends = findViewById(R.id.listSelectFriends)

        // --- DÜZELTME BURADA: <Button> eklendi ---
        val btnConfirm = findViewById<Button>(R.id.btnConfirmSelection)
        val btnSelectAll = findViewById<Button>(R.id.btnSelectAll)
        val layoutEmpty = findViewById<LinearLayout>(R.id.layoutEmptyState)

        // Özel Adapter (Checkbox'lı)
        adapter = SelectFriendsAdapter(this, filteredList)
        listSelectFriends.adapter = adapter

        // --- VERİLERİ YÜKLE ---
        loadAndFilterFriends(layoutEmpty, btnConfirm)

        // Tümünü Seç Butonu
        btnSelectAll.setOnClickListener {
            val allSelected = filteredList.all { it.isSelected }
            filteredList.forEach { it.isSelected = !allSelected }
            adapter.notifyDataSetChanged()
            btnSelectAll.text = if (!allSelected) "Seçimi Kaldır" else "Tümünü Seç"
        }

        // Onayla Butonu
        btnConfirm.setOnClickListener {
            val selectedIds = ArrayList<String>()
            for (user in filteredList) {
                if (user.isSelected) {
                    selectedIds.add(user.uid)
                }
            }

            if (selectedIds.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putStringArrayListExtra("selectedIds", selectedIds)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Lütfen en az bir arkadaş seçin.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAndFilterFriends(layoutEmpty: LinearLayout, btnConfirm: Button) {
        val currentUserId = auth.currentUser?.uid ?: return

        // 1. Önce Kullanıcının Arkadaşlarını Çek
        db.collection("users").document(currentUserId).get().addOnSuccessListener { userDoc ->
            val friendIds = userDoc.get("friends") as? List<String> ?: listOf()

            if (friendIds.isEmpty()) {
                showEmptyState(layoutEmpty, btnConfirm, "Henüz hiç arkadaşın yok")
                return@addOnSuccessListener
            }

            // Arkadaş detaylarını çek (İsim, ID vb.)
            val tasks = friendIds.map { friendId ->
                db.collection("users").document(friendId).get()
            }

            Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks).addOnSuccessListener { results ->
                friendList.clear()
                for (doc in results) {
                    val uid = doc.getString("uid")
                    val name = doc.getString("name") ?: "İsimsiz"
                    val username = doc.getString("username") ?: ""
                    if (uid != null) {
                        friendList.add(UserModel(uid, name, username, false))
                    }
                }

                // EĞER HATIM ID VARSA FİLTRELE, YOKSA HEPSİNİ GÖSTER
                if (hatimId != null) {
                    filterForExistingHatim(layoutEmpty, btnConfirm)
                } else {
                    // Yeni hatim oluşturuluyor, filtreye gerek yok
                    filteredList.addAll(friendList)
                    adapter.notifyDataSetChanged()
                    checkEmptyState(layoutEmpty, btnConfirm)
                }
            }
        }
    }

    private fun filterForExistingHatim(layoutEmpty: LinearLayout, btnConfirm: Button) {
        // İki sorgu yapacağız:
        // 1. Hatimin mevcut katılımcıları (participants)
        // 2. Hatim için bekleyen davetler (invitations -> status == pending)

        val hatimTask = db.collection("hatims").document(hatimId!!).get()
        val inviteTask = db.collection("invitations")
            .whereEqualTo("hatimId", hatimId)
            .whereEqualTo("status", "pending")
            .get()

        Tasks.whenAllSuccess<Any>(hatimTask, inviteTask).addOnSuccessListener { results ->
            val hatimDoc = results[0] as com.google.firebase.firestore.DocumentSnapshot
            val inviteSnapshot = results[1] as QuerySnapshot

            // A) Mevcut Katılımcılar Listesi
            val participants = hatimDoc.get("participants") as? List<String> ?: listOf()

            // B) Bekleyen Davetliler Listesi (Receiver ID'leri)
            val pendingInvites = inviteSnapshot.documents.mapNotNull { it.getString("receiverId") }

            // FİLTRELEME:
            // Arkadaş listesinden, (Katılımcıları) VE (Bekleyenleri) çıkar.

            filteredList.clear()
            for (friend in friendList) {
                val isParticipant = participants.contains(friend.uid)
                val isPending = pendingInvites.contains(friend.uid)

                if (!isParticipant && !isPending) {
                    filteredList.add(friend)
                }
            }

            adapter.notifyDataSetChanged()

            if (filteredList.isEmpty()) {
                showEmptyState(layoutEmpty, btnConfirm, "Tüm arkadaşların zaten ekli")
            }else {
                layoutEmpty.visibility = View.GONE
                btnConfirm.visibility = View.VISIBLE
            }
        }
    }

    private fun checkEmptyState(layoutEmpty: LinearLayout, btnConfirm: Button) {
        if (filteredList.isEmpty()) {
            showEmptyState(layoutEmpty, btnConfirm, "Gösterilecek arkadaş yok")
        } else {
            layoutEmpty.visibility = View.GONE
            btnConfirm.visibility = View.VISIBLE
        }
    }

    private fun showEmptyState(layoutEmpty: LinearLayout, btnConfirm: Button, message: String) {
        layoutEmpty.visibility = View.VISIBLE
        btnConfirm.visibility = View.GONE
        listSelectFriends.visibility = View.GONE // Listeyi de gizle ki arkada kalmasın

        val txtMessage = layoutEmpty.findViewById<TextView>(R.id.txtEmptyMessage)
        txtMessage?.text = message
    }
}

@Keep
// Basit Model Sınıfı
data class UserModel(
    val uid: String,
    val name: String,
    val username: String,
    var isSelected: Boolean = false
)