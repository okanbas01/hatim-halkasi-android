package com.example.sharedkhatm

import android.os.Bundle
import android.view.View
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class SpiritualityFragment : Fragment(R.layout.fragment_spirituality) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Kartlar
        val cardZikirmatik = view.findViewById<CardView>(R.id.cardZikirmatik)
        val cardEsma = view.findViewById<CardView>(R.id.cardEsma)
        val cardDua = view.findViewById<CardView>(R.id.cardDua)
        val cardKaza = view.findViewById<CardView>(R.id.cardKaza)
        val cardQibla = view.findViewById<CardView>(R.id.cardQibla)
        val cardNamaz = view.findViewById<View>(R.id.cardNamazHocasi)
        val cardRadio = view.findViewById<CardView>(R.id.cardRadio)
        val cardFridayMsg = view.findViewById<CardView>(R.id.cardFridayMsg)
        val cardBilgiYarismasi = view.findViewById<CardView>(R.id.cardBilgiYarismasi)
        val cardFeedback = view.findViewById<View>(R.id.cardFeedback)

        // Tıklama Olayları
        cardZikirmatik.setOnClickListener { navigateTo(DhikrFragment()) }
        cardEsma.setOnClickListener { navigateTo(EsmaFragment()) }
        cardDua.setOnClickListener { navigateTo(DuaFragment()) }
        cardKaza.setOnClickListener { navigateTo(KazaFragment()) }
        cardQibla.setOnClickListener { navigateTo(QiblaFragment()) }
        cardNamaz.setOnClickListener { navigateTo(PrayerGuideFragment()) }
        cardRadio.setOnClickListener { navigateTo(RadioPlayerFragment()) }

        cardFridayMsg.setOnClickListener { navigateTo(FridayMessageFragment()) }
        cardBilgiYarismasi.setOnClickListener { navigateTo(QuizFragment()) }
        cardFeedback.setOnClickListener { navigateTo(SupportFragment()) }
    }

    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}