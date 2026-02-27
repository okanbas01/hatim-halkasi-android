package com.example.sharedkhatm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class TasbihView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beadRadius = 40f // Taş büyüklüğü
    private val beadGap = 15f    // Taşlar arası boşluk
    private val totalDistance = (beadRadius * 2) + beadGap

    // Animasyon Ofseti (Kayma miktarı)
    private var offsetY = 0f

    init {
        // İp Rengi (Krem/Kahve tonu)
        stringPaint.color = Color.parseColor("#8D6E63")
        stringPaint.strokeWidth = 6f
        stringPaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val height = height.toFloat()

        // 1. İPİ ÇİZ (Yukarıdan aşağıya düz çizgi)
        canvas.drawLine(centerX, 0f, centerX, height, stringPaint)

        // 2. TAŞLARI ÇİZ
        // Ekranı dolduracak kadar taş çiziyoruz
        val beadCount = (height / totalDistance).toInt() + 2

        for (i in -1..beadCount) {
            // Taşın Y konumu: (Sıra * Mesafe) + Animasyon Kayması
            val cy = (i * totalDistance) + offsetY + (totalDistance / 2)

            // 3D Hissiyatı İçin Gradyan (Işık efekti)
            // Koyu Yeşil (#1B5E20) ile Açık Yeşil (#66BB6A) arası geçiş
            val shader = RadialGradient(
                centerX - (beadRadius / 3), // Işık kaynağı biraz sol üstte
                cy - (beadRadius / 3),
                beadRadius,
                intArrayOf(
                    Color.parseColor("#66BB6A"), // Parlak nokta (Açık Yeşil)
                    Color.parseColor("#2E7D32"), // Orta ton
                    Color.parseColor("#1B5E20")  // Gölge (Koyu Yeşil)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )

            beadPaint.shader = shader
            canvas.drawCircle(centerX, cy, beadRadius, beadPaint)
        }
    }

    // Bu fonksiyonu çağırdığında taşlar 1 birim aşağı kayar
    fun pullBead() {
        val animator = ValueAnimator.ofFloat(0f, totalDistance)
        animator.duration = 200 // Hız (Milisaniye)
        animator.interpolator = DecelerateInterpolator() // Yavaşlayarak durma efekti

        animator.addUpdateListener { animation ->
            offsetY = animation.animatedValue as Float
            invalidate() // Ekranı tekrar çiz
        }

        animator.start()
    }
}