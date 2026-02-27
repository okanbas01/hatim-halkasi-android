package com.example.sharedkhatm

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream

class FridayMessageFragment : Fragment(R.layout.fragment_friday_message) {

    private lateinit var layoutExportArea: View
    private lateinit var imgBackgroundPreview: ImageView
    private lateinit var tvMessagePreview: TextView
    private lateinit var etCustomMessage: TextInputEditText
    private lateinit var inputLayoutMessage: TextInputLayout
    private lateinit var rvThemes: RecyclerView
    private lateinit var progressBarThemeLoading: ProgressBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // View Bağlantıları
        layoutExportArea = view.findViewById(R.id.layoutExportArea)
        imgBackgroundPreview = view.findViewById(R.id.imgBackgroundPreview)
        tvMessagePreview = view.findViewById(R.id.tvMessagePreview)
        etCustomMessage = view.findViewById(R.id.etCustomMessage)
        inputLayoutMessage = view.findViewById(R.id.tilCustomMessage)
        rvThemes = view.findViewById(R.id.rvThemes)
        progressBarThemeLoading = view.findViewById(R.id.progressBarThemeLoading)

        // ✅ Input dışına dokununca klavye kapansın (kullanıcı dostu)
        view.setOnClickListener {
            hideKeyboardAndClearFocus()
        }

        // Geri Butonu
        val imgBack = view.findViewById<ImageView>(R.id.imgBack)
        imgBack.setOnClickListener {
            hideKeyboardAndClearFocus()
            parentFragmentManager.popBackStack()
        }

        // Butonlar
        val btnMsg1 = view.findViewById<Button>(R.id.btnMsg1)
        val btnMsg2 = view.findViewById<Button>(R.id.btnMsg2)
        val btnMsg3 = view.findViewById<Button>(R.id.btnMsg3)
        val btnShare = view.findViewById<Button>(R.id.btnShare)

        // Hazır Mesajlar
        btnMsg1.setOnClickListener { updateText("Hayırlı Cumalar.\nAllah dualarımızı kabul etsin.") }
        btnMsg2.setOnClickListener { updateText("Kandiliniz Mübarek Olsun.\nDualarda buluşmak ümidiyle.") }
        btnMsg3.setOnClickListener { updateText("Allah'ım, gönlümüzden geçenleri hakkımızda hayırlı eyle.") }

        // Yazı Değişimi Dinleyicisi
        etCustomMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                inputLayoutMessage.isErrorEnabled = false
                if (!s.isNullOrEmpty()) {
                    tvMessagePreview.text = s.toString()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ✅ Done / Enter basınca klavye kapansın
        etCustomMessage.setOnEditorActionListener { v, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isDone || isEnter) {
                hideKeyboardAndClearFocus()
                true
            } else {
                false
            }
        }

        // --- TEMA LİSTESİ ---
        val themeList = ArrayList<ThemeModel>()

        // 1. Manuel Temalar
        themeList.add(ThemeModel(R.drawable.bg_friday_theme_emerald, true))
        themeList.add(ThemeModel(R.drawable.bg_friday_theme_gold, false))

        // 2. Otomatik Resim Yükleme
        val context = requireContext()
        for (i in 1..30) {
            val imageName = "bg_theme_$i"
            val resId = context.resources.getIdentifier(imageName, "drawable", context.packageName)
            if (resId != 0) {
                themeList.add(ThemeModel(resId, true))
            }
        }

        // RecyclerView Kurulumu
        rvThemes.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvThemes.adapter = ThemeAdapter(themeList) { selectedTheme ->

            progressBarThemeLoading.visibility = View.VISIBLE

            Glide.with(this)
                .load(selectedTheme.imageRes)
                .placeholder(imgBackgroundPreview.drawable)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBarThemeLoading.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBarThemeLoading.visibility = View.GONE
                        return false
                    }
                })
                .centerCrop()
                .into(imgBackgroundPreview)

            if (selectedTheme.isTextWhite) {
                tvMessagePreview.setTextColor(Color.WHITE)
                tvMessagePreview.setShadowLayer(15f, 0f, 0f, Color.BLACK)
            } else {
                tvMessagePreview.setTextColor(Color.parseColor("#1B5E20"))
                tvMessagePreview.setShadowLayer(0f, 0f, 0f, 0)
            }
        }

        // --- PAYLAŞ BUTONU ---
        btnShare.setOnClickListener {
            hideKeyboardAndClearFocus()

            val messageToCheck = etCustomMessage.text?.toString().orEmpty()

            if (ProfanityFilter.containsBadWords(messageToCheck)) {
                inputLayoutMessage.error = "Lütfen üslubumuza uygun ifadeler kullanalım."
                inputLayoutMessage.isErrorEnabled = true
                Toast.makeText(requireContext(), "Mesajınızda uygunsuz ifadeler bulundu.", Toast.LENGTH_SHORT).show()
            } else {
                inputLayoutMessage.isErrorEnabled = false
                shareCardAsImage()
            }
        }
    }

    private fun hideKeyboardAndClearFocus() {
        try {
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(requireView().windowToken, 0)
            etCustomMessage.clearFocus()
        } catch (_: Exception) {}
    }

    private fun updateText(msg: String) {
        tvMessagePreview.text = msg
        etCustomMessage.setText("")
        inputLayoutMessage.isErrorEnabled = false
        hideKeyboardAndClearFocus()
    }

    private fun shareCardAsImage() {
        val bitmap = getBitmapFromView(layoutExportArea)
        if (bitmap != null) {
            shareBitmap(bitmap)
        } else {
            Toast.makeText(requireContext(), "Resim oluşturulamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBitmapFromView(view: View): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()

            val stream = FileOutputStream("$cachePath/share_image.png")
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.close()

            val newFile = File(cachePath, "share_image.png")
            val contentUri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                newFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, requireContext().contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/jpeg"
            }
            startActivity(Intent.createChooser(shareIntent, "Sevdiklerinle Paylaş"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Paylaşım hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
