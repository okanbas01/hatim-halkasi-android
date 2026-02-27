package com.example.sharedkhatm

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView // Kart import edildi

class YasinTabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_yasin_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ARTIK BUTONU DEĞİL, KARTI BULUYORUZ
        val cardYasin = view.findViewById<MaterialCardView>(R.id.cardYasin)

        // Karta tıklanınca çalışır
        cardYasin.setOnClickListener {
            val intent = Intent(requireContext(), ReadJuzActivity::class.java)
            intent.putExtra("surahNumber", 36)
            intent.putExtra("fromYasinTab", true)
            startActivity(intent)
        }
    }
}