package com.example.sharedkhatm.hicri

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sharedkhatm.R

/**
 * "Tüm Özel Günler" yatay listesi için adapter. Küçük kart: ikon, isim, ay adı.
 */
class HicriTumOzelAdapter(
    private var items: List<HicriSpecialDayItem>,
    var onItemClick: ((position: Int, item: HicriSpecialDayItem) -> Unit)? = null
) : RecyclerView.Adapter<HicriTumOzelAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hicri_tum_ozel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items.getOrNull(position) ?: return, position)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<HicriSpecialDayItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.itemTumOzelIcon)
        private val name: TextView = itemView.findViewById(R.id.itemTumOzelName)
        private val month: TextView = itemView.findViewById(R.id.itemTumOzelMonth)

        fun bind(item: HicriSpecialDayItem, position: Int) {
            name.text = item.name
            month.text = IslamicCalendarHelper.getHijriMonthName(item.hijriMonthNumber)
            icon.setImageResource(when {
                item.name.contains("Mirac Kandili") -> R.drawable.mirac
                item.name.contains("Berat Kandili") -> R.drawable.berat
                item.name == "Ramazan" || item.name.contains("Ramazan Bayramı") -> R.drawable.ramazan
                item.name.contains("Kurban Bayramı") -> R.drawable.kurban
                item.name.contains("Kadir Gecesi") -> R.drawable.kadir
                item.name.contains("Mevlid Kandili") -> R.drawable.mevlid
                item.name.contains("Regaib Kandili") -> R.drawable.regaib
                item.name == "Üç Ayların Başlangıcı" -> R.drawable.uc_aylar
                item.name == "Hicri Yılbaşı" -> R.drawable.hicri_yilbasi
                item.name == "Aşure Günü" -> R.drawable.asure
                else -> R.drawable.ic_star_selcuk
            })
            itemView.setOnClickListener { onItemClick?.invoke(position, item) }
        }
    }
}
