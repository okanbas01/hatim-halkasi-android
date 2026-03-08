package com.example.sharedkhatm.hicri

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Observer
import com.example.sharedkhatm.R
import java.time.LocalDate
import java.util.Calendar

class HicriTakvimActivity : AppCompatActivity() {

    private val viewModel: HicriTakvimViewModel by viewModels()
    private lateinit var recycler: RecyclerView
    private lateinit var recyclerAyGunleri: RecyclerView
    private lateinit var recyclerTumOzel: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var txtError: TextView
    private lateinit var txtTumOzelTitle: TextView
    private lateinit var adapter: HicriSpecialDayAdapter
    private lateinit var adapterAyGunleri: HicriSpecialDayAdapter
    private lateinit var tumOzelAdapter: HicriTumOzelAdapter
    private lateinit var txtMonthStripName: TextView
    private lateinit var txtMonthStripNum: TextView
    private lateinit var contentHicriTakvim: View

    private var currentList: List<HicriSpecialDayItem> = emptyList()
    private var monthsWithEvents: List<Int> = emptyList()
    private var selectedMonthIndex: Int = 0
    /** Tek gün seçiliyse (örn. Kadir Gecesi) orta listede sadece o gösterilir; null ise aydaki tüm günler. */
    private var selectedEventName: String? = "Kadir Gecesi"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hicri_takvim)

        val btnBack = findViewById<ImageButton>(R.id.btnHicriBack)
        val txtDayBig = findViewById<TextView>(R.id.txtHicriDayBig)
        val txtMonthBig = findViewById<TextView>(R.id.txtHicriMonthBig)
        val txtYearBig = findViewById<TextView>(R.id.txtHicriYearBig)
        val txtGregorianPill = findViewById<TextView>(R.id.txtHicriGregorianPill)
        val btnPrevMonth = findViewById<ImageButton>(R.id.btnHicriPrevMonth)
        val btnNextMonth = findViewById<ImageButton>(R.id.btnHicriNextMonth)
        recycler = findViewById(R.id.recyclerHicri)
        recyclerAyGunleri = findViewById(R.id.recyclerHicriAyGunleri)
        recyclerTumOzel = findViewById(R.id.recyclerHicriTumOzel)
        progress = findViewById(R.id.progressHicri)
        txtError = findViewById(R.id.txtHicriError)
        txtTumOzelTitle = findViewById(R.id.txtHicriTumOzelTitle)
        txtMonthStripName = findViewById(R.id.txtHicriMonthStripName)
        txtMonthStripNum = findViewById(R.id.txtHicriMonthStripNum)
        contentHicriTakvim = findViewById(R.id.contentHicriTakvim)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val today = LocalDate.now()
        val helper = IslamicCalendarHelper
        txtDayBig.text = helper.getHijriDayOfMonth(today).toString()
        val (monthNum, year) = helper.getHijriMonthYear(today)
        txtMonthBig.text = helper.getHijriMonthName(monthNum)
        txtYearBig.text = "$year Hicri"
        txtGregorianPill.text = helper.toGregorianShort(today)

        txtMonthStripName.text = helper.getHijriMonthName(monthNum)
        txtMonthStripNum.text = "$monthNum. Ay"

        adapter = HicriSpecialDayAdapter().apply {
            nearestIndex = -1
            selectedIndex = -1
            onItemClick = { position, _ ->
                scrollYaklasanToPosition(position)
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        adapterAyGunleri = HicriSpecialDayAdapter().apply {
            nearestIndex = -1
            selectedIndex = -1
        }
        recyclerAyGunleri.layoutManager = LinearLayoutManager(this)
        recyclerAyGunleri.adapter = adapterAyGunleri

        tumOzelAdapter = HicriTumOzelAdapter(emptyList()) { _, item ->
            // Ramazan (oruç ayı) kartına tıklanınca her zaman 9. ay; Ramazan Bayramı 10. ay (Şevval)
            val monthNum = when (item.name) {
                "Ramazan" -> 9
                "Ramazan Bayramı" -> 10
                else -> item.hijriMonthNumber
            }
            val idx = monthsWithEvents.indexOf(monthNum)
            if (idx >= 0) {
                selectedMonthIndex = idx
                selectedEventName = when {
                    item.name.contains("Ramazan Bayramı") || item.name.contains("Kurban Bayramı") -> null
                    else -> item.name
                }
                updateMonthStrip()
                updateMonthEventsList()
            }
        }
        recyclerTumOzel.layoutManager = GridLayoutManager(this, 2)
        recyclerTumOzel.adapter = tumOzelAdapter

        btnPrevMonth.setOnClickListener {
            if (monthsWithEvents.isEmpty()) return@setOnClickListener
            selectedMonthIndex = (selectedMonthIndex - 1 + monthsWithEvents.size) % monthsWithEvents.size
            selectedEventName = null
            val monthNum = monthsWithEvents[selectedMonthIndex]
            updateMonthStrip()   // şerit: ay adı (Şaban, Ramazan vb.)
            updateMonthEventsList()  // liste: sadece bu aya ait özel günler
            scrollYaklasanToFirstOfMonth(monthNum)
        }
        btnNextMonth.setOnClickListener {
            if (monthsWithEvents.isEmpty()) return@setOnClickListener
            selectedMonthIndex = (selectedMonthIndex + 1) % monthsWithEvents.size
            selectedEventName = null
            val monthNum = monthsWithEvents[selectedMonthIndex]
            updateMonthStrip()
            updateMonthEventsList()
            scrollYaklasanToFirstOfMonth(monthNum)
        }

        val yearInt = Calendar.getInstance().get(Calendar.YEAR)
        viewModel.loadYear(yearInt)

        viewModel.yearListLiveData.observe(this, Observer { state ->
            when (state) {
                is HicriYearListState.Loading -> {
                    progress.visibility = View.VISIBLE
                    contentHicriTakvim.visibility = View.GONE
                    txtError.visibility = View.GONE
                }
                is HicriYearListState.Success -> {
                    progress.visibility = View.GONE
                    contentHicriTakvim.visibility = View.VISIBLE
                    recycler.visibility = View.VISIBLE
                    recyclerAyGunleri.visibility = View.VISIBLE
                    recyclerTumOzel.visibility = View.VISIBLE
                    txtError.visibility = View.GONE
                    currentList = state.items
                    monthsWithEvents = currentList.map { it.hijriMonthNumber }.distinct().sorted()
                    // Varsayılan: Kadir Gecesi seçili gibi (Ramazan ayı, sadece Kadir Gecesi)
                    selectedEventName = "Kadir Gecesi"
                    val ramazanIndex = monthsWithEvents.indexOf(9)
                    selectedMonthIndex = if (ramazanIndex >= 0) ramazanIndex else 0

                    val now = LocalDate.now()
                    // Aynı etkinlikten sadece ilk gün (örn. Kurban Bayramı (1. Gün)) Yaklaşan'da gösterilsin
                    val baseName = { name: String ->
                        name.replace(Regex(" \\(\\d+\\. Gün\\)$"), "")
                    }
                    // Geçen veya bugün (0 gün kaldı) olanları gösterme; Ramazan için sadece ilk gün (Oruç ayının ilk günü)
                    val yaklasanList = currentList
                        .filter { item ->
                            item.daysOffset > 0 && (item.rangeEndDate == null || !item.isRangeEnded(now))
                                && !item.name.matches(Regex("Ramazan \\d+\\. Gün")) // Ramazan 2.–30. günü Yaklaşan'da gösterme
                        }
                        .distinctBy { "${it.name}-${it.gregorianDate}" }
                        .sortedBy { it.gregorianDate }
                        .distinctBy { baseName(it.name) }
                        .take(4)
                    adapter.nearestIndex = 0
                    adapter.submitList(yaklasanList)

                    // Tüm Özel Günler: Tek Ramazan kartı (oruç ayı + Ramazan Bayramı); Kurban ayrı
                    val groupKey = { name: String ->
                        when {
                            name.contains("Ramazan Bayramı") || name == "Oruç ayının ilk günü" || (name.startsWith("Ramazan ") && name.endsWith(". Gün")) -> "Ramazan"
                            name.contains("Kurban Bayramı") -> "Kurban Bayramı"
                            else -> baseName(name)
                        }
                    }
                    val tumOzelGrouped = currentList
                        .groupBy { groupKey(it.name) }
                        .map { (displayName, group) ->
                            // Tıklanınca Ramazan için 9. aya (oruç ayı) gitsin; temsilci en erken tarih
                            val representative = group
                                .filter { !it.name.contains("Arefe") }
                                .minByOrNull { it.gregorianDate }
                                ?: group.minByOrNull { it.gregorianDate }!!
                            representative.copy(name = displayName)
                        }
                        .sortedBy { it.gregorianDate }
                    tumOzelAdapter.submitList(tumOzelGrouped)
                    txtTumOzelTitle.text = "Tüm Özel Günler (${tumOzelGrouped.size})"
                    updateMonthStrip()
                    updateMonthEventsList()
                }
                is HicriYearListState.Error -> {
                    progress.visibility = View.GONE
                    recycler.visibility = View.GONE
                    recyclerAyGunleri.visibility = View.GONE
                    recyclerTumOzel.visibility = View.GONE
                    txtError.visibility = View.VISIBLE
                    txtError.text = state.message
                }
            }
        })
    }

    /** Şeritte gösterilen ay = monthsWithEvents[selectedMonthIndex]; sağ/sol oklar bu indeksi değiştirir. */
    private fun updateMonthStrip() {
        if (monthsWithEvents.isEmpty()) return
        val monthNum = monthsWithEvents.getOrNull(selectedMonthIndex) ?: return
        // Tek günlük etkinlik seçiliyse (Kadir Gecesi, Mevlid vb.) şeritte onun adı gösterilsin
        val singleEventName = selectedEventName?.takeIf { name ->
            name != "Ramazan" && !name.contains("Bayramı")
        }
        if (!singleEventName.isNullOrBlank()) {
            txtMonthStripName.text = singleEventName
            txtMonthStripNum.text = "${IslamicCalendarHelper.getHijriMonthName(monthNum)} · $monthNum. Ay"
        } else {
            txtMonthStripName.text = IslamicCalendarHelper.getHijriMonthName(monthNum)
            txtMonthStripNum.text = "$monthNum. Ay"
        }
    }

    /** Şeritteki ay ile eşleşen liste: sadece o Hicri aydaki özel günler (Şaban → Şaban günleri, Ramazan → Ramazan günleri). */
    private fun updateMonthEventsList() {
        if (monthsWithEvents.isEmpty()) return
        val monthNum = monthsWithEvents.getOrNull(selectedMonthIndex) ?: return
        var forMonth = currentList.filter { it.hijriMonthNumber == monthNum }
        val filterName = selectedEventName
        if (!filterName.isNullOrBlank()) {
            forMonth = when (filterName) {
                "Ramazan" -> {
                    // Sadece oruç ayı günleri (9. ay), 1. günden 30. güne kronolojik
                    forMonth.filter {
                        it.name == "Oruç ayının ilk günü" || it.name.matches(Regex("Ramazan \\d+\\. Gün"))
                    }.sortedBy { item ->
                        when {
                            item.name == "Oruç ayının ilk günü" -> 1
                            else -> Regex("Ramazan (\\d+)\\. Gün").find(item.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                    }
                }
                else -> forMonth.filter { it.name == filterName || it.name.startsWith("$filterName ") }
                    .sortedBy { it.gregorianDate }
            }
        } else {
            forMonth = forMonth.sortedBy { it.gregorianDate }
        }
        adapterAyGunleri.submitList(forMonth)
    }

    private fun scrollYaklasanToPosition(position: Int) {
        recycler.smoothScrollToPosition(position.coerceIn(0, (currentList.size - 1).coerceAtLeast(0)))
    }

    private fun scrollYaklasanToFirstOfMonth(monthNum: Int) {
        val pos = currentList.indexOfFirst { it.hijriMonthNumber == monthNum }.coerceAtLeast(0)
        scrollYaklasanToPosition(pos)
    }
}
