package com.example.sharedkhatm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.Calendar
import android.text.format.DateFormat

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

    /** Profil ile aynı kaynak (parts): toplam ve bu ayki cüz sayısını users dokümanına yazar. Hedefler listesi bu veriyi kullanır. */
    private fun syncUserReadCountsToFirestore(userId: String, totalReadCount: Int, currentMonthReadCount: Int) {
        val currentMonthKey = DateFormat.format("yyyy-MM", java.util.Date()).toString()
        val updates = mutableMapOf<String, Any>(
            "totalReadCount" to totalReadCount,
            "monthlyReadCount" to currentMonthReadCount
        )
        if (currentMonthReadCount > 0) updates["lastActiveMonth"] = currentMonthKey
        db.collection("users").document(userId).update(updates).addOnFailureListener { /* sessiz; profil sayısı zaten doğru */ }
    }

    private fun calculateStats(userId: String) {
        txtTotalHatims.text = "..."
        txtTotalJuzs.text = "..."

        db.collection("hatims")
            .whereArrayContains("participants", userId)
            .get()
            .addOnSuccessListener { hatimDocuments ->
                if (!isAdded) return@addOnSuccessListener
                // Tamamlanan hatim sayısı (hafif)
                var completedHatimCount = 0
                for (doc in hatimDocuments) {
                    val createdBy = doc.getString("createdBy")
                    if (createdBy != userId) continue
                    val completedParts = doc.getLong("completedParts")?.toInt() ?: 0
                    val totalParts = doc.getLong("totalParts")?.toInt() ?: 30
                    if (completedParts >= totalParts) completedHatimCount++
                }
                txtTotalHatims.text = completedHatimCount.toString()

                val tasks = mutableListOf<Task<QuerySnapshot>>()
                for (doc in hatimDocuments) {
                    tasks.add(doc.reference.collection("parts").whereEqualTo("ownerId", userId).get())
                }

                if (tasks.isNotEmpty()) {
                    Tasks.whenAllSuccess<QuerySnapshot>(tasks).addOnSuccessListener { results ->
                        if (!isAdded) return@addOnSuccessListener
                        viewLifecycleOwner.lifecycleScope.launch {
                            val (total, monthCount) = withContext(Dispatchers.Default) {
                                var totalReadJuzCount = 0
                                var currentMonthReadCount = 0
                                val cal = Calendar.getInstance()
                                val currentYear = cal.get(Calendar.YEAR)
                                val currentMonth = cal.get(Calendar.MONTH)
                                for (snapshot in results) {
                                    for (partDoc in snapshot.documents) {
                                        val rawStatus = partDoc.get("status")
                                        val statusInt = when (rawStatus) {
                                            is Number -> rawStatus.toInt()
                                            is String -> rawStatus.toIntOrNull() ?: 0
                                            else -> 0
                                        }
                                        if (statusInt >= 2) {
                                            totalReadJuzCount++
                                            val updatedAt = partDoc.get("updatedAt")
                                            if (updatedAt is Timestamp) {
                                                val d = updatedAt.toDate()
                                                cal.time = d
                                                if (cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth) {
                                                    currentMonthReadCount++
                                                }
                                            }
                                        }
                                    }
                                }
                                totalReadJuzCount to currentMonthReadCount
                            }
                            if (!isAdded) return@launch
                            txtTotalJuzs.text = total.toString()
                            saveStatsAndRefreshBadges(userId, completedHatimCount, total)
                            syncUserReadCountsToFirestore(userId, total, monthCount)
                        }
                    }
                } else {
                    txtTotalJuzs.text = "0"
                    saveStatsAndRefreshBadges(userId, completedHatimCount, 0)
                    syncUserReadCountsToFirestore(userId, 0, 0)
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
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
