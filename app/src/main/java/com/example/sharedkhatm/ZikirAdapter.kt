package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Zikir kartları listesi. DiffUtil ile güncelleme; payload ile sadece sayı güncellemesi.
 * notifyDataSetChanged kullanılmaz.
 */
class ZikirAdapter(
    private val onPlus: (ZikirItem) -> Unit,
    private val onMinus: (ZikirItem) -> Unit,
    private val onReset: (ZikirItem) -> Unit,
    private val onSave: (ZikirItem) -> Unit,
    private val onDelete: (ZikirItem) -> Unit,
    private val onEdit: (ZikirItem) -> Unit,
    private val vibrate: () -> Unit
) : RecyclerView.Adapter<ZikirAdapter.ZikirViewHolder>() {

    companion object {
        val PAYLOAD_COUNT = Any()
    }

    var items: List<ZikirItem> = emptyList()
        set(value) {
            val oldList = field
            if (oldList === value) return
            if (oldList.isEmpty() && value.isEmpty()) return
            if (oldList.isEmpty()) {
                field = value
                notifyItemRangeInserted(0, value.size)
                return
            }
            if (value.isEmpty()) {
                val size = oldList.size
                field = value
                notifyItemRangeRemoved(0, size)
                return
            }
            val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldList.size
                override fun getNewListSize() = value.size
                override fun areItemsTheSame(i: Int, j: Int): Boolean {
                    if (i !in oldList.indices || j !in value.indices) return false
                    return oldList[i].id == value[j].id
                }
                override fun areContentsTheSame(i: Int, j: Int): Boolean {
                    if (i !in oldList.indices || j !in value.indices) return false
                    return oldList[i] == value[j]
                }
                override fun getChangePayload(i: Int, j: Int): Any? {
                    if (i !in oldList.indices || j !in value.indices) return null
                    val a = oldList[i]
                    val b = value[j]
                    return if (a.currentCount != b.currentCount && a.name == b.name && a.target == b.target) PAYLOAD_COUNT else null
                }
            })
            field = value
            result.dispatchUpdatesTo(this)
        }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZikirViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_zikir_card, parent, false)
        return ZikirViewHolder(v)
    }

    override fun onBindViewHolder(holder: ZikirViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onBindViewHolder(holder: ZikirViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] === PAYLOAD_COUNT) {
            holder.updateCountOnly(items[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount() = items.size

    inner class ZikirViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtZikirName: TextView = itemView.findViewById(R.id.txtZikirName)
        private val txtCountTarget: TextView = itemView.findViewById(R.id.txtCountTarget)
        private val txtCount: TextView = itemView.findViewById(R.id.txtCount)
        private val layoutCount: View = itemView.findViewById(R.id.layoutCount)
        private val btnMinus: View = itemView.findViewById(R.id.btnMinus)
        private val btnReset: View = itemView.findViewById(R.id.btnReset)
        private val btnSave: View = itemView.findViewById(R.id.btnSave)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val imgDrag: ImageView = itemView.findViewById(R.id.imgDrag)

        fun bind(item: ZikirItem) {
            txtZikirName.text = item.name
            txtCountTarget.text = "${item.currentCount} / ${item.target}"
            txtCount.text = item.currentCount.toString()
            layoutCount.setOnClickListener {
                vibrate()
                onPlus(item)
            }
            btnMinus.setOnClickListener {
                vibrate()
                onMinus(item)
            }
            btnReset.setOnClickListener {
                vibrate()
                onReset(item)
            }
            btnSave.setOnClickListener { onSave(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnEdit.setOnClickListener { onEdit(item) }
            imgDrag.visibility = View.VISIBLE
        }

        fun updateCountOnly(item: ZikirItem) {
            txtCountTarget.text = "${item.currentCount} / ${item.target}"
            txtCount.text = item.currentCount.toString()
        }
    }
}

/** Geçmiş listesi için basit adapter (DiffUtil opsiyonel, liste küçük). */
class ZikirHistoryAdapter : RecyclerView.Adapter<ZikirHistoryAdapter.HistoryViewHolder>() {

    var onDelete: ((ZikirHistoryRecord) -> Unit)? = null

    var records: List<ZikirHistoryRecord> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged() // Geçmiş listesi küçük ve sık değişmez; kabul edilebilir
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_zikir_history, parent, false)
        return HistoryViewHolder(v)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtDate: TextView = itemView.findViewById(R.id.txtHistoryDate)
        private val txtName: TextView = itemView.findViewById(R.id.txtHistoryName)
        private val txtCount: TextView = itemView.findViewById(R.id.txtHistoryCount)
        private val btnHistoryDelete: ImageButton = itemView.findViewById(R.id.btnHistoryDelete)

        fun bind(record: ZikirHistoryRecord) {
            txtDate.text = ZikirStorage.formatDateForDisplay(record.dateYmd)
            txtName.text = record.zikirName
            txtCount.text = record.count.toString()
            btnHistoryDelete.setOnClickListener { this@ZikirHistoryAdapter.onDelete?.invoke(record) }
        }
    }
}
