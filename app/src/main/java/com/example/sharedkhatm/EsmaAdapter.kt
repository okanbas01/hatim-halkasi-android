package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EsmaAdapter(private val esmaList: List<EsmaName>) :
    RecyclerView.Adapter<EsmaAdapter.EsmaViewHolder>() {

    class EsmaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtArabic: TextView = itemView.findViewById(R.id.txtEsmaArabic)
        val txtName: TextView = itemView.findViewById(R.id.txtEsmaName)
        val txtMeaning: TextView = itemView.findViewById(R.id.txtEsmaMeaning)
        val txtCount: TextView = itemView.findViewById(R.id.txtEsmaCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EsmaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_esma, parent, false)
        return EsmaViewHolder(view)
    }

    override fun onBindViewHolder(holder: EsmaViewHolder, position: Int) {
        val item = esmaList[position]
        holder.txtArabic.text = item.arabic
        holder.txtName.text = "${position + 1}. ${item.name}"
        holder.txtMeaning.text = item.meaning
        holder.txtCount.text = item.count.toString()
    }

    override fun getItemCount() = esmaList.size
}