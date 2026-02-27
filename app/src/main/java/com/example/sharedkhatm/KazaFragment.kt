package com.example.sharedkhatm

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

class KazaFragment : Fragment(R.layout.fragment_kaza) {

    // Sayaç değerlerini tutacak harita
    private val kazaCounts = mutableMapOf(
        "sabah" to 0,
        "ogle" to 0,
        "ikindi" to 0,
        "aksam" to 0,
        "yatsi" to 0,
        "vitir" to 0,
        "oruc" to 0
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Geri Butonu
        view.findViewById<View>(R.id.btnBackKaza).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Kayıtlı Verileri Yükle
        loadData()

        // 7 Kartı Tek Tek Ayarla
        setupItem(view.findViewById(R.id.itemSabah), "Sabah Namazı", "sabah")
        setupItem(view.findViewById(R.id.itemOgle), "Öğle Namazı", "ogle")
        setupItem(view.findViewById(R.id.itemIkindi), "İkindi Namazı", "ikindi")
        setupItem(view.findViewById(R.id.itemAksam), "Akşam Namazı", "aksam")
        setupItem(view.findViewById(R.id.itemYatsi), "Yatsı Namazı", "yatsi")
        setupItem(view.findViewById(R.id.itemVitir), "Vitir Namazı", "vitir")
        setupItem(view.findViewById(R.id.itemOruc), "Kaza Orucu", "oruc")
    }

    private fun setupItem(itemView: View, title: String, key: String) {
        val txtName = itemView.findViewById<TextView>(R.id.txtKazaName)
        val txtCount = itemView.findViewById<TextView>(R.id.txtKazaCount)
        val btnMinus = itemView.findViewById<ImageView>(R.id.btnMinus)
        val btnPlus = itemView.findViewById<ImageView>(R.id.btnPlus)

        txtName.text = title
        txtCount.text = kazaCounts[key].toString()

        // Artır
        btnPlus.setOnClickListener {
            val current = kazaCounts[key] ?: 0
            kazaCounts[key] = current + 1
            txtCount.text = kazaCounts[key].toString()
            saveData(key, kazaCounts[key]!!)
        }

        // Azalt
        btnMinus.setOnClickListener {
            val current = kazaCounts[key] ?: 0
            if (current > 0) {
                kazaCounts[key] = current - 1
                txtCount.text = kazaCounts[key].toString()
                saveData(key, kazaCounts[key]!!)
            }
        }
    }

    private fun saveData(key: String, value: Int) {
        val prefs = requireContext().getSharedPreferences("KazaPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(key, value).apply()
    }

    private fun loadData() {
        val prefs = requireContext().getSharedPreferences("KazaPrefs", Context.MODE_PRIVATE)
        for (key in kazaCounts.keys) {
            kazaCounts[key] = prefs.getInt(key, 0)
        }
    }
}