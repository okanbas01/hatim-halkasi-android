package com.example.sharedkhatm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var txtName: TextView
    private lateinit var txtUsername: TextView
    private lateinit var txtTotalHatims: TextView
    private lateinit var txtTotalJuzs: TextView
    private lateinit var txtBadgeCount: TextView
    private lateinit var recyclerBadges: RecyclerView

    private lateinit var layoutMember: LinearLayout
    private lateinit var layoutGuest: LinearLayout

    private lateinit var btnSettings: View
    private lateinit var btnLogout: View
    private lateinit var btnLogin: View
    private lateinit var btnRegister: View
    
    private lateinit var badgeAdapter: BadgeAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews(view)
        setupClickListeners()
        checkUserStateAndLoadData()
    }

    override fun onResume() {
        super.onResume()
        checkUserStateAndLoadData()
        // Rozetleri güncelle (hafif kontrol) - sadece adapter varsa
        if (::badgeAdapter.isInitialized) {
            updateBadges()
        }
    }
    
    /** Rozetleri güncelle - performans odaklı */
    private fun updateBadges() {
        val unlockedBadges = BadgeManager.getUnlockedBadges(requireContext())
        val isPremium = BadgeManager.isPremiumUser(requireContext())
        val allBadges = BadgeManager.allBadges.filter { 
            !it.isPremium || isPremium 
        }
        val sortedBadges = allBadges.sortedBy { 
            if (unlockedBadges.contains(it.id)) 0 else 1 
        }
        
        // Adapter'ı güncelle - stable ID ile performanslı
        badgeAdapter = BadgeAdapter(sortedBadges, unlockedBadges)
        badgeAdapter.onBadgeClick = { badge ->
            BadgeUnlockDialog.showUnlockDialog(requireContext(), badge)
        }
        recyclerBadges.adapter = badgeAdapter
        txtBadgeCount.text = "${unlockedBadges.size} Rozet"
    }

    private fun initViews(view: View) {
        txtName = view.findViewById(R.id.txtProfileName)
        txtUsername = view.findViewById(R.id.txtProfileUsername)
        txtTotalHatims = view.findViewById(R.id.txtTotalHatims)
        txtTotalJuzs = view.findViewById(R.id.txtTotalJuzs)
        txtBadgeCount = view.findViewById(R.id.txtBadgeCount)
        recyclerBadges = view.findViewById(R.id.recyclerBadges)

        layoutMember = view.findViewById(R.id.layoutMemberActions)
        layoutGuest = view.findViewById(R.id.layoutGuestActions)

        btnSettings = view.findViewById(R.id.btnProfileSettings)
        btnLogout = view.findViewById(R.id.btnProfileLogout)
        btnLogin = view.findViewById(R.id.btnProfileLogin)
        btnRegister = view.findViewById(R.id.btnProfileRegister)
        
        setupBadgesRecycler()
    }
    
    /** Rozet RecyclerView kurulumu - performans odaklı. Eski kullanıcılar için sync bir kez çalışır. */
    private fun setupBadgesRecycler() {
        BadgeManager.syncBadgesFromExistingProgress(requireContext())
        val unlockedBadges = BadgeManager.getUnlockedBadges(requireContext())
        val isPremium = BadgeManager.isPremiumUser(requireContext())
        
        // Tüm rozetleri al (premium kontrolü ile filtrele)
        val allBadges = BadgeManager.allBadges.filter { 
            !it.isPremium || isPremium 
        }
        
        // Açılmış rozetler üstte, kilitli rozetler altta
        val sortedBadges = allBadges.sortedBy { 
            if (unlockedBadges.contains(it.id)) 0 else 1 
        }
        
        badgeAdapter = BadgeAdapter(sortedBadges, unlockedBadges)
        badgeAdapter.onBadgeClick = { badge ->
            BadgeUnlockDialog.showUnlockDialog(requireContext(), badge)
        }
        
        recyclerBadges.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerBadges.adapter = badgeAdapter
        
        // Rozet sayısını güncelle
        val unlockedCount = unlockedBadges.size
        txtBadgeCount.text = "$unlockedCount Rozet"
    }

    private fun checkUserStateAndLoadData() {
        val user = auth.currentUser

        if (user == null) {
            showGuestUI()
            txtUsername.text = "Oturum Açılmadı"
            return
        }

        if (user.isAnonymous) {
            showGuestUI()
            calculateStats(user.uid)
        } else {
            showMemberUI()
            loadMemberProfile(user.uid)
            calculateStats(user.uid)
        }
    }

    private fun showGuestUI() {
        layoutGuest.visibility = View.VISIBLE
        layoutMember.visibility = View.GONE
        txtName.text = "Hatim Gönüllüsü"
        txtUsername.text = "Misafir Kullanıcı"
    }

    private fun showMemberUI() {
        layoutMember.visibility = View.VISIBLE
        layoutGuest.visibility = View.GONE
    }

    private fun loadMemberProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener {
                if (it.exists()) {
                    txtUsername.text = "@${it.getString("username") ?: ""}"
                    txtName.text = it.getString("name") ?: "Kullanıcı"
                }
            }
    }

    /** Firebase'den gelen hatim/cüz sayılarını UserGoal'a yazar; rozet kontrolü yapar ve listeyi günceller. */
    private fun saveStatsAndRefreshBadges(userId: String, completedHatims: Int, totalJuzRead: Int) {
        requireContext().getSharedPreferences("UserGoal_$userId", Context.MODE_PRIVATE).edit()
            .putInt("completedHatims", completedHatims)
            .putInt("totalJuzRead", totalJuzRead)
            .apply()
        BadgeManager.checkAndUnlockBadges(requireContext())
        if (::badgeAdapter.isInitialized) updateBadges()
    }

    private fun calculateStats(userId: String) {
        txtTotalHatims.text = "..."
        txtTotalJuzs.text = "..."

        db.collection("hatims")
            .whereArrayContains("participants", userId)
            .get()
            .addOnSuccessListener { hatimDocuments ->

                var completedHatimCount = 0
                for (doc in hatimDocuments) {
                    val completedParts = doc.getLong("completedParts")?.toInt() ?: 0
                    val totalParts = doc.getLong("totalParts")?.toInt() ?: 30
                    if (completedParts >= totalParts) completedHatimCount++
                }
                txtTotalHatims.text = completedHatimCount.toString()

                val tasks = mutableListOf<Task<QuerySnapshot>>()
                for (doc in hatimDocuments) {
                    val query = doc.reference.collection("parts")
                        .whereEqualTo("ownerId", userId)
                        .get()
                    tasks.add(query)
                }

                if (tasks.isNotEmpty()) {
                    Tasks.whenAllSuccess<QuerySnapshot>(tasks).addOnSuccessListener { results ->
                        var totalReadJuzCount = 0
                        for (snapshot in results) {
                            for (partDoc in snapshot.documents) {
                                val rawStatus = partDoc.get("status")
                                val statusInt = when (rawStatus) {
                                    is Number -> rawStatus.toInt()
                                    is String -> rawStatus.toIntOrNull() ?: 0
                                    else -> 0
                                }
                                if (statusInt >= 2) totalReadJuzCount++
                            }
                        }
                        txtTotalJuzs.text = totalReadJuzCount.toString()
                        saveStatsAndRefreshBadges(userId, completedHatimCount, totalReadJuzCount)
                    }
                } else {
                    txtTotalJuzs.text = "0"
                    saveStatsAndRefreshBadges(userId, completedHatimCount, 0)
                }
            }
            .addOnFailureListener {
                txtTotalHatims.text = "0"
                txtTotalJuzs.text = "0"
                saveStatsAndRefreshBadges(userId, 0, 0)
            }
    }

    private fun setupClickListeners() {
        btnSettings.setOnClickListener {
            context?.let { ctx -> startActivity(Intent(ctx, SettingsActivity::class.java)) }
        }

        // ✅ Çıkış yap gerçekten çıkış (burada signOut doğru)
        btnLogout.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("isGuest", false).apply()

            auth.signOut()

            val intent = Intent(requireContext(), WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        // ✅ Login/Register giderken signOut YOK (misafir hatimler kaybolmasın)
        btnLogin.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(requireContext(), RegisterActivity::class.java))
        }
    }
}
