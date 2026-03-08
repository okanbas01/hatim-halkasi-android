package com.example.sharedkhatm.hicri

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sharedkhatm.R
import com.google.android.material.card.MaterialCardView

class HicriSpecialDayAdapter : ListAdapter<HicriSpecialDayItem, HicriSpecialDayAdapter.ViewHolder>(DiffCallback) {

    /** En yakın (gelecekteki ilk) özel günün listeki indeksi; bu öğe farklı renkte vurgulanır. */
    var nearestIndex: Int = -1

    /** Seçili öğe indeksi; tıklanınca orta alana kaydırılır. */
    var selectedIndex: Int = -1

    var onItemClick: ((position: Int, item: HicriSpecialDayItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hicri_special_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val accentBar: View = itemView.findViewById(R.id.itemHicriAccentBar)
        private val icon: ImageView = itemView.findViewById(R.id.itemHicriIcon)
        private val name: TextView = itemView.findViewById(R.id.itemHicriName)
        private val desc: TextView = itemView.findViewById(R.id.itemHicriDesc)
        private val tagView: TextView = itemView.findViewById(R.id.itemHicriTag)
        private val hijri: TextView = itemView.findViewById(R.id.itemHicriHijri)
        private val gregorian: TextView = itemView.findViewById(R.id.itemHicriGregorian)
        private val daysNum: TextView = itemView.findViewById(R.id.itemHicriDaysNum)
        private val daysLabel: TextView = itemView.findViewById(R.id.itemHicriDaysLabel)

        fun bind(item: HicriSpecialDayItem, position: Int) {
            name.text = item.name
            hijri.text = item.hijriDisplay
            gregorian.text = item.gregorianDisplay

            if (item.description.isNotBlank()) {
                desc.visibility = View.VISIBLE
                desc.text = item.description
            } else {
                desc.visibility = View.GONE
            }

            val isPast = item.daysOffset < 0
            val absDays = kotlin.math.abs(item.daysOffset)
            daysNum.text = absDays.toString()
            daysLabel.text = if (isPast) "gün geçti" else "gün kaldı"

            val isNearest = (position == nearestIndex)
            // Sol çubuk: en yakın gün sarı (accent_gold), diğerleri hep aynı renk
            val accentColor = if (isNearest) R.color.accent_gold else R.color.hicri_accent_secondary
            val tagText = when {
                item.name.contains("Bayramı") || item.name.contains("Bayramı Arefesi") -> "Bayram"
                item.name.contains("Arefe") -> "Özel Gün"
                item.name.contains("Kandil") -> "Kandil"
                item.name == "Oruç ayının ilk günü" || (item.name.startsWith("Ramazan ") && item.name.endsWith(". Gün")) -> "Ramazan"
                item.name == "Hicri Yılbaşı" -> "Yılbaşı"
                item.name == "Aşure Günü" -> "Aşure"
                item.name == "Üç Ayların Başlangıcı" -> "Üç Aylar"
                else -> null
            }
            val color = ContextCompat.getColor(itemView.context, accentColor)
            accentBar.setBackgroundColor(color)
            // celebration ikonu orijinal görünsün, tint uygulanmıyor

            if (tagText != null) {
                tagView.visibility = View.VISIBLE
                tagView.text = tagText
            } else {
                tagView.visibility = View.GONE
            }

            val isSelected = (position == selectedIndex)
            itemView.isSelected = isSelected
            if (card.strokeWidth > 0 != isSelected) {
                card.strokeWidth = if (isSelected) (4 * itemView.resources.displayMetrics.density).toInt() else 0
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.hicri_gradient_start)
            }
            itemView.setOnClickListener { onItemClick?.invoke(position, item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HicriSpecialDayItem>() {
        override fun areItemsTheSame(a: HicriSpecialDayItem, b: HicriSpecialDayItem): Boolean =
            a.name == b.name && a.gregorianDate == b.gregorianDate

        override fun areContentsTheSame(a: HicriSpecialDayItem, b: HicriSpecialDayItem): Boolean = a == b
    }
}
