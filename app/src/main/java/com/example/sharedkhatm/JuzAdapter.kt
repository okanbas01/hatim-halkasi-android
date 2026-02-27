package com.example.sharedkhatm

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class JuzAdapter(private val juzList: ArrayList<Juz>) :
    RecyclerView.Adapter<JuzAdapter.JuzViewHolder>() {

    var onJuzClick: ((Juz) -> Unit)? = null

    class JuzViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtNumber: TextView = itemView.findViewById(R.id.txtJuzNumber)
        val imgStatus: ImageView = itemView.findViewById(R.id.imgStatus)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardJuz)
        val txtOwner: TextView = itemView.findViewById(R.id.txtOwner) // Yeni ekledik
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JuzViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_juz, parent, false)
        return JuzViewHolder(view)
    }

    override fun onBindViewHolder(holder: JuzViewHolder, position: Int) {
        val juz = juzList[position]

        holder.txtNumber.text = juz.number.toString()

        // --- DURUM MANTIĞI ---
        when (juz.status) {
            0 -> { // BOŞ
                holder.cardView.setCardBackgroundColor(Color.WHITE)
                holder.cardView.strokeColor = Color.parseColor("#E0E0E0")
                holder.txtNumber.setTextColor(Color.parseColor("#2E7D32")) // Yeşil
                holder.imgStatus.setImageResource(android.R.drawable.ic_input_add) // Artı ikonu
                holder.imgStatus.setColorFilter(Color.LTGRAY)

                // İsim Gizli
                holder.txtOwner.visibility = View.GONE
            }
            1 -> { // ALINDI (Okunuyor)
                holder.cardView.setCardBackgroundColor(Color.parseColor("#FFF9C4")) // Açık Sarı
                holder.cardView.strokeColor = Color.parseColor("#FBC02D") // Koyu Sarı Kenar
                holder.txtNumber.setTextColor(Color.BLACK)
                holder.imgStatus.setImageResource(android.R.drawable.ic_menu_edit) // Kalem ikonu
                holder.imgStatus.setColorFilter(Color.parseColor("#F57F17"))

                // İsim Görünür
                holder.txtOwner.visibility = View.VISIBLE
                holder.txtOwner.text = juz.ownerName ?: "Biri"
            }
            2 -> { // TAMAMLANDI
                holder.cardView.setCardBackgroundColor(Color.parseColor("#C8E6C9")) // Açık Yeşil
                holder.cardView.strokeColor = Color.parseColor("#2E7D32")
                holder.txtNumber.setTextColor(Color.parseColor("#1B5E20"))
                holder.imgStatus.setImageResource(R.drawable.ic_check_bold) // Tik ikonu
                holder.imgStatus.setColorFilter(Color.parseColor("#1B5E20"))

                // İsim Görünür
                holder.txtOwner.visibility = View.VISIBLE
                holder.txtOwner.text = juz.ownerName ?: "Biri"
            }
        }

        holder.itemView.setOnClickListener {
            onJuzClick?.invoke(juz)
        }
    }

    override fun getItemCount(): Int {
        return juzList.size
    }
}