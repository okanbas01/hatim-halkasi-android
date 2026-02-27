package com.example.sharedkhatm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

class SelectFriendsAdapter(private val context: Context, private val users: List<UserModel>) : BaseAdapter() {

    override fun getCount(): Int = users.size
    override fun getItem(position: Int): Any = users[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_select_friend, parent, false)

        val user = users[position]

        val txtName = view.findViewById<TextView>(R.id.txtName)
        val txtUsername = view.findViewById<TextView>(R.id.txtUsername)
        val checkBox = view.findViewById<CheckBox>(R.id.cbSelect)
        val imgProfile = view.findViewById<ImageView>(R.id.imgProfile)

        txtName.text = user.name
        txtUsername.text = "@${user.username}"

        // Checkbox durumunu modelden al (Scroll sorunu olmaması için)
        checkBox.setOnCheckedChangeListener(null) // Listener çakışmasını önle
        checkBox.isChecked = user.isSelected

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            user.isSelected = isChecked
        }

        // Satıra tıklayınca da seçsin
        view.setOnClickListener {
            checkBox.isChecked = !checkBox.isChecked
        }

        return view
    }
}