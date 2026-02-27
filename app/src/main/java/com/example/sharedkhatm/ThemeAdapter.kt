package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView

class ThemeAdapter(
    private val themeList: List<ThemeModel>,
    private val onThemeSelected: (ThemeModel) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    private var selectedPosition = 0

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.cardThemeItem)
        val image: ImageView = itemView.findViewById(R.id.imgThemeThumb)
        // Check image yerine FrameLayout'u alıyoruz
        val selectedOverlay: View = itemView.findViewById(R.id.layoutSelectedState)

        fun bind(theme: ThemeModel, position: Int) {
            Glide.with(itemView.context)
                .load(theme.imageRes)
                .apply(RequestOptions().transform(CenterCrop()))
                .into(image)

            if (position == selectedPosition) {
                card.strokeWidth = 6
                selectedOverlay.visibility = View.VISIBLE // Tik katmanını göster
            } else {
                card.strokeWidth = 0
                selectedOverlay.visibility = View.GONE // Gizle
            }

            itemView.setOnClickListener {
                val previousItem = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousItem)
                notifyItemChanged(selectedPosition)
                onThemeSelected(theme)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_selection, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(themeList[position], position)
    }

    override fun getItemCount() = themeList.size
}