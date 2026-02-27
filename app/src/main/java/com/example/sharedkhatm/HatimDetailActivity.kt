package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HatimDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var juzAdapter: JuzAdapter
    private lateinit var juzList: ArrayList<Juz>

    private var hatimId: String? = null
    private var hatimName: String? = null
    private var hatimDesc: String? = null
    private var hatimCreatorId: String? = null

    private var partsListener: ListenerRegistration? = null
    private var hatimListener: ListenerRegistration? = null

    private lateinit var txtDetailTitle: TextView
    private lateinit var txtDetailInfo: TextView
    private lateinit var btnEditHatim: LinearLayout
    private lateinit var btnDeleteHatim: LinearLayout

    private val selectFriendsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedIds = result.data?.getStringArrayListExtra("selectedIds")
            if (!selectedIds.isNullOrEmpty()) {
                sendInvitationsToFriends(selectedIds)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hatim_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        hatimId = intent.getStringExtra("hatimId")
        hatimName = intent.getStringExtra("hatimName")
        hatimDesc = intent.getStringExtra("hatimDesc")

        txtDetailTitle = findViewById(R.id.txtDetailTitle)
        txtDetailInfo = findViewById(R.id.txtDetailInfo)
        btnEditHatim = findViewById(R.id.btnEditHatim)
        btnDeleteHatim = findViewById(R.id.btnDeleteHatim)

        val btnShare = findViewById<LinearLayout>(R.id.btnShareWhatsapp)
        val btnInvite = findViewById<LinearLayout>(R.id.btnInviteFriend)
        val btnBack = findViewById<View>(R.id.btnBackDetail)

        updateHeaderUI(hatimName, hatimDesc)

        btnBack.setOnClickListener { finish() }
        btnShare.setOnClickListener { shareEmptyParts() }

        // DAVET BUTONU
        btnInvite.setOnClickListener { checkGuestAndInvite() }

        btnEditHatim.setOnClickListener {
            val intent = Intent(this, EditHatimActivity::class.java)
            intent.putExtra("hatimId", hatimId)
            intent.putExtra("hatimName", hatimName)
            intent.putExtra("hatimDesc", hatimDesc)
            startActivity(intent)
        }

        btnDeleteHatim.setOnClickListener { showDeleteConfirmationDialog() }

        val recyclerJuzList = findViewById<RecyclerView>(R.id.recyclerJuzList)
        juzList = ArrayList()
        for (i in 1..30) juzList.add(Juz(number = i, status = 0))

        juzAdapter = JuzAdapter(juzList)
        recyclerJuzList.adapter = juzAdapter
        recyclerJuzList.layoutManager = GridLayoutManager(this, 5)
        juzAdapter.onJuzClick = { selectedJuz -> showJuzActionDialog(selectedJuz) }

        listenToHatimDetails()
        listenToJuzChanges()
    }

    private fun checkGuestAndInvite() {
        val user = auth.currentUser

        if (user == null || user.isAnonymous) {
            showGuestInviteDialog()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val friends = doc.get("friends") as? List<String>
                if (friends.isNullOrEmpty()) {
                    showNoFriendsDialog()
                } else {
                    val intent = Intent(this, SelectFriendsActivity::class.java)
                    intent.putExtra("hatimId", hatimId)
                    selectFriendsLauncher.launch(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Bağlantı hatası.", Toast.LENGTH_SHORT).show()
            }
    }


    // !!! DÜZELTİLDİ: MaterialAlertDialogBuilder kullanıldı !!!
    private fun showNoFriendsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_no_friends, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnGoToFriends = view.findViewById<Button>(R.id.btnGoToFriends)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancelDialog)

        btnGoToFriends.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("openTab", "friends")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // !!! DÜZELTİLDİ: MaterialAlertDialogBuilder kullanıldı !!!
    private fun showGuestInviteDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_guest_signup, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.txtDialogTitle)?.text = "Üyelik Gerekli"
        view.findViewById<TextView>(R.id.txtDialogMessage)?.text =
            "Halkaya davet edebilmek için kayıt olmalısın.\n\n" +
                    "Üye olarak dostlarını davet edebilir ve birlikte hatim okuyabilirsin."

        view.findViewById<Button>(R.id.btnDialogRegister).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun sendInvitationsToFriends(friendIds: ArrayList<String>) {
        if (hatimId == null) return

        val user = auth.currentUser ?: return
        val senderName = user.displayName ?: "Bir Dost"
        val batch = db.batch()

        for (friendId in friendIds) {
            val inviteRef = db.collection("invitations").document()
            val inviteData = hashMapOf(
                "hatimId" to hatimId,
                "hatimName" to hatimName,
                "senderId" to user.uid,
                "senderName" to senderName,
                "receiverId" to friendId,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(inviteRef, inviteData)
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Davetiyeler gönderildi.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Davet gönderilemedi.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- STANDART FONKSİYONLAR ---
    private fun listenToHatimDetails() {
        if (hatimId == null) return
        hatimListener = db.collection("hatims").document(hatimId!!)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                hatimName = snapshot.getString("name")
                hatimDesc = snapshot.getString("description")
                hatimCreatorId = snapshot.getString("createdBy")
                updateHeaderUI(hatimName, hatimDesc)

                val currentUserId = auth.currentUser?.uid
                if (currentUserId != null && currentUserId == hatimCreatorId) {
                    btnEditHatim.visibility = View.VISIBLE
                    btnDeleteHatim.visibility = View.VISIBLE
                } else {
                    btnEditHatim.visibility = View.GONE
                    btnDeleteHatim.visibility = View.GONE
                }
            }
    }

    private fun updateHeaderUI(name: String?, desc: String?) {
        txtDetailTitle.text = name ?: "Hatim Detayı"
        txtDetailInfo.text = if (!desc.isNullOrEmpty()) desc else "Allah kabul etsin."
    }

    // !!! DÜZELTİLDİ: MaterialAlertDialogBuilder !!!
    private fun showDeleteConfirmationDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirmation, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnConfirmDelete).setOnClickListener { deleteHatim(); dialog.dismiss() }
        view.findViewById<Button>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun deleteHatim() {
        val id = hatimId ?: return

        val hatimDoc = db.collection("hatims").document(id)

        hatimDoc.collection("parts").get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    hatimDoc.delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Hatim silindi.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Silinemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    return@addOnSuccessListener
                }

                val docs = snap.documents
                val chunks = docs.chunked(450)

                fun deleteChunk(index: Int) {
                    if (index >= chunks.size) {
                        hatimDoc.delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Hatim silindi.", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Silinemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        return
                    }

                    val batch = db.batch()
                    for (d in chunks[index]) batch.delete(d.reference)

                    batch.commit()
                        .addOnSuccessListener { deleteChunk(index + 1) }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Silinemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }

                deleteChunk(0)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Silinemedi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun listenToJuzChanges() {
        if (hatimId == null) return
        partsListener = db.collection("hatims").document(hatimId!!).collection("parts").addSnapshotListener { snap, _ ->
            snap?.forEach { doc ->
                val no = doc.id.toIntOrNull()
                if (no != null) {
                    val j = juzList[no - 1]
                    j.status = doc.getLong("status")?.toInt() ?: 0
                    j.ownerName = doc.getString("ownerName")
                    j.ownerId = doc.getString("ownerId")
                    juzAdapter.notifyItemChanged(no - 1)
                }
            }
        }
    }

    // !!! DÜZELTİLDİ: MaterialAlertDialogBuilder !!!
    private fun showJuzActionDialog(juz: Juz) {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid
        val userName = if (currentUser?.isAnonymous == true) "Hatim Gönüllüsü" else (currentUser?.displayName ?: "İsimsiz")
        val isAdmin = (userId != null && userId == hatimCreatorId)

        val view = layoutInflater.inflate(R.layout.dialog_juz_action, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle = view.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = view.findViewById<TextView>(R.id.txtDialogMessage)
        val btnPositive = view.findViewById<Button>(R.id.btnPositive)
        val btnNeutral = view.findViewById<Button>(R.id.btnNeutral)
        val btnNegative = view.findViewById<Button>(R.id.btnNegative)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        txtTitle.text = "${juz.number}. Cüz İşlemi"

        if (juz.status == 0) {
            txtMessage.text = "Bu cüz boş. Almak ister misiniz?"
            btnPositive.text = "Cüzü Al"
            btnPositive.setOnClickListener { updateJuzStatus(juz.number, 1, userId, userName); dialog.dismiss() }
            btnNeutral.visibility = View.GONE
            btnNegative.visibility = View.GONE
        } else if (juz.status == 1 && juz.ownerId == userId) {
            txtMessage.text = "Bu cüzü siz okuyorsunuz."
            btnPositive.text = "Tamamladım"
            btnPositive.setOnClickListener { updateJuzStatus(juz.number, 2, userId, userName); dialog.dismiss() }
            btnNeutral.visibility = View.VISIBLE
            btnNeutral.text = "📖 Oku"
            btnNeutral.setOnClickListener { openReadingPage(juz.number); dialog.dismiss() }
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = "Bırak"
            btnNegative.setOnClickListener { updateJuzStatus(juz.number, 0, null, null); dialog.dismiss() }
        } else if (juz.status == 1 && isAdmin) {
            txtMessage.text = "${juz.ownerName} okuyor. Yönetici işlemi:"
            btnPositive.text = "Tamamlandı İşaretle"
            btnPositive.setOnClickListener { updateJuzStatus(juz.number, 2, juz.ownerId, juz.ownerName); dialog.dismiss() }
            btnNeutral.visibility = View.GONE
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = "Boşa Çıkar"
            btnNegative.setOnClickListener { updateJuzStatus(juz.number, 0, null, null); dialog.dismiss() }
        } else if (juz.status == 2) {
            txtMessage.text = "${juz.ownerName} tamamladı."
            if(isAdmin || juz.ownerId == userId) {
                btnPositive.visibility = View.GONE; btnNeutral.visibility = View.GONE
                btnNegative.visibility = View.VISIBLE
                btnNegative.text = "Geri Al (Okunuyor Yap)"
                btnNegative.setOnClickListener { updateJuzStatus(juz.number, 1, juz.ownerId, juz.ownerName); dialog.dismiss() }
            } else {
                btnPositive.visibility = View.GONE; btnNeutral.visibility = View.GONE; btnNegative.visibility = View.GONE
            }
        } else {
            txtMessage.text = "${juz.ownerName} okuyor."
            btnPositive.visibility = View.GONE; btnNeutral.visibility = View.GONE; btnNegative.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateJuzStatus(juzNumber: Int, newStatus: Int, ownerId: String?, ownerName: String?) {
        if (hatimId == null) return
        val partData = hashMapOf("status" to newStatus, "ownerId" to ownerId, "ownerName" to ownerName, "updatedAt" to com.google.firebase.Timestamp.now())
        db.collection("hatims").document(hatimId!!).collection("parts").document(juzNumber.toString()).set(partData)
            .addOnSuccessListener {
                updateHatimProgress()
                if (newStatus == 2 && ownerId == auth.currentUser?.uid) {
                    if (!auth.currentUser!!.isAnonymous) updateMonthlyLeaderboard(ownerId!!)
                    addJuzToDailyGoal(1)
                }
            }
    }

    private fun updateMonthlyLeaderboard(userId: String) {
        val userRef = db.collection("users").document(userId)
        val currentMonthKey = android.text.format.DateFormat.format("yyyy-MM", java.util.Date()).toString()
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val lastMonth = snapshot.getString("lastActiveMonth")
            val currentCount = snapshot.getLong("monthlyReadCount") ?: 0
            if (lastMonth != currentMonthKey) {
                transaction.update(userRef, "lastActiveMonth", currentMonthKey)
                transaction.update(userRef, "monthlyReadCount", 1)
            } else {
                transaction.update(userRef, "monthlyReadCount", currentCount + 1)
            }
            transaction.update(userRef, "totalReadCount", FieldValue.increment(1))
        }
    }

    private fun addJuzToDailyGoal(juzCount: Int) {
        val userId = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("UserGoal_$userId", MODE_PRIVATE)
        val today = android.text.format.DateFormat.format("yyyyMMdd", java.util.Date()).toString()
        if (today != prefs.getString("lastReadDate", "")) prefs.edit().putInt("juzReadToday", 0).putString("lastReadDate", today).apply()
        val newTotal = prefs.getInt("juzReadToday", 0) + juzCount
        prefs.edit().putInt("juzReadToday", newTotal).apply()
        Toast.makeText(this, "+$juzCount Cüz hedefe eklendi!", Toast.LENGTH_SHORT).show()
    }

    private fun updateHatimProgress() {
        if (hatimId == null) return
        db.collection("hatims").document(hatimId!!).collection("parts").whereEqualTo("status", 2).get()
            .addOnSuccessListener { documents -> db.collection("hatims").document(hatimId!!).update("completedParts", documents.size()) }
    }

    private fun openReadingPage(juzNumber: Int) {
        val prefs = getSharedPreferences("AppGlobalPrefs", MODE_PRIVATE)
        prefs.edit()
            .putString("lastReadType", "JUZ")
            .putInt("lastReadValue", juzNumber)
            .putString("lastReadHatimId", hatimId)
            .putString("lastReadName", "$juzNumber. Cüz")
            .apply()
        val intent = Intent(this, ReadJuzActivity::class.java)
        intent.putExtra("juzNumber", juzNumber); intent.putExtra("hatimId", hatimId)
        startActivity(intent)
    }

    private fun shareEmptyParts() {
        val emptyParts = juzList.filter { it.status == 0 }.map { it.number }
        if (emptyParts.isEmpty()) { Toast.makeText(this, "Hatim Tamamlandı!", Toast.LENGTH_SHORT).show(); return }
        val sb = StringBuilder("📢 *HATIM DUYURUSU* 📢\n\n📖 **$hatimName**\n\nBoş Cüzler: ${emptyParts.joinToString(", ")}\n\nOkumak isteyen var mı? 🤲")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString()) }, "Paylaş"))
    }

    override fun onDestroy() {
        super.onDestroy()
        partsListener?.remove()
        hatimListener?.remove()
    }
}