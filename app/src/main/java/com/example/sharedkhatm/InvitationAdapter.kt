package com.example.sharedkhatm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class InvitationAdapter(private val context: Context, private val list: List<InvitationModel>) : BaseAdapter() {

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_invitation, parent, false)

        val item = list[position]

        val txtHatimName = view.findViewById<TextView>(R.id.txtHatimName)
        val txtInviter = view.findViewById<TextView>(R.id.txtInviterName)

        txtHatimName.text = item.hatimName
        txtInviter.text = "Davet Eden: ${item.senderName}"

        return view
    }
}