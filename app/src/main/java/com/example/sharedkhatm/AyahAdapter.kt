package com.example.sharedkhatm

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView

class AyahAdapter(private val ayahList: ArrayList<Ayah>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BESMELE = 0
        private const val VIEW_TYPE_AYAH = 1
        /** Payload: sadece highlight güncellenir, layout kayması olmaz */
        val PAYLOAD_PLAYING_STATE = Any()
        /** Payload: ayarlar değişti (text size, mode) */
        private val PAYLOAD_SETTINGS_CHANGE = Any()
    }

    var currentTextSize: Float = 28f
    var isNightMode: Boolean = false
    var currentMode: Int = 0
    var currentPlayingIndex: Int = -1

    // Uthmani font - fallback to default if font not found
    private var uthmaniTypeface: Typeface? = null
    private var fontLoaded: Boolean = false

    fun loadFont(context: android.content.Context) {
        if (fontLoaded) return
        
        try {
            // Önce res/font klasöründen dene (Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val fontRes = context.resources.getIdentifier(
                    "kfgqpc_uthmanic_hafs",
                    "font",
                    context.packageName
                )
                if (fontRes != 0) {
                    uthmaniTypeface = context.resources.getFont(fontRes)
                    fontLoaded = true
                    return
                }
            }
            
            // Assets klasöründen dene
            uthmaniTypeface = android.graphics.Typeface.createFromAsset(
                context.assets,
                "fonts/kfgqpc_uthmanic_hafs.ttf"
            )
            fontLoaded = true
        } catch (e: Exception) {
            try {
                // Assets root'tan dene
                uthmaniTypeface = android.graphics.Typeface.createFromAsset(
                    context.assets,
                    "kfgqpc_uthmanic_hafs.ttf"
                )
                fontLoaded = true
            } catch (e2: Exception) {
                // Font dosyası yoksa serif kullan
                uthmaniTypeface = Typeface.create("serif", Typeface.NORMAL)
                fontLoaded = true
            }
        }
    }

    class AyahViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtArabic: TextView = itemView.findViewById(R.id.txtArabic)
        val txtSurahInfo: TextView = itemView.findViewById(R.id.txtSurahInfo)
        val layoutAyahNumber: LinearLayout = itemView.findViewById(R.id.layoutAyahNumber)
        val txtAyahNumber: TextView = itemView.findViewById(R.id.txtAyahNumber)
    }

    class BesmeleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtBesmele: TextView = itemView.findViewById(R.id.txtBesmele)
    }

    override fun getItemViewType(position: Int): Int {
        if (position >= ayahList.size) return VIEW_TYPE_AYAH
        val ayah = ayahList[position]
        // İlk ayet ve Besmele içeriyorsa Besmele view type
        if (position == 0 && ayah.text.contains("بِسْمِ", ignoreCase = false)) {
            val text = ayah.text.trim()
            // Besmele'yi ayırmak için kontrol et - sadece Besmele ise ayrı göster
            if (text.startsWith("بِسْمِ") && 
                (text.contains("الرَّحْمَٰنِ") || text.contains("الرَّحِيمِ")) &&
                text.length < 60) {
                return VIEW_TYPE_BESMELE
            }
        }
        return VIEW_TYPE_AYAH
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Font'u yükle (ilk kez)
        if (!fontLoaded) {
            loadFont(parent.context)
        }
        
        return when (viewType) {
            VIEW_TYPE_BESMELE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_besmele, parent, false)
                BesmeleViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ayah, parent, false)
                AyahViewHolder(view)
            }
        }
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] === PAYLOAD_PLAYING_STATE) {
            when (holder) {
                is BesmeleViewHolder -> applyPlayingStateOnly(holder.itemView, position == currentPlayingIndex)
                is AyahViewHolder -> applyPlayingStateOnly(holder.itemView, position == currentPlayingIndex)
            }
            return
        }
        onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BesmeleViewHolder -> bindBesmele(holder, position)
            is AyahViewHolder -> bindAyah(holder, position)
        }
    }

    /** Sadece highlight durumunu günceller – metin/layout değişmez, kayma olmaz */
    private fun applyPlayingStateOnly(itemView: View, isPlaying: Boolean) {
        if (isPlaying) {
            itemView.setBackgroundResource(
                if (isNightMode) R.drawable.bg_ayah_playing_dark
                else R.drawable.bg_ayah_playing_light
            )
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun bindBesmele(holder: BesmeleViewHolder, position: Int) {
        val ayah = ayahList[position]
        
        // Besmele metnini ayır
        val text = ayah.text.trim()
        val besmeleText = if (text.startsWith("بِسْمِ")) {
            // Eğer Besmele'den sonra başka metin varsa ayır
            if (text.contains("يَسَ")) {
                text.substringBefore("يَسَ").trim()
            } else if (text.contains("الٓمٓ")) {
                text.substringBefore("الٓمٓ").trim()
            } else if (text.contains("الرَّحِيمِ")) {
                // Sadece Besmele'yi al
                val parts = text.split("الرَّحِيمِ")
                if (parts.size > 1) {
                    (parts[0] + "الرَّحِيمِ").trim()
                } else {
                    text
                }
            } else {
                text
            }
        } else {
            "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
        }

        holder.txtBesmele.text = besmeleText
        holder.txtBesmele.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize * 1.15f)
        
        // Uthmani font uygula
        uthmaniTypeface?.let {
            holder.txtBesmele.typeface = it
        } ?: run {
            holder.txtBesmele.typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        
        // Harekelerin net görünmesi için kritik ayarlar
        holder.txtBesmele.includeFontPadding = false

        // Açık/koyu temada okunabilir renk
        if (isNightMode) {
            holder.txtBesmele.setTextColor(Color.parseColor("#E8E8E8"))
        } else {
            holder.txtBesmele.setTextColor(Color.parseColor("#1A1A1A"))
        }
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        // Highlight – drawable ile sabit görünüm, kayma yok
        applyPlayingStateOnly(holder.itemView, position == currentPlayingIndex)
    }

    private fun bindAyah(holder: AyahViewHolder, position: Int) {
        val ayah = ayahList[position]

        val surahName = ayah.surah?.name ?: "Sûre"
        val surahEnglishName = ayah.surah?.englishName ?: "Surah"

        // Ayet numarasını göster (sadece Arapça modunda)
        if (currentMode == 0) {
            holder.layoutAyahNumber.visibility = View.VISIBLE
            holder.txtAyahNumber.text = toArabicNumbers(ayah.numberInSurah)
            holder.txtSurahInfo.visibility = View.GONE
        } else {
            holder.layoutAyahNumber.visibility = View.GONE
            holder.txtSurahInfo.visibility = View.VISIBLE
            holder.txtSurahInfo.text = "$surahEnglishName - ${ayah.numberInSurah}"
            holder.txtSurahInfo.typeface = Typeface.DEFAULT
        }

        when (currentMode) {
            0 -> {
                // Arapça modu - Mushaf stili
                var ayahText = ayah.text
                
                // Eğer Besmele içeriyorsa ve ilk ayet değilse, Besmele'yi kaldır
                // Ayrıca ilk ayet Besmele view type ise Besmele'yi kaldır
                if ((position > 0 || getItemViewType(0) == VIEW_TYPE_BESMELE) && 
                    ayahText.contains("بِسْمِ", ignoreCase = false)) {
                    ayahText = ayahText.replace("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", "")
                        .replace("بِسْمِ ٱللَّهِ", "")
                        .trim()
                }
                
                holder.txtArabic.text = ayahText
                holder.txtArabic.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    currentTextSize
                )
                
                // Uthmani font uygula - harekeler için kritik
                uthmaniTypeface?.let {
                    holder.txtArabic.typeface = it
                } ?: run {
                    holder.txtArabic.typeface = Typeface.create("serif", Typeface.NORMAL)
                }
                
                // Harekelerin net görünmesi için kritik ayarlar
                holder.txtArabic.includeFontPadding = false
                holder.txtArabic.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                holder.txtArabic.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            }
            1 -> {
                holder.txtArabic.text = ayah.textTurkish ?: "Meal yükleniyor..."
                holder.txtArabic.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    currentTextSize * 0.75f
                )
                holder.txtArabic.typeface = Typeface.DEFAULT
                holder.txtArabic.includeFontPadding = true
            }
            2 -> {
                holder.txtArabic.text =
                    ayah.textTransliteration ?: "Okunuş yükleniyor..."
                holder.txtArabic.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    currentTextSize * 0.75f
                )
                holder.txtArabic.typeface = Typeface.DEFAULT
                holder.txtArabic.includeFontPadding = true
            }
        }

        // Okunan ayet vurgusu: sadece arka plan rengi; metin rengi değişmez, her zaman okunabilir
        applyPlayingStateOnly(holder.itemView, position == currentPlayingIndex)
        holder.txtArabic.alpha = 1f

        // Açık ve koyu temada net okunabilir metin renkleri (sese göre renk yok)
        if (isNightMode) {
            holder.txtArabic.setTextColor(Color.parseColor("#E8E8E8"))
            holder.txtSurahInfo.setTextColor(Color.parseColor("#B0B0B0"))
            holder.txtAyahNumber.setTextColor(Color.parseColor("#B0B0B0"))
        } else {
            holder.txtArabic.setTextColor(Color.parseColor("#1A1A1A"))
            holder.txtSurahInfo.setTextColor(Color.parseColor("#5D4E37"))
            holder.txtAyahNumber.setTextColor(Color.parseColor("#1B5E20"))
        }
    }

    /** Okunan ayet indeksini günceller; sadece highlight değişir, layout kaymaz */
    fun updatePlayingIndex(newIndex: Int) {
        val oldIndex = currentPlayingIndex
        if (oldIndex == newIndex) return
        currentPlayingIndex = newIndex

        if (oldIndex != -1) notifyItemChanged(oldIndex, PAYLOAD_PLAYING_STATE)
        if (newIndex != -1) notifyItemChanged(newIndex, PAYLOAD_PLAYING_STATE)
    }

    private fun toArabicNumbers(number: Int): String {
        return number.toString()
            .replace("0", "٠")
            .replace("1", "١")
            .replace("2", "٢")
            .replace("3", "٣")
            .replace("4", "٤")
            .replace("5", "٥")
            .replace("6", "٦")
            .replace("7", "٧")
            .replace("8", "٨")
            .replace("9", "٩")
    }

    override fun getItemCount(): Int = ayahList.size
}