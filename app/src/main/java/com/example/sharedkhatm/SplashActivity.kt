package com.example.sharedkhatm

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Splash ekranı — tek katman. Başlangıç durumu kodda (büyük translationY) uygulanır,
 * animasyon ilk çerçeve çizildikten sonra logo.post ile başlar. Geçiş yalnızca
 * subtitle withEndAction -> ensureMinDurationThenRoute() ile; onResume vb. çağırmaz.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val PREFS = "AppGlobalPrefs"

    private var splashStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        splashStartTime = SystemClock.uptimeMillis()
        // Beyaz ekran süresini kısaltmak için layout mümkün olan en erken yüklenir
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val title = findViewById<TextView>(R.id.txtAppNameSplash)
        val subtitle = findViewById<TextView>(R.id.txtSloganSplash)

        logo.apply {
            alpha = 0f
            translationY = 150f
        }
        title.apply {
            alpha = 0f
            translationY = 200f
        }
        subtitle.apply {
            alpha = 0f
            translationY = 250f
        }

        // Tek post: görünür yap, ilk çerçevede animasyonu başlat (ekstra frame gecikmesi yok)
        logo.post {
            logo.visibility = View.VISIBLE
            title.visibility = View.VISIBLE
            subtitle.visibility = View.VISIBLE
            logo.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    logo.viewTreeObserver.removeOnPreDrawListener(this)
                    logo.alpha = 0f
                    logo.translationY = 150f
                    title.alpha = 0f
                    title.translationY = 200f
                    subtitle.alpha = 0f
                    subtitle.translationY = 250f
                    startAnimation(logo, title, subtitle)
                    return true
                }
            })
        }

        auth = FirebaseAuth.getInstance()
    }

    private fun startAnimation(logo: ImageView, title: TextView, subtitle: TextView) {
        val interpolator = AccelerateDecelerateInterpolator()
        val mainHandler = Handler(Looper.getMainLooper())

        // ObjectAnimator — View.animate() yerine (OPPO / Android 15'te daha güvenilir)
        val logoAlpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
            duration = 700
            this.setInterpolator(interpolator)
        }
        val logoTy = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 150f, 0f).apply {
            duration = 700
            this.setInterpolator(interpolator)
        }
        val logoSet = AnimatorSet().apply { playTogether(logoAlpha, logoTy) }

        val titleAlpha = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 200
            this.setInterpolator(interpolator)
        }
        val titleTy = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 200f, 0f).apply {
            duration = 600
            startDelay = 200
            this.setInterpolator(interpolator)
        }
        val titleSet = AnimatorSet().apply { playTogether(titleAlpha, titleTy) }

        val subtitleAlpha = ObjectAnimator.ofFloat(subtitle, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 400
            this.setInterpolator(interpolator)
        }
        val subtitleTy = ObjectAnimator.ofFloat(subtitle, View.TRANSLATION_Y, 250f, 0f).apply {
            duration = 600
            startDelay = 400
            this.setInterpolator(interpolator)
        }
        val subtitleSet = AnimatorSet().apply {
            playTogether(subtitleAlpha, subtitleTy)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    ensureMinDurationThenRoute()
                }
            })
        }

        logoSet.start()
        mainHandler.postDelayed({ titleSet.start() }, 50)
        mainHandler.postDelayed({ subtitleSet.start() }, 50)
    }

    private fun ensureMinDurationThenRoute() {
        val minDuration = 1800L
        val elapsed = SystemClock.uptimeMillis() - splashStartTime
        if (elapsed >= minDuration) {
            route()
        } else {
            val remaining = minDuration - elapsed
            window.decorView.postDelayed({
                if (!isFinishing) route()
            }, remaining)
        }
    }

    private fun route() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isGuest = prefs.getBoolean("isGuest", false)
        val user = auth.currentUser

        if (user != null) {
            if (user.isAnonymous) prefs.edit().putBoolean("isGuest", true).apply()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        if (isGuest) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    prefs.edit().putBoolean("isGuest", true).apply()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Misafir girişi başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, WelcomeActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            return
        }

        startActivity(Intent(this, WelcomeActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
