package com.example.sharedkhatm

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var foundUserId: String? = null

    // View Elemanları
    private var layoutResult: LinearLayout? = null
    private var btnAdd: Button? = null
    private var txtSearchUsername: TextView? = null
    private var etSearch: EditText? = null
    private var btnSearch: Button? = null
    private var txtNoRequests: TextView? = null
    private var listRequests: ListView? = null
    private var listFriends: ListView? = null

    private var requestListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null

    // Yeni Adapter için liste
    private val friendModels = ArrayList<FriendModel>()
    private var friendsAdapter: FriendsListAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            // View Bağlama
            etSearch = view.findViewById(R.id.etSearchUser)
            btnSearch = view.findViewById(R.id.btnSearch)
            layoutResult = view.findViewById(R.id.layoutSearchResult)
            txtSearchUsername = view.findViewById(R.id.txtSearchUsername)
            btnAdd = view.findViewById(R.id.btnAddFriend)
            txtNoRequests = view.findViewById(R.id.txtNoRequests)
            listRequests = view.findViewById(R.id.listRequests)
            listFriends = view.findViewById(R.id.listFriends)

            if (currentUser == null || currentUser.isAnonymous) {
                view.findViewById<View>(R.id.cardSearch).visibility = View.GONE
                txtNoRequests?.text = "Dostlarını görmek için üye olmalısın."
                txtNoRequests?.visibility = View.VISIBLE
                return
            }

            val currentUserId = currentUser.uid

            // Adapter Kurulumu (Silme işlemi buraya bağlanıyor)
            friendsAdapter = FriendsListAdapter(requireContext(), friendModels) { friendToDelete ->
                showDeleteDialog(friendToDelete)
            }
            listFriends?.adapter = friendsAdapter


            // Arama Listener
            etSearch?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    layoutResult?.visibility = View.GONE
                    foundUserId = null
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            btnSearch?.setOnClickListener {
                val username = etSearch?.text.toString().trim()
                if (username.isNotEmpty()) searchUser(username, currentUserId)
            }

            btnAdd?.setOnClickListener {
                if (foundUserId != null && btnAdd != null) {
                    sendFriendRequest(currentUserId, foundUserId!!, btnAdd!!)
                }
            }

            loadIncomingRequests(currentUserId)
            loadFriends(currentUserId)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SİLME İŞLEMLERİ (YENİ EKLENEN KISIM) ---

    private fun showDeleteDialog(friend: FriendModel) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_delete_friend)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtMessage = dialog.findViewById<TextView>(R.id.txtDeleteMessage)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDelete)

        txtMessage.text = "${friend.name} adlı kişiyi dostların arasından çıkarmak istediğine emin misin?"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            removeFriend(friend.uid, friend.name)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun removeFriend(friendId: String, friendName: String) {
        val myId = auth.currentUser?.uid ?: return

        // Karşılıklı Silme İşlemi (Batch)
        val batch = db.batch()

        // 1. Benim listemden onu sil
        val myRef = db.collection("users").document(myId)
        batch.update(myRef, "friends", FieldValue.arrayRemove(friendId))

        // 2. Onun listesinden beni sil
        val friendRef = db.collection("users").document(friendId)
        batch.update(friendRef, "friends", FieldValue.arrayRemove(myId))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(context, "$friendName dostlardan çıkarıldı.", Toast.LENGTH_SHORT).show()
                // Liste Listener sayesinde otomatik güncellenecek
            }
            .addOnFailureListener {
                Toast.makeText(context, "Bir hata oluştu: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- MEVCUT FONKSİYONLAR (GÜNCELLENDİ) ---

    private fun loadFriends(myId: String) {
        friendsListener = db.collection("users").document(myId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val friendsIds = doc.get("friends") as? ArrayList<String> ?: arrayListOf()

                // Listeyi temizle ve yeniden doldur
                friendModels.clear()
                if (friendsAdapter != null) friendsAdapter!!.notifyDataSetChanged()

                if (friendsIds.isEmpty()) return@addSnapshotListener

                // ID'lerden İsimleri Çek
                for (friendId in friendsIds) {
                    db.collection("users").document(friendId).get().addOnSuccessListener { fDoc ->
                        val realName = fDoc.getString("name") ?: "İsimsiz"
                        val uName = fDoc.getString("username") ?: "..."

                        // Modeli oluştur ve listeye ekle
                        friendModels.add(FriendModel(friendId, realName, uName))

                        // Adapter'ı güncelle
                        if (friendsAdapter != null) friendsAdapter!!.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // ... (searchUser, checkFriendshipStatus, sendFriendRequest, loadIncomingRequests AYNI KALIYOR) ...
    // Sadece yukarıda eklediklerimi değiştirmen yeterli.

    // searchUser ve diğer fonksiyonlar senin attığın koddaki gibi kalabilir,
    // ama loadFriends'i yukarıdaki gibi değiştirmen ŞART.

    private fun searchUser(username: String, currentUserId: String) {
        db.collection("users").whereEqualTo("username", username).get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    foundUserId = doc.getString("uid")
                    val foundName = doc.getString("username")

                    if (foundUserId == currentUserId) {
                        Toast.makeText(context, "Kendini ekleyemezsin :)", Toast.LENGTH_SHORT).show()
                    } else {
                        layoutResult?.visibility = View.VISIBLE
                        txtSearchUsername?.text = foundName
                        if (btnAdd != null) checkFriendshipStatus(currentUserId, foundUserId!!, btnAdd!!)
                    }
                } else {
                    Toast.makeText(context, "Kullanıcı bulunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkFriendshipStatus(myId: String, targetId: String, btn: Button) {
        btn.isEnabled = true
        btn.text = "Ekle"
        btn.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.primary_green))

        db.collection("users").document(myId).get().addOnSuccessListener { doc ->
            val friends = doc.get("friends") as? List<String> ?: listOf()
            val sentRequests = doc.get("sentRequests") as? List<String> ?: listOf()

            if (friends.contains(targetId)) {
                btn.text = "Arkadaşsınız"
                btn.isEnabled = false
                btn.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            } else if (sentRequests.contains(targetId)) {
                btn.text = "İstek Gönderildi"
                btn.isEnabled = false
                btn.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            }
        }
    }

    private fun sendFriendRequest(myId: String, targetId: String, btn: Button) {
        btn.isEnabled = false
        db.collection("users").document(myId).update("sentRequests", FieldValue.arrayUnion(targetId))
        db.collection("users").document(targetId).update("receivedRequests", FieldValue.arrayUnion(myId))
            .addOnSuccessListener {
                Toast.makeText(context, "İstek başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                btn.text = "İstek Gönderildi"
                btn.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
                btn.isEnabled = false
            }
            .addOnFailureListener {
                btn.isEnabled = true
                Toast.makeText(context, "Hata oluştu: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadIncomingRequests(myId: String) {
        requestListener = db.collection("users").document(myId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val requests = doc.get("receivedRequests") as? ArrayList<String> ?: arrayListOf()
                if (activity is HomeActivity) { (activity as HomeActivity).showBadge(requests.size) }

                if (requests.isEmpty()) {
                    txtNoRequests?.visibility = View.VISIBLE
                    listRequests?.visibility = View.GONE
                } else {
                    txtNoRequests?.visibility = View.GONE
                    listRequests?.visibility = View.VISIBLE
                    val requestNames = ArrayList<String>()
                    val requestIds = ArrayList<String>()
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, requestNames)
                    listRequests?.adapter = adapter

                    for (reqId in requests) {
                        db.collection("users").document(reqId).get().addOnSuccessListener { userDoc ->
                            val uName = userDoc.getString("username")
                            if (uName != null) {
                                requestNames.add("📩 $uName")
                                requestIds.add(reqId)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                    listRequests?.setOnItemClickListener { _, _, position, _ ->
                        if (position < requestIds.size) showAcceptDialog(myId, requestIds[position], requestNames[position])
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requestListener?.remove()
        friendsListener?.remove()
    }

    private fun showAcceptDialog(myId: String, senderId: String, senderName: String) {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_friend_request)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtName = dialog.findViewById<TextView>(R.id.txtSenderName)
        val btnAccept = dialog.findViewById<Button>(R.id.btnAcceptRequest)
        val btnReject = dialog.findViewById<Button>(R.id.btnRejectRequest)

        val cleanName = senderName.replace("📩 ", "")
        txtName.text = cleanName

        btnAccept.setOnClickListener {
            val batch = db.batch()
            val myRef = db.collection("users").document(myId)
            val senderRef = db.collection("users").document(senderId)
            batch.update(myRef, "receivedRequests", FieldValue.arrayRemove(senderId))
            batch.update(myRef, "friends", FieldValue.arrayUnion(senderId))
            batch.update(senderRef, "sentRequests", FieldValue.arrayRemove(myId))
            batch.update(senderRef, "friends", FieldValue.arrayUnion(myId))
            batch.commit().addOnSuccessListener { Toast.makeText(context, "Artık arkadaşsınız!", Toast.LENGTH_SHORT).show() }
            dialog.dismiss()
        }

        btnReject.setOnClickListener {
            db.collection("users").document(myId).update("receivedRequests", FieldValue.arrayRemove(senderId))
            db.collection("users").document(senderId).update("sentRequests", FieldValue.arrayRemove(myId))
            Toast.makeText(context, "İstek reddedildi.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}