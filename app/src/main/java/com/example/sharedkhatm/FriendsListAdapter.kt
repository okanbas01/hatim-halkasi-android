package com.example.sharedkhatm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class FriendsListAdapter(
    private val context: Context,
    private val friendsList: ArrayList<FriendModel>,
    private val onDeleteClick: (FriendModel) -> Unit // Tıklanınca çalışacak fonksiyon
) : BaseAdapter() {

    override fun getCount(): Int = friendsList.size
    override fun getItem(position: Int): Any = friendsList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_friend_row, parent, false)

        val friend = friendsList[position]

        val txtName = view.findViewById<TextView>(R.id.txtFriendName)
        val txtUsername = view.findViewById<TextView>(R.id.txtFriendUsername)
        val btnDelete = view.findViewById<ImageView>(R.id.btnDeleteFriend)

        txtName.text = friend.name
        txtUsername.text = "(@${friend.username})"

        // Silme butonuna tıklanınca Fragment'a haber ver
        btnDelete.setOnClickListener {
            onDeleteClick(friend)
        }

        return view
    }
}