package com.example.sharedkhatm

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.UUID

/**
 * Zikir listesi ve geçmiş tek yerde; hafif, main thread'de kısa süreli okuma/yazma.
 * notifyDataSetChanged yok, O(1) history append (tek kayıt ekle + cap).
 */
object ZikirStorage {

    private const val PREFS_NAME = "ZikirPrefs"
    private const val KEY_LIST = "zikir_list"
    private const val KEY_HISTORY = "zikir_history"
    private const val KEY_INITIALIZED = "zikr_initialized"
    private const val KEY_LEGACY_COUNT = "savedCount" // eski tek sayaç
    private const val MAX_HISTORY = 300

    private val gson = Gson()
    private val listType = object : TypeToken<ArrayList<ZikirItem>>() {}.type
    private val historyType = object : TypeToken<ArrayList<ZikirHistoryRecord>>() {}.type

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Mevcut listeyi oku; yoksa veya boşsa default/migration uygula ve kaydet. */
    fun getOrCreateList(context: Context): MutableList<ZikirItem> {
        return try {
            val p = prefs(context)
            val raw = p.getString(KEY_LIST, null)
            val list = if (raw.isNullOrBlank()) mutableListOf<ZikirItem>() else runCatching {
                gson.fromJson<ArrayList<ZikirItem>>(raw, listType)?.toMutableList() ?: mutableListOf()
            }.getOrElse { mutableListOf() }

        if (p.getBoolean(KEY_INITIALIZED, false)) {
            // Eski tek sayaç "Zikir" ise "İsimsiz" göster; kullanıcı editleyip isim verebilir
            if (list.any { it.name == "Zikir" }) {
                val updated = list.map { if (it.name == "Zikir") it.copy(name = "İsimsiz") else it }.toMutableList()
                saveList(context, updated)
                return updated
            }
            return list
        }

            // İlk kez: eski kullanıcı sayaç koruma + default set (isim çakışması yoksa ekle)
            val legacyCount = p.getInt(KEY_LEGACY_COUNT, -1)
            val existingNames = list.map { it.name.trim().lowercase() }.toSet()
            val toAdd = mutableListOf<ZikirItem>()

            if (legacyCount >= 0) {
                toAdd.add(ZikirItem(id = "legacy", name = "İsimsiz", target = 33, currentCount = legacyCount, order = 0))
            }
            val defaults = listOf(
                Triple("Subhanallah", 33, 1),
                Triple("Elhamdülillah", 33, 2),
                Triple("Allahu Ekber", 33, 3),
                Triple("La ilahe illallah", 100, 4),
                Triple("Estağfirullah", 100, 5)
            )
            var orderOffset = toAdd.size
            for ((name, target, _) in defaults) {
                if (name.trim().lowercase() in existingNames) continue
                toAdd.add(ZikirItem(id = "def_$orderOffset", name = name, target = target, currentCount = 0, order = orderOffset))
                orderOffset++
            }

            saveList(context, toAdd)
            p.edit().putBoolean(KEY_INITIALIZED, true).apply()
            toAdd
        } catch (e: Exception) {
            // Bozuk veri veya beklenmeyen hata: varsayılan 5 zikir dön
            mutableListOf(
                ZikirItem("def_0", "Subhanallah", 33, 0, 0),
                ZikirItem("def_1", "Elhamdülillah", 33, 0, 1),
                ZikirItem("def_2", "Allahu Ekber", 33, 0, 2),
                ZikirItem("def_3", "La ilahe illallah", 100, 0, 3),
                ZikirItem("def_4", "Estağfirullah", 100, 0, 4)
            )
        }
    }

    fun saveList(context: Context, list: List<ZikirItem>) {
        prefs(context).edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }

    /** Geçmiş listesini oku (en yeniden eskiye sıralı dön). */
    fun getHistory(context: Context): List<ZikirHistoryRecord> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<ArrayList<ZikirHistoryRecord>>(raw, historyType) ?: emptyList()
        }.getOrElse { emptyList() }.reversed()
    }

    /** Tek kayıt ekle (O(1) append), liste uzunluğu cap. */
    fun appendHistory(context: Context, record: ZikirHistoryRecord) {
        val p = prefs(context)
        val raw = p.getString(KEY_HISTORY, null)
        val list = if (raw.isNullOrBlank()) mutableListOf<ZikirHistoryRecord>()
        else runCatching { gson.fromJson<ArrayList<ZikirHistoryRecord>>(raw, historyType)?.toMutableList() }.getOrNull() ?: mutableListOf()
        list.add(record)
        val capped = if (list.size > MAX_HISTORY) list.takeLast(MAX_HISTORY) else list
        p.edit().putString(KEY_HISTORY, gson.toJson(capped)).apply()
    }

    /** Geçmişten tek kaydı sil (tarih + zikir adı + sayı eşleşmesi). */
    fun removeHistoryRecord(context: Context, record: ZikirHistoryRecord): Boolean {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return false
        val list = runCatching { gson.fromJson<ArrayList<ZikirHistoryRecord>>(raw, historyType)?.toMutableList() }.getOrNull() ?: return false
        val idx = list.indexOfFirst { it.dateYmd == record.dateYmd && it.zikirName == record.zikirName && it.count == record.count }
        if (idx < 0) return false
        list.removeAt(idx)
        prefs(context).edit().putString(KEY_HISTORY, gson.toJson(list)).apply()
        return true
    }

    fun todayYmd(): String {
        val c = Calendar.getInstance()
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun formatDateForDisplay(dateYmd: String): String {
        if (dateYmd.length != 8) return dateYmd
        return try {
            val y = dateYmd.substring(0, 4).toInt()
            val m = dateYmd.substring(4, 6).toInt()
            val d = dateYmd.substring(6, 8).toInt()
            "$d ${monthName(m)} $y"
        } catch (_: Exception) {
            dateYmd
        }
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "Ocak"
        2 -> "Şubat"
        3 -> "Mart"
        4 -> "Nisan"
        5 -> "Mayıs"
        6 -> "Haziran"
        7 -> "Temmuz"
        8 -> "Ağustos"
        9 -> "Eylül"
        10 -> "Ekim"
        11 -> "Kasım"
        12 -> "Aralık"
        else -> ""
    }

    fun generateId(): String = UUID.randomUUID().toString()
}
