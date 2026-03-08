package com.example.sharedkhatm

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper

/**
 * Dark/Light mode toggle: Lottie Switch.json + ThemeManager.
 * Layout’ta FrameLayout; Lottie view kod ile eklenir. Tıklamada crash önlenir, animasyon görünür.
 */
object ThemeToggleHelper {

    private const val FRAME_LIGHT_END = 60
    private const val FRAME_DARK_END = 120
    private const val RECREATE_DELAY_MS = 500L
    private const val LOTTIE_CLASS = "com.airbnb.android.lottie.LottieAnimationView"
    private const val MIN_MEMORY_CLASS_FOR_LOTTIE = 128

    fun setup(view: View, lottieId: Int, onRecreate: () -> Unit, fallbackIconId: Int = -1) {
        val container = view.findViewById<View>(lottieId) as? FrameLayout ?: return
        val ctx = view.context
        val fallbackIcon = if (fallbackIconId != -1) view.findViewById<View>(fallbackIconId) else null
        if (!isMemorySufficientForLottie(ctx)) {
            fallbackSetup(container, fallbackIcon, ctx, onRecreate)
            return
        }
        container.post {
            if (!container.isAttachedToWindow) return@post
            val lottieAny = createLottieView(ctx) ?: run {
                fallbackSetup(container, fallbackIcon, ctx, onRecreate)
                return@post
            }
            val lottieView = lottieAny as? View ?: run {
                fallbackSetup(container, fallbackIcon, ctx, onRecreate)
                return@post
            }
            lottieView.alpha = 1f
            lottieView.setBackgroundColor(Color.TRANSPARENT)
            lottieView.elevation = 4f
            container.setBackgroundColor(Color.TRANSPARENT)
            container.addView(lottieView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            fallbackIcon?.visibility = android.view.View.GONE
            setupLottie(lottieAny, container, ctx, onRecreate)
        }
    }

    private fun isMemorySufficientForLottie(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            (am?.memoryClass ?: 0) >= MIN_MEMORY_CLASS_FOR_LOTTIE
        } catch (_: Exception) {
            true
        }
    }

    private fun createLottieView(context: Context): Any? {
        return try {
            val clazz = Class.forName(LOTTIE_CLASS)
            val ctor = clazz.getConstructor(Context::class.java)
            ctor.newInstance(context)
        } catch (e: Exception) {
            null
        }
    }

    private fun setupLottie(lottie: Any, container: View, ctx: Context, onRecreate: () -> Unit) {
        val clazz = lottie.javaClass
        val lottieView = lottie as View

        try {
            clazz.getMethod("setAnimation", String::class.java).invoke(lottie, "Switch.json")
            // OOM/ANR: donanım hızlandırmalı render (performans)
            try {
                val renderModeClass = Class.forName("com.airbnb.android.lottie.RenderMode")
                val hard = (renderModeClass.enumConstants as? Array<*>)?.firstOrNull { (it as? Enum<*>)?.name == "HARDWARE" }
                if (hard != null) clazz.getMethod("setRenderMode", renderModeClass).invoke(lottie, hard)
            } catch (_: Throwable) { }
        } catch (_: Throwable) {
            val fallback = if (container is ViewGroup && container.childCount > 0) container.getChildAt(0) else null
            fallbackSetup(container, fallback, ctx, onRecreate)
            return
        }
        try {
            clazz.getMethod("setRepeatCount", Int::class.javaPrimitiveType).invoke(lottie, 0)
        } catch (_: Throwable) {
            try { clazz.getMethod("setRepeatMode", Int::class.javaPrimitiveType).invoke(lottie, 1) } catch (_: Throwable) { }
        }
        lottieView.isClickable = false
        lottieView.isFocusable = false

        val isDark = ThemeManager.isDarkMode(ctx)
        try {
            val setMinMax = clazz.getMethod("setMinAndMaxFrame", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val setFrame = clazz.getMethod("setFrame", Int::class.javaPrimitiveType)
            setMinMax.invoke(lottie, if (isDark) FRAME_LIGHT_END else 0, if (isDark) FRAME_DARK_END else FRAME_LIGHT_END)
            setFrame.invoke(lottie, if (isDark) FRAME_LIGHT_END else 0)
        } catch (_: Throwable) {
            val fallback = if (container is ViewGroup && container.childCount > 0) container.getChildAt(0) else null
            fallbackSetup(container, fallback, ctx, onRecreate)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        container.setOnClickListener {
            try {
                val animating = try { clazz.getMethod("isAnimating").invoke(lottie) == true } catch (_: Throwable) { false }
                if (animating) return@setOnClickListener

                val nowDark = ThemeManager.isDarkMode(ctx)
                val targetDark = !nowDark
                val setMinMax = clazz.getMethod("setMinAndMaxFrame", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                val setFrame = clazz.getMethod("setFrame", Int::class.javaPrimitiveType)

                if (targetDark) {
                    setMinMax.invoke(lottie, 0, FRAME_LIGHT_END)
                    setFrame.invoke(lottie, 0)
                } else {
                    setMinMax.invoke(lottie, FRAME_LIGHT_END, FRAME_DARK_END)
                    setFrame.invoke(lottie, FRAME_LIGHT_END)
                }
                clazz.getMethod("playAnimation").invoke(lottie)

                handler.postDelayed({
                    try {
                        ThemeManager.setDarkMode(ctx, targetDark)
                        safeRecreate(container, onRecreate)
                    } catch (_: Throwable) { }
                }, RECREATE_DELAY_MS)
            } catch (_: Throwable) {
                try {
                    ThemeManager.setDarkMode(ctx, !ThemeManager.isDarkMode(ctx))
                    handler.postDelayed({ safeRecreate(container, onRecreate) }, 250L)
                } catch (_: Throwable) { }
            }
        }
    }

    private fun fallbackSetup(container: View, fallbackIcon: View?, ctx: Context, onRecreate: () -> Unit) {
        container.isClickable = true
        container.isFocusable = true
        if (fallbackIcon is android.widget.ImageView) {
            fallbackIcon.visibility = android.view.View.VISIBLE
            fallbackIcon.setImageResource(if (ThemeManager.isDarkMode(ctx)) R.drawable.ic_read_sun else R.drawable.ic_read_moon)
            fallbackIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_gold))
        }
        val handler = Handler(Looper.getMainLooper())
        container.setOnClickListener {
            try {
                val context = container.context
                val targetDark = !ThemeManager.isDarkMode(context)
                ThemeManager.setDarkMode(context, targetDark)
                handler.postDelayed({ safeRecreate(container, onRecreate) }, 300L)
            } catch (_: Throwable) { }
        }
    }

    /** recreate'i bir sonraki mesaj döngüsünde çağırır; tıklama/Handler yığınından çıkarak ANR riskini azaltır. */
    private fun safeRecreate(container: View, onRecreate: () -> Unit) {
        container.post {
            try { onRecreate() } catch (_: Throwable) { }
        }
    }
}
