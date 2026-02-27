package com.example.sharedkhatm

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date
import java.util.Locale

class DuaFragment : Fragment(R.layout.fragment_dua) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: DuaAdapter
    private lateinit var duaList: ArrayList<DuaModel>
    private var myUserId: String = ""
    private var duasListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Kullanıcı ID'sini al (Misafir ise anon_guest)
        myUserId = if (auth.currentUser != null) auth.currentUser!!.uid else "anon_guest"
        // Geri Butonu
        view.findViewById<View>(R.id.btnBackDua).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerDua)
        val fabAdd = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddDua)

        // Liste Ayarları
        recyclerView.layoutManager = LinearLayoutManager(context)
        duaList = ArrayList()
        adapter = DuaAdapter(duaList, myUserId) // Adaptöre ID gönderiyoruz
        recyclerView.adapter = adapter

        loadDuas()

        // Kalp/Amin Tıklama
        adapter.onAminClick = { dua ->
            incrementAmin(dua)
        }

        // --- DUA İSTE BUTONU TIKLAMA ---
        fabAdd.setOnClickListener {
            showAddDuaDialog()
        }
    }

    private fun loadDuas() {
        duasListener?.remove()
        duasListener = db.collection("duas")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                if (snapshots != null) {
                    duaList.clear()
                    for (doc in snapshots) {
                        val dua = doc.toObject(DuaModel::class.java)
                        dua.id = doc.id
                        duaList.add(dua)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    override fun onDestroyView() {
        duasListener?.remove()
        duasListener = null
        super.onDestroyView()
    }

    private fun incrementAmin(dua: DuaModel) {
        // Hem sayıyı artır hem de ID'yi listeye ekle
        db.collection("duas").document(dua.id)
            .update(
                "aminCount", FieldValue.increment(1),
                "likedBy", FieldValue.arrayUnion(myUserId)
            )
            .addOnFailureListener {
                Toast.makeText(context, "İşlem başarısız", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddDuaDialog() {
        // --- 1. SPAM KONTROLÜ (Günde 1 Hak) ---
        val prefs = requireContext().getSharedPreferences("DuaPrefs", android.content.Context.MODE_PRIVATE)
        val lastPostTime = prefs.getLong("lastDuaTime", 0L)
        val currentTime = System.currentTimeMillis()

        // 24 Saat = 86400000 milisaniye
        if (currentTime - lastPostTime < 86400000) {
            val hoursLeft = 24 - ((currentTime - lastPostTime) / 3600000)
            AlertDialog.Builder(context)
                .setTitle("Hakkınız Doldu")
                .setMessage("Herkesin duasının görünebilmesi için günde sadece 1 dua isteği paylaşabilirsiniz.\n\nKalan süre: $hoursLeft saat")
                .setPositiveButton("Tamam", null)
                .setIcon(android.R.drawable.ic_lock_idle_alarm)
                .show()
            return
        }
        // ---------------------------------------

        val input = EditText(context)
        input.hint = "Duanızı buraya yazın..."
        input.minHeight = 250
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES


        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_add_dua)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etInput = dialog.findViewById<EditText>(R.id.etDuaInput)
        val btnPost = dialog.findViewById<View>(R.id.btnPostDua)
        val btnCancel = dialog.findViewById<View>(R.id.btnCancelDua)

        btnPost.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "Boş bırakmayınız", Toast.LENGTH_SHORT).show()
            } else if (text.length < 10) {
                Toast.makeText(context, "Lütfen biraz daha detaylı yazın.", Toast.LENGTH_SHORT).show()
            } else {
                if (containsBadWords(text)) {
                    showWarningDialog()
                } else {
                    postDua(text)
                    // ZAMAN DAMGASINI KAYDET
                    prefs.edit().putLong("lastDuaTime", System.currentTimeMillis()).apply()
                    dialog.dismiss()
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun postDua(text: String) {
        val user = auth.currentUser
        var displayName = "Bir Kardeşiniz"

        if (user != null) {
            if (user.isAnonymous) {
                displayName = "Bir Kardeşiniz"
            } else {
                val realName = user.displayName ?: "Mümin Kardeş"
                displayName = maskName(realName) // İsim maskeleme
            }
        }

        val newDua = DuaModel(
            text = text,
            authorName = displayName,
            authorId = myUserId,
            aminCount = 0,
            createdAt = Date(),
            likedBy = arrayListOf()
        )

        db.collection("duas").add(newDua)
            .addOnSuccessListener {
                Toast.makeText(context, "Duanız paylaşıldı.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun maskName(fullName: String): String {
        val words = fullName.split(" ")
        return words.joinToString(" ") { word ->
            if (word.length > 2) word.substring(0, 2) + "*".repeat(3) else word + "*"
        }
    }

    private fun containsBadWords(text: String): Boolean {
        val badWords = listOf("aptal", "salak", "gerizekalı", "manyak", "ahmak", "küfür", "hakaret") // Listeyi genişletebilirsin
        val lowerText = text.lowercase(Locale("tr", "TR"))
        for (word in badWords) {
            if (lowerText.contains(word)) return true
        }
        return false
    }

    private fun showWarningDialog() {
        AlertDialog.Builder(context)
            .setTitle("Uyarı")
            .setMessage("İçeriğinizde uygunsuz ifadeler tespit edildi.")
            .setPositiveButton("Tamam", null)
            .show()
    }
}