package com.example.sharedkhatm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment

/**
 * Görüş ve Destek sayfası: Geri Bildirim kartı → FeedbackFragment;
 * Bizi Destekleyenler kartı → aynı sayfada genişleyen detay (Roof Atelier, Chrome Custom Tabs).
 */
class SupportFragment : Fragment(R.layout.fragment_support) {

    private val roofAtelierUrl = "https://www.roofatelier.com/"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = view.findViewById<View>(R.id.supportContentContainer)
        val screenWidthPx = resources.displayMetrics.widthPixels
        val maxWidthPx = (600 * resources.displayMetrics.density).toInt()
        if (screenWidthPx > maxWidthPx) {
            container.layoutParams = (container.layoutParams as ViewGroup.LayoutParams).apply {
                width = maxWidthPx
            }
        }

        view.findViewById<View>(R.id.btnSupportBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.cardSupportFeedback).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                .replace(R.id.fragmentContainer, FeedbackFragment())
                .addToBackStack(null)
                .commit()
        }

        val cardBrand = view.findViewById<View>(R.id.cardSupportBrand)
        val detail = view.findViewById<View>(R.id.supportBrandDetail)
        val iconExpand = view.findViewById<ImageView>(R.id.iconSupportExpand)

        cardBrand.setOnClickListener {
            val isExpanded = detail.visibility == View.VISIBLE
            detail.visibility = if (isExpanded) View.GONE else View.VISIBLE
            iconExpand.rotation = if (isExpanded) 0f else 90f
        }

        view.findViewById<View>(R.id.btnSupportVisitWeb).setOnClickListener {
            openUrl(roofAtelierUrl)
        }
    }

    private fun openUrl(url: String) {
        val uri = Uri.parse(if (url.startsWith("http")) url else "https://$url")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (_: Exception) {
            startActivity(intent)
        }
    }
}
