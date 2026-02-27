package com.example.sharedkhatm

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream

/**
 * Rozet unlock dialog - hafif, performans odaklı
 * Tekrar gösterme kontrolü var
 */
object BadgeUnlockDialog {

    private const val APP_SHARE_LINK = "Hatim Halkası https://play.google.com/store/apps/details?id=com.hatimhalkasi.app"

    /**
     * Rozet kazanıldığında göster - sadece 1 kere
     */
    fun showUnlockDialog(context: Context, badge: BadgeModel) {
        // Daha önce gösterildi mi kontrol et
        if (BadgeManager.isBadgeShown(context, badge.id)) {
            return // Tekrar gösterme
        }
        
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_badge_unlock, null)
        dialog.setContentView(view)
        
        val txtIcon = view.findViewById<TextView>(R.id.txtUnlockIcon)
        val txtTitle = view.findViewById<TextView>(R.id.txtUnlockTitle)
        val txtBadgeName = view.findViewById<TextView>(R.id.txtUnlockBadgeName)
        val txtMessage = view.findViewById<TextView>(R.id.txtUnlockMessage)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnShareBadge)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseBadge)
        
        txtIcon.text = badge.icon
        txtBadgeName.text = badge.name
        txtMessage.text = badge.description
        
        btnShare.setOnClickListener {
            dialog.dismiss()
            shareBadge(context, badge)
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            // Gösterildi olarak işaretle
            BadgeManager.markBadgeAsShown(context, badge.id)
        }
        
        dialog.show()
    }
    
    /**
     * Rozet paylaşımı - hafif bitmap oluşturma
     */
    private fun shareBadge(context: Context, badge: BadgeModel) {
        try {
            // Basit bitmap oluştur - hafif
            val bitmap = createBadgeShareImage(context, badge)
            
            // Geçici dosyaya kaydet
            val file = File(context.cacheDir, "badge_share_${badge.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            
            // Share intent
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val shareText = "${badge.name} rozetini kazandım! 🌿\n\n$APP_SHARE_LINK"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Rozetini Paylaş"))
        } catch (e: Exception) {
            // Hata durumunda sadece text paylaş
            val shareText = "${badge.icon} ${badge.name} rozetini kazandım! ${badge.description}\n\n$APP_SHARE_LINK"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Rozetini Paylaş"))
        }
    }
    
    /**
     * Rozet paylaşım görseli - minimal bitmap (basit, performans odaklı)
     */
    private fun createBadgeShareImage(context: Context, badge: BadgeModel): Bitmap {
        // Küçük bitmap - performans için (düşük çözünürlük)
        val width = 600
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565) // ARGB_8888 yerine RGB_565 (daha hafif)
        val canvas = Canvas(bitmap)
        
        // Arka plan - krem tonu
        canvas.drawColor(Color.parseColor("#FFF8E1"))
        
        // Not: Gerçek uygulamada TextView'ı bitmap'e çevirmek için View.draw() kullanılabilir
        // ama performans için şimdilik basit bitmap yeterli
        // İleride geliştirilebilir
        
        return bitmap
    }
}
