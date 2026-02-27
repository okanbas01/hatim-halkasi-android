package com.example.sharedkhatm

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var foundUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return

        val etSearch = findViewById<EditText>(R.id.etSearchUser)
        val btnSearch = findViewById<Button>(R.id.btnSearch)
        val txtResult = findViewById<TextView>(R.id.txtSearchResult)
        val btnAdd = findViewById<Button>(R.id.btnAddFriend)
        val listFriends = findViewById<ListView>(R.id.listFriends)

        // 1. Kullanıcı Ara
        btnSearch.setOnClickListener {
            val username = etSearch.text.toString().trim()
            if (username.isEmpty()) return@setOnClickListener

            db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val doc = documents.documents[0]
                        foundUserId = doc.getString("uid")
                        val name = doc.getString("name")

                        // Kendini ekleyemesin
                        if (foundUserId == currentUserId) {
                            txtResult.text = "Bu sensin :)"
                            btnAdd.visibility = View.GONE
                        } else {
                            txtResult.text = "Bulundu: $name"
                            txtResult.visibility = View.VISIBLE
                            btnAdd.visibility = View.VISIBLE
                        }
                    } else {
                        txtResult.text = "Kullanıcı bulunamadı."
                        txtResult.visibility = View.VISIBLE
                        btnAdd.visibility = View.GONE
                    }
                }
        }

        // 2. Arkadaş Ekle
        btnAdd.setOnClickListener {
            if (foundUserId != null) {
                // Benim arkadaş listeme onun ID'sini ekle
                db.collection("users").document(currentUserId)
                    .update("friends", FieldValue.arrayUnion(foundUserId))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Arkadaş eklendi!", Toast.LENGTH_SHORT).show()
                        loadFriends(currentUserId, listFriends)
                        txtResult.visibility = View.GONE
                        btnAdd.visibility = View.GONE
                        etSearch.text.clear()
                    }
            }
        }

        // 3. Mevcut Arkadaşları Listele
        loadFriends(currentUserId, listFriends)
    }

    private fun loadFriends(myId: String, listView: ListView) {
        // Önce benim arkadaş listemi (UID'leri) çek
        db.collection("users").document(myId).get()
            .addOnSuccessListener { document ->
                val friendsIds = document.get("friends") as? ArrayList<String>

                if (!friendsIds.isNullOrEmpty()) {
                    // Bu ID'lerin kime ait olduğunu bul (Basit yöntem)
                    // Gerçek uygulamada "whereIn" kullanılır ama 10 kişi sınırı var.
                    // Şimdilik sadece ID'leri ya da tek tek isimleri çekelim.

                    val friendNames = ArrayList<String>()
                    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, friendNames)
                    listView.adapter = adapter

                    // Her bir arkadaşın ismini çek
                    for (friendId in friendsIds) {
                        db.collection("users").document(friendId).get().addOnSuccessListener { friendDoc ->
                            val name = friendDoc.getString("name")
                            if (name != null) {
                                friendNames.add(name)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
    }
}