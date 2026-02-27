package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.Keep
import androidx.recyclerview.widget.RecyclerView
@Keep
data class LeaderUser(val name: String, val count: Long)

class LeaderboardAdapter(private val leaderList: ArrayList<LeaderUser>) :
    RecyclerView.Adapter<LeaderboardAdapter.LeaderViewHolder>() {

    class LeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtRank: TextView = itemView.findViewById(R.id.txtRank)
        val txtName: TextView = itemView.findViewById(R.id.txtLeaderName)
        val txtCount: TextView = itemView.findViewById(R.id.txtReadCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leader, parent, false)
        return LeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderViewHolder, position: Int) {
        val user = leaderList[position]

        holder.txtRank.text = "${position + 1}."
        holder.txtName.text = user.name
        holder.txtCount.text = user.count.toString()
    }

    override fun getItemCount(): Int = leaderList.size
}