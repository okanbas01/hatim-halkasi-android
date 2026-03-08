package com.example.sharedkhatm

import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DuaAdapter(
    private val duaList: ArrayList<DuaModel>, // Burası ArrayList kalabilir (Fragmenttan geliyor)
    private val currentUserId: String
) : RecyclerView.Adapter<DuaAdapter.DuaViewHolder>() {

    var onAminClick: ((DuaModel) -> Unit)? = null

    class DuaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtText: TextView = itemView.findViewById(R.id.txtDuaText)
        val txtAuthor: TextView = itemView.findViewById(R.id.txtDuaAuthor)
        val txtCount: TextView = itemView.findViewById(R.id.txtAminCount)
        val btnAmin: LinearLayout = itemView.findViewById(R.id.btnAmin)
        val imgAmin: ImageView = itemView.findViewById(R.id.imgAminIcon) // ID'ye dikkat
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DuaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dua, parent, false)
        return DuaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DuaViewHolder, position: Int) {
        val dua = duaList[position]

        holder.txtText.text = dua.text
        holder.txtAuthor.text = dua.authorName
        holder.txtCount.text = "${dua.aminCount} Amin"

        // Null Safety (Güvenlik) Kontrolü
        // likedBy bazen null gelebilir, kontrol edelim
        val likes = dua.likedBy ?: emptyList()
        val isLiked = likes.contains(currentUserId)

        val ctx = holder.itemView.context
        if (isLiked) {
            // Beğenilmiş: sadece kalp kırmızı, yanındaki yazı gri (okunabilir)
            holder.imgAmin.setImageResource(R.drawable.ic_heart_filled)
            holder.imgAmin.setColorFilter(ContextCompat.getColor(ctx, R.color.amin_liked))
            holder.txtCount.setTextColor(ContextCompat.getColor(ctx, R.color.text_grey))
            holder.btnAmin.isEnabled = false
            holder.btnAmin.alpha = 0.6f
        } else {
            // Beğenilmemiş: yeşil el/amin ikonu
            holder.imgAmin.setImageResource(R.drawable.ic_action_amin)
            val greenColor = ContextCompat.getColor(ctx, R.color.amin_btn_text)
            holder.imgAmin.setColorFilter(greenColor)
            holder.txtCount.setTextColor(greenColor)
            holder.btnAmin.isEnabled = true
            holder.btnAmin.alpha = 1.0f
        }

        holder.btnAmin.setOnClickListener {
            holder.btnAmin.isEnabled = false
            onAminClick?.invoke(dua)
        }
    }

    override fun getItemCount(): Int = duaList.size
}