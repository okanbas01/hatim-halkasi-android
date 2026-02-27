package com.example.sharedkhatm

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.max

class QuranHubFragment : Fragment(R.layout.fragment_quran_hub) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var badgeInvitation: CardView
    private var invitationListener: ListenerRegistration? = null

    private var pageCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutQuran)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerQuran)
        val btnInvite = view.findViewById<FrameLayout>(R.id.layoutHeaderInvitation)
        badgeInvitation = view.findViewById(R.id.badgeHeaderInvitation)
        val btnCreateHatim = view.findViewById<ExtendedFloatingActionButton>(R.id.btnCreateHatimHub)

        // ✅ ADAPTER (4 sekme)
        val adapter = QuranPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Hatimlerim"
                1 -> tab.text = "Sureler"
                2 -> tab.text = "Yasin-i Şerif"
                3 -> tab.text = "Hedeflerim"
            }
        }.attach()

        // ✅ Sadece Hatimlerim sekmesinde göster
        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 0) btnCreateHatim.show() else btnCreateHatim.hide()
            }
        }
        viewPager.registerOnPageChangeCallback(pageCallback!!)

        // ✅ Buton click
        btnCreateHatim.setOnClickListener {
            startActivity(Intent(requireContext(), CreateHatimActivity::class.java))
        }

        // ✅ Davet click
        btnInvite.setOnClickListener {
            val user = auth.currentUser
            if (user != null && user.isAnonymous) {
                showSignUpDialog()
            } else if (user != null) {
                startActivity(Intent(requireContext(), HatimInvitationsActivity::class.java))
            }
        }

        // ✅ Davet badge
        val user = auth.currentUser
        if (user != null && !user.isAnonymous) {
            listenToInvitations(user.uid)
        }

        // ✅ Açılışta sekme yönlendirme (senin mevcut mantık)
        val prefs = requireContext().getSharedPreferences("AppNavigation", Context.MODE_PRIVATE)
        when {
            prefs.getBoolean("openSurahTab", false) -> {
                viewPager.post { viewPager.currentItem = 1 }
                prefs.edit().putBoolean("openSurahTab", false).apply()
            }
            prefs.getBoolean("openYasinTab", false) -> {
                viewPager.post { viewPager.currentItem = 2 }
                prefs.edit().putBoolean("openYasinTab", false).apply()
            }
        }

        // ✅ Kritik: ViewPager padding'ine dokunmuyoruz!
        // Sadece FAB'i menünün üstüne dinamik taşıyoruz.
        adjustFabOnly(btnCreateHatim)
    }

    private fun adjustFabOnly(fab: ExtendedFloatingActionButton) {
        view?.doOnLayout {
            val root = requireView()
            val navCard = requireActivity().findViewById<View?>(R.id.navCard) ?: return@doOnLayout

            // root ve navCard konumlarını window'a göre al
            val rootLoc = IntArray(2)
            val navLoc = IntArray(2)
            root.getLocationInWindow(rootLoc)
            navCard.getLocationInWindow(navLoc)

            val navTopInRoot = navLoc[1] - rootLoc[1]          // navCard'ın root içindeki Y konumu
            val spaceFromBottomToNavTop = root.height - navTopInRoot  // root bottom -> navCard top mesafe

            val gapAboveNav = dp(6) // FAB ile navCard arasında boşluk (6-10 arası deneyebilirsin)

            fab.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = spaceFromBottomToNavTop + gapAboveNav
            }

            fab.translationY = 0f
            fab.requestLayout()
        }
    }


    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun listenToInvitations(userId: String) {
        val query = db.collection("invitations")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "pending")

        invitationListener = query.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            badgeInvitation.visibility =
                if (snapshots != null && !snapshots.isEmpty) View.VISIBLE else View.GONE
        }
    }

    private fun showSignUpDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_guest_signup)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.findViewById<View>(R.id.btnDialogRegister).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(requireContext(), RegisterActivity::class.java))
        }
        dialog.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        invitationListener?.remove()
        pageCallback?.let {
            view?.findViewById<ViewPager2>(R.id.viewPagerQuran)?.unregisterOnPageChangeCallback(it)
        }
        pageCallback = null
    }
}

class QuranPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> FreeReadingFragment()
            2 -> YasinTabFragment()
            3 -> GoalsFragment()
            else -> HomeFragment()
        }
    }
}
