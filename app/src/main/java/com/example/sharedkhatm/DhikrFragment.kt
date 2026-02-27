package com.example.sharedkhatm

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText

/**
 * Gelişmiş Zikirmatik: çoklu zikir, hedef, kaydet, geçmiş, sıralama.
 * Performans: DiffUtil + payload, notifyDataSetChanged yok. Veri SharedPreferences.
 */
class DhikrFragment : Fragment(R.layout.fragment_dhikr) {

    private lateinit var recyclerZikir: RecyclerView
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var layoutHistoryContainer: View
    private lateinit var txtHistoryTotal: TextView
    private lateinit var tabZikirler: TextView
    private lateinit var tabGecmis: TextView
    private lateinit var layoutAddZikir: View

    private val list = mutableListOf<ZikirItem>()
    private lateinit var adapter: ZikirAdapter
    private lateinit var historyAdapter: ZikirHistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }

            recyclerZikir = view.findViewById(R.id.recyclerZikir)
            recyclerHistory = view.findViewById(R.id.recyclerHistory)
            layoutHistoryContainer = view.findViewById(R.id.layoutHistoryContainer)
            txtHistoryTotal = view.findViewById(R.id.txtHistoryTotal)
            tabZikirler = view.findViewById(R.id.tabZikirler)
            tabGecmis = view.findViewById(R.id.tabGecmis)
            layoutAddZikir = view.findViewById(R.id.layoutAddZikir)

            setupTabs()
            setupList()
            setupHistory()
            layoutAddZikir.setOnClickListener { showAddDialog() }
            loadDataAsync()
        } catch (e: Exception) {
            android.util.Log.e("DhikrFragment", "Zikirmatik açılamadı: ${e.javaClass.simpleName} - ${e.message}", e)
            Toast.makeText(requireContext(), "Zikirmatik açılamadı.", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupTabs() {
        tabZikirler.setOnClickListener { showZikirler() }
        tabGecmis.setOnClickListener { showGecmis() }
    }

    private fun showZikirler() {
        recyclerZikir.visibility = View.VISIBLE
        layoutHistoryContainer.visibility = View.GONE
        layoutAddZikir.visibility = View.VISIBLE
        tabZikirler.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_green))
        tabZikirler.setTypeface(null, android.graphics.Typeface.BOLD)
        tabGecmis.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_grey))
        tabGecmis.setTypeface(null, android.graphics.Typeface.NORMAL)
    }

    private fun showGecmis() {
        recyclerZikir.visibility = View.GONE
        layoutHistoryContainer.visibility = View.VISIBLE
        layoutAddZikir.visibility = View.GONE
        tabGecmis.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_green))
        tabGecmis.setTypeface(null, android.graphics.Typeface.BOLD)
        tabZikirler.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_grey))
        tabZikirler.setTypeface(null, android.graphics.Typeface.NORMAL)
        refreshHistory()
    }

    private fun vibrate() {
        try {
            val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(50)
                }
            }
        } catch (_: Exception) {}
    }

    private fun setupList() {
        adapter = ZikirAdapter(
            onPlus = { item -> updateCount(item, 1) },
            onMinus = { item -> updateCount(item, -1) },
            onReset = { item -> showResetDialog(item) },
            onSave = { item -> saveToHistory(item) },
            onDelete = { item -> showDeleteConfirm(item) },
            onEdit = { showEditDialog(it) },
            vibrate = { vibrate() }
        )
        adapter.setHasStableIds(true)
        recyclerZikir.layoutManager = LinearLayoutManager(requireContext())
        recyclerZikir.adapter = adapter

        val callback = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from >= list.size || to >= list.size) return false
                val moved = list.removeAt(from)
                list.add(to, moved)
                updateOrderAndSave()
                adapter.items = list.toList()
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        })
        callback.attachToRecyclerView(recyclerZikir)
    }

    private fun updateOrderAndSave() {
        list.forEachIndexed { i, it -> it.order = i }
        ZikirStorage.saveList(requireContext(), list)
    }

    private fun setupHistory() {
        historyAdapter = ZikirHistoryAdapter()
        historyAdapter.onDelete = { record ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kaydı sil")
                .setMessage("\"${record.zikirName}\" (${record.count}) kaydı silinsin mi?")
                .setPositiveButton("Sil") { _, _ ->
                    if (ZikirStorage.removeHistoryRecord(requireContext(), record)) {
                        refreshHistory()
                        Toast.makeText(requireContext(), "Kayıt silindi.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }
        recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        recyclerHistory.adapter = historyAdapter
    }

    /** ANR fix: prefs + Gson off main thread. */
    private fun loadDataAsync() {
        viewLifecycleOwner.lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) { ZikirStorage.getOrCreateList(requireContext()) }
            if (!isAdded) return@launch
            list.clear()
            list.addAll(created)
            adapter.items = list.toList()
        }
    }

    private fun updateCount(item: ZikirItem, delta: Int) {
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        val current = list[idx]
        val newCount = (current.currentCount + delta).coerceIn(0, Int.MAX_VALUE)
        list[idx] = current.copy(currentCount = newCount)
        ZikirStorage.saveList(requireContext(), list)
        adapter.items = list.toList()
    }

    private fun showResetDialog(item: ZikirItem) {
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        val current = list[idx]
        if (current.currentCount == 0) {
            Toast.makeText(requireContext(), "Sayaç zaten sıfır.", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Zikri sıfırla")
            .setMessage("Sayacı sıfırlamak istediğinize emin misiniz? Geçmiş kayıtlar silinmez.")
            .setPositiveButton("Sıfırla") { _, _ ->
                if (idx in list.indices) {
                    list[idx] = list[idx].copy(currentCount = 0)
                    ZikirStorage.saveList(requireContext(), list)
                    adapter.items = list.toList()
                    Toast.makeText(requireContext(), "Sıfırlandı", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun saveToHistory(item: ZikirItem) {
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        val current = list[idx]
        if (current.currentCount <= 0) {
            Toast.makeText(requireContext(), "Kaydedilecek sayı yok.", Toast.LENGTH_SHORT).show()
            return
        }
        ZikirStorage.appendHistory(requireContext(), ZikirHistoryRecord(
            dateYmd = ZikirStorage.todayYmd(),
            zikirId = current.id,
            zikirName = current.name,
            count = current.currentCount
        ))
        list[idx] = current.copy(currentCount = 0)
        ZikirStorage.saveList(requireContext(), list)
        adapter.items = list.toList()
        Toast.makeText(requireContext(), "Geçmişe eklendi.", Toast.LENGTH_SHORT).show()
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dhikr_edit, null)
        dialogView.findViewById<ImageView>(R.id.iconDialog).setImageResource(android.R.drawable.ic_input_add)
        dialogView.findViewById<TextView>(R.id.txtDialogTitle).text = "Yeni zikir"
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etTarget = dialogView.findViewById<EditText>(R.id.etTarget)
        etTarget.setText("33")
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val target = etTarget.text?.toString()?.toIntOrNull() ?: 33
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Zikir adı girin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Yeni zikir en üstte: mevcut tüm order'ları 1 artır, yeniye order 0 ver
            list.forEach { it.order = it.order + 1 }
            val newItem = ZikirItem(
                id = ZikirStorage.generateId(),
                name = name,
                target = target.coerceIn(1, 100_000),
                currentCount = 0,
                order = 0
            )
            list.add(0, newItem)
            ZikirStorage.saveList(requireContext(), list)
            adapter.items = list.toList()
            dialog.dismiss()
            recyclerZikir.post { recyclerZikir.smoothScrollToPosition(0) }
            Toast.makeText(requireContext(), "Eklendi.", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showEditDialog(item: ZikirItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dhikr_edit, null)
        dialogView.findViewById<ImageView>(R.id.iconDialog).setImageResource(R.drawable.ic_edit)
        dialogView.findViewById<TextView>(R.id.txtDialogTitle).text = "Zikri düzenle"
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etTarget = dialogView.findViewById<EditText>(R.id.etTarget)
        etName.setText(item.name)
        etTarget.setText(item.target.toString())
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val target = etTarget.text?.toString()?.toIntOrNull() ?: item.target
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Zikir adı girin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val idx = list.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(name = name, target = target.coerceIn(1, 100_000))
                ZikirStorage.saveList(requireContext(), list)
                adapter.items = list.toList()
            }
            dialog.dismiss()
            Toast.makeText(requireContext(), "Güncellendi.", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showDeleteConfirm(item: ZikirItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Zikri sil")
            .setMessage("\"${item.name}\" silinsin mi? Geçmiş kayıtlar durur.")
            .setPositiveButton("Sil") { _, _ ->
                list.removeAll { it.id == item.id }
                updateOrderAndSave()
                adapter.items = list.toList()
                Toast.makeText(requireContext(), "Silindi.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** ANR fix: prefs + Gson off main thread. */
    private fun refreshHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) { ZikirStorage.getHistory(requireContext()) }
            if (!isAdded) return@launch
            historyAdapter.records = records
            txtHistoryTotal.text = records.sumOf { it.count }.toString()
        }
    }

}
