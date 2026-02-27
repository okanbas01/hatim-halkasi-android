package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator

class HatimAdapter(private val hatimList: ArrayList<Hatim>) :
    RecyclerView.Adapter<HatimAdapter.HatimViewHolder>() {

    var onItemClick: ((Hatim) -> Unit)? = null

    class HatimViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtItemTitle)
        val txtDesc: TextView = itemView.findViewById(R.id.txtItemDesc)
        // Yeni eklediğimiz alanlar:
        val txtStatusText: TextView = itemView.findViewById(R.id.txtItemStatusText)
        val progressCircle: CircularProgressIndicator = itemView.findViewById(R.id.progressCircle)
        val txtProgressPercent: TextView = itemView.findViewById(R.id.txtProgressPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HatimViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hatim, parent, false)
        return HatimViewHolder(view)
    }

    override fun onBindViewHolder(holder: HatimViewHolder, position: Int) {
        val hatim = hatimList[position]

        holder.txtTitle.text = hatim.name
        holder.txtDesc.text = hatim.description

        // Yüzde Hesaplama
        val total = if (hatim.totalParts > 0) hatim.totalParts else 30
        val completed = hatim.completedParts
        val progress = ((completed.toFloat() / total.toFloat()) * 100).toInt()

        // Progress Bar'a Veriyi Bas
        holder.progressCircle.progress = progress
        holder.txtProgressPercent.text = "%$progress"
        holder.txtStatusText.text = "$completed / $total Cüz Tamamlandı"

        // Hatim Bitti mi? Rengi değiştir
        if (progress == 100) {
            holder.txtStatusText.text = "Hatim Tamamlandı! 🤲"
            holder.txtStatusText.setTextColor(holder.itemView.context.getColor(R.color.primary_green))
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(hatim)
        }
    }

    override fun getItemCount(): Int {
        return hatimList.size
    }
}