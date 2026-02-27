package com.example.sharedkhatm

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Rozet adapter - performans odaklı
 * Stable ID, payload kullanımı, minimal allocation
 */
class BadgeAdapter(
    private val badges: List<BadgeModel>,
    private val unlockedBadges: Set<String>
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    var onBadgeClick: ((BadgeModel) -> Unit)? = null

    class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardBadge: MaterialCardView = itemView.findViewById(R.id.cardBadge)
        val txtIcon: TextView = itemView.findViewById(R.id.txtBadgeIcon)
        val txtName: TextView = itemView.findViewById(R.id.txtBadgeName)
        val txtDescription: TextView = itemView.findViewById(R.id.txtBadgeDescription)
        val viewLocked: View = itemView.findViewById(R.id.viewLocked)
    }

    override fun getItemId(position: Int): Long = badges[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badges[position]
        val isUnlocked = unlockedBadges.contains(badge.id)

        holder.txtIcon.text = badge.icon
        holder.txtName.text = badge.name
        holder.txtDescription.text = badge.description

        // Kilitli/açık durum - manevi tasarım
        if (isUnlocked) {
            holder.viewLocked.visibility = View.GONE
            holder.txtIcon.alpha = 1f
            holder.txtName.alpha = 1f
            holder.txtDescription.alpha = 1f
            // Açık rozet - doğal, huzurlu tonlar
            holder.cardBadge.setCardBackgroundColor(
                if (badge.isPremium) Color.parseColor("#FFF8E1") // Altın krem
                else Color.parseColor("#F1F8E9") // Açık yeşil
            )
            holder.cardBadge.strokeWidth = 0
        } else {
            holder.viewLocked.visibility = View.VISIBLE
            holder.txtIcon.alpha = 0.3f
            holder.txtName.alpha = 0.5f
            holder.txtDescription.alpha = 0.5f
            // Kilitli rozet - gri, sade tonlar
            holder.cardBadge.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            val strokeWidthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1f,
                holder.itemView.context.resources.displayMetrics
            ).toInt()
            holder.cardBadge.strokeWidth = strokeWidthPx
            holder.cardBadge.strokeColor = Color.parseColor("#E0E0E0")
        }

        holder.itemView.setOnClickListener {
            if (isUnlocked) {
                onBadgeClick?.invoke(badge)
            }
        }
    }

    override fun getItemCount(): Int = badges.size
}
