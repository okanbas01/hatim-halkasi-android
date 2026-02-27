package com.example.sharedkhatm

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HatimInvitationsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listView: ListView
    private lateinit var txtNoInvites: TextView
    private lateinit var progressBar: ProgressBar

    private val inviteList = ArrayList<InvitationModel>()
    private lateinit var adapter: InvitationAdapter
    private var invitationsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hatim_invitations)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        listView = findViewById(R.id.listInvitations)
        txtNoInvites = findViewById(R.id.txtNoInvitations)
        progressBar = findViewById(R.id.progressBarInv)
        val btnBack = findViewById<LinearLayout>(R.id.btnBackInv)

        adapter = InvitationAdapter(this, inviteList)
        listView.adapter = adapter

        btnBack.setOnClickListener { finish() }

        // Tıklama Olayı -> Diyalog Aç
        listView.setOnItemClickListener { _, _, position, _ ->
            val invite = inviteList[position]
            showActionDialog(invite)
        }

        loadInvitations()
    }

    private fun loadInvitations() {
        val userId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        txtNoInvites.visibility = View.GONE

        // SORGUNUN DÜZELTİLMİŞ HALİ
        invitationsListener?.remove()
        invitationsListener = db.collection("invitations")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "pending") // Sadece bekleyenleri getir
            .addSnapshotListener { snapshots, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Hata: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                inviteList.clear()
                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val invite = InvitationModel(
                            inviteId = doc.id,
                            hatimId = doc.getString("hatimId") ?: "",
                            hatimName = doc.getString("hatimName") ?: "Hatim",
                            senderName = doc.getString("senderName") ?: "Biri",
                            status = doc.getString("status") ?: "pending",
                            date = doc.getTimestamp("createdAt")
                        )
                        inviteList.add(invite)
                    }
                    adapter.notifyDataSetChanged()
                    listView.visibility = View.VISIBLE
                    txtNoInvites.visibility = View.GONE
                } else {
                    listView.visibility = View.GONE
                    txtNoInvites.visibility = View.VISIBLE
                }
            }
    }

    override fun onDestroy() {
        invitationsListener?.remove()
        invitationsListener = null
        super.onDestroy()
    }

    private fun showActionDialog(invite: InvitationModel) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_invitation_action)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogHatimName)
        val txtMsg = dialog.findViewById<TextView>(R.id.txtDialogSender)
        val btnAccept = dialog.findViewById<Button>(R.id.btnAccept)
        val btnReject = dialog.findViewById<Button>(R.id.btnReject)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelDialog)

        txtTitle.text = invite.hatimName
        txtMsg.text = "${invite.senderName} seni bu hatim halkasına davet ediyor."

        // KABUL ET BUTONU
        btnAccept.setOnClickListener {
            acceptInvitation(invite, dialog)
        }

        // REDDET BUTONU
        btnReject.setOnClickListener {
            rejectInvitation(invite, dialog)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun acceptInvitation(invite: InvitationModel, dialog: Dialog) {
        val userId = auth.currentUser?.uid ?: return
        val batch = db.batch()

        // 1. Davetiyeyi 'accepted' yap
        val inviteRef = db.collection("invitations").document(invite.inviteId)
        batch.update(inviteRef, "status", "accepted")

        // 2. Kullanıcıyı Hatim 'participants' listesine ekle
        val hatimRef = db.collection("hatims").document(invite.hatimId)
        batch.update(hatimRef, "participants", FieldValue.arrayUnion(userId))

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Hatime katıldınız!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            // Liste zaten snapshotListener ile otomatik güncellenecek
        }.addOnFailureListener {
            Toast.makeText(this, "İşlem başarısız: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rejectInvitation(invite: InvitationModel, dialog: Dialog) {
        db.collection("invitations").document(invite.inviteId)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "Davet reddedildi.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }
}