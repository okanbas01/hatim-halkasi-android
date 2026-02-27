package com.example.sharedkhatm

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SurahAdapter(
    private val fullList: List<SurahInfo> // Orijinal tam liste
) : RecyclerView.Adapter<SurahAdapter.SurahViewHolder>() {

    // Ekranda gösterilen filtrelenmiş liste
    private var displayedList: List<SurahInfo> = fullList

    class SurahViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNumber: TextView = view.findViewById(R.id.txtSurahNumber)
        val txtName: TextView = view.findViewById(R.id.txtSurahName)
        val txtDetail: TextView = view.findViewById(R.id.txtSurahDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_surah_row, parent, false)
        return SurahViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        val surah = displayedList[position]

        holder.txtNumber.text = surah.number.toString()
        holder.txtName.text = surah.name
        // DETAY BİLGİSİ: "Mekki • 7 Ayet"
        holder.txtDetail.text = "${surah.type} • ${surah.verseCount} Ayet"

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, ReadJuzActivity::class.java)
            intent.putExtra("surahNumber", surah.number)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = displayedList.size

    // ARAMA FİLTRELEME FONKSİYONU
    fun filterList(query: String) {
        // .trim() ekledik: Baş ve sondaki boşlukları siler.
        val cleanQuery = query.trim()

        displayedList = if (cleanQuery.isEmpty()) {
            fullList
        } else {
            fullList.filter {
                // Hem Türkçe karakter duyarlılığını yönetir hem de boşlukları affeder
                it.name.contains(cleanQuery, ignoreCase = true) ||
                        it.number.toString() == cleanQuery
            }
        }
        notifyDataSetChanged()
    }
}