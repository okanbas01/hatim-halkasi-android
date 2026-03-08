package com.example.sharedkhatm

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private val leaderList = ArrayList<LeaderUser>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etGoal = view.findViewById<EditText>(R.id.etPageGoal)
        val btnSave = view.findViewById<Button>(R.id.btnSaveGoal)
        val progressGoal = view.findViewById<ProgressBar>(R.id.progressGoal)
        val txtGoalStatus = view.findViewById<TextView>(R.id.txtGoalStatus)

        // Mevcut durumu yükle (EditText'i doldurma)
        updateProgressUI(etGoal, progressGoal, txtGoalStatus, fillInput = false)

        btnSave.setOnClickListener {
            val userId = auth.currentUser?.uid
            if (userId.isNullOrBlank()) {
                Toast.makeText(context, "Kullanıcı bulunamadı.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goalStr = etGoal.text?.toString().orEmpty().trim()
            if (goalStr.isEmpty()) {
                etGoal.error = "Bir sayı giriniz"
                return@setOnClickListener
            }

            val goal = goalStr.toIntOrNull()
            if (goal == null) {
                etGoal.error = "Geçerli bir sayı giriniz"
                return@setOnClickListener
            }

            if (goal < 1) {
                etGoal.error = "En az 1 Cüz hedeflemelisiniz"
                return@setOnClickListener
            }

            // Hedefi kaydet
            val prefs = requireContext().getSharedPreferences("UserGoal_$userId", Context.MODE_PRIVATE)
            prefs.edit().putInt("dailyJuzGoal", goal).apply()

            Toast.makeText(context, "Günlük hedef kaydedildi!", Toast.LENGTH_SHORT).show()

            // UI güncelle (inputta değer kalsın)
            updateProgressUI(etGoal, progressGoal, txtGoalStatus, fillInput = true)

            /**
             * KRİTİK: Hedef kaydedilince 21:00 alarmını (switch açıksa) yeniden kur.
             * - notif_goal switch kapalıysa zaten kurmaz.
             * - exact alarm / bildirim izni yoksa zaten kurmaz.
             */
            DailyAlarmScheduler.scheduleGoalIfEnabled(requireContext())
        }

        // --- LİDERLİK TABLOSU ---
        val recyclerLeader = view.findViewById<RecyclerView>(R.id.recyclerLeaderboard)
        val ctx = context ?: return
        recyclerLeader.layoutManager = LinearLayoutManager(ctx)
        leaderboardAdapter = LeaderboardAdapter(leaderList)
        recyclerLeader.adapter = leaderboardAdapter

        loadLeaderboard()
    }

    override fun onResume() {
        super.onResume()
        // Geri gelince sadece barı güncelle, inputa dokunma
        if (!isAdded) return
        view?.let {
            val etGoal = it.findViewById<EditText>(R.id.etPageGoal)
            val progressGoal = it.findViewById<ProgressBar>(R.id.progressGoal)
            val txtGoalStatus = it.findViewById<TextView>(R.id.txtGoalStatus)
            if (etGoal != null && progressGoal != null && txtGoalStatus != null) {
                updateProgressUI(etGoal, progressGoal, txtGoalStatus, fillInput = false)
            }
        }
    }

    private fun updateProgressUI(
        etGoal: EditText,
        progressBar: ProgressBar,
        txtStatus: TextView,
        fillInput: Boolean
    ) {
        val userId = auth.currentUser?.uid ?: run {
            txtStatus.text = "Hedef için giriş yapın."
            return
        }
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("UserGoal_$userId", Context.MODE_PRIVATE)

        // Tarih kontrolü (gün değiştiyse sıfırla)
        val today = android.text.format.DateFormat.format("yyyyMMdd", java.util.Date()).toString()
        val lastDate = prefs.getString("lastReadDate", "")

        if (today != lastDate) {
            prefs.edit()
                .putInt("juzReadToday", 0)
                .putString("lastReadDate", today)
                .apply()
        }

        val goal = prefs.getInt("dailyJuzGoal", 0) // 0 => hedef yok
        val read = prefs.getInt("juzReadToday", 0)

        // Eğer kaydedilmiş hedef varsa ve fillInput true ise kutuyu doldur
        if (goal > 0 && fillInput) {
            etGoal.setText(goal.toString())
        } else if (!fillInput && goal > 0) {
            // Sayfa ilk açıldığında hedef varsa hint
            etGoal.hint = "Hedefiniz: $goal Cüz"
        }

        if (goal > 0) {
            progressBar.max = goal
            progressBar.progress = read
            val percent = (read * 100) / goal
            txtStatus.text = "%$percent Tamamlandı ($read / $goal Cüz)"
        } else {
            progressBar.progress = 0
            txtStatus.text = "Henüz hedef belirlemediniz."

            // İstersen hedef yoksa goal alarmını iptal et (güvenli)
            DailyAlarmScheduler.cancelGoal(ctx)
        }
    }

    /** Bu ay en çok okuyanlar. Sıralama/işleme arka planda (düşük model telefonlarda ANR önlemi). */
    private fun loadLeaderboard() {
        val currentMonthKey = android.text.format.DateFormat.format("yyyy-MM", java.util.Date()).toString()
        db.collection("users")
            .whereEqualTo("lastActiveMonth", currentMonthKey)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                viewLifecycleOwner.lifecycleScope.launch {
                    val tempList = withContext(Dispatchers.Default) {
                        documents.documents.mapNotNull { doc ->
                            val name = doc.getString("name")
                            val count = doc.getLong("monthlyReadCount") ?: 0L
                            if (name != null && count > 0) LeaderUser(maskName(name), count) else null
                        }.sortedByDescending { it.count }.take(10)
                    }
                    if (!isAdded) return@launch
                    leaderList.clear()
                    leaderList.addAll(tempList)
                    leaderboardAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                leaderList.clear()
                leaderboardAdapter.notifyDataSetChanged()
            }
    }

    private fun maskName(fullName: String): String {
        val parts = fullName.split(" ")
        val maskedParts = parts.map { word ->
            if (word.length > 2) "${word.substring(0, 2)}***" else "$word***"
        }
        return maskedParts.joinToString(" ")
    }
}
