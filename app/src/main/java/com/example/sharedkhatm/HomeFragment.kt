package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val hatimArrayList = ArrayList<Hatim>()
    private lateinit var hatimAdapter: HatimAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtEmptyState: TextView
    private lateinit var txtHatimCount: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // ✅ fragment_home.xml içinde OLAN view'lar
        txtEmptyState = view.findViewById(R.id.txtEmptyState)
        txtHatimCount = view.findViewById(R.id.txtHatimCount)
        recyclerView = view.findViewById(R.id.recyclerViewHatims)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        hatimAdapter = HatimAdapter(hatimArrayList)
        recyclerView.adapter = hatimAdapter

        // Detaya git
        hatimAdapter.onItemClick = { secilenHatim ->
            val intent = Intent(requireContext(), HatimDetailActivity::class.java)
            intent.putExtra("hatimId", secilenHatim.id)
            intent.putExtra("hatimName", secilenHatim.name)
            intent.putExtra("hatimDesc", secilenHatim.description)
            startActivity(intent)
        }

        // ✅ BUTON KALDIRILDI:
        // btnCreateHatimHub artık fragment_home.xml'de yok.
        // Yeni Hatim Başlat butonu activity_home.xml (FAB) üzerinden yönetilecek.

        getHatimData()
    }

    override fun onResume() {
        super.onResume()
        getHatimData()
    }

    private fun getHatimData() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("hatims")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener

                hatimArrayList.clear()

                if (documents.isEmpty) {
                    txtEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    txtHatimCount.text = "0 / 0 Tamamlandı"
                    hatimAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                txtEmptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                var totalHatim = 0
                var finishedHatim = 0

                for (document in documents) {
                    try {
                        val hatim = document.toObject(Hatim::class.java)
                        hatim.id = document.id
                        hatimArrayList.add(hatim)

                        totalHatim++
                        if (hatim.completedParts >= hatim.totalParts && hatim.totalParts > 0) {
                            finishedHatim++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // En yeni üstte
                hatimArrayList.sortByDescending { it.createdAt }

                txtHatimCount.text = "$finishedHatim / $totalHatim Tamamlandı"
                hatimAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Veri çekilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
