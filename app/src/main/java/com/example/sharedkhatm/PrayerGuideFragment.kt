package com.example.sharedkhatm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

class PrayerGuideFragment : Fragment(R.layout.fragment_prayer_guide) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Geri Butonu
        view.findViewById<View>(R.id.btnBackPrayer).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerPrayerSteps)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabPrayerTimes)

        recyclerView.layoutManager = LinearLayoutManager(context)

        // Sekmeleri Ekle
        val prayerNames = listOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı")
        for (name in prayerNames) {
            tabLayout.addTab(tabLayout.newTab().setText(name))
        }

        // Başlangıçta Sabah Namazını Yükle
        loadSteps(recyclerView, PrayerDataHelper.getSabah())

        // Sekme Değişimi
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val list = when (tab?.position) {
                    0 -> PrayerDataHelper.getSabah()
                    1 -> PrayerDataHelper.getOgle()
                    2 -> PrayerDataHelper.getIkindi()
                    3 -> PrayerDataHelper.getAksam()
                    4 -> PrayerDataHelper.getYatsi()
                    else -> emptyList()
                }
                loadSteps(recyclerView, list)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadSteps(recycler: RecyclerView, list: List<PrayerStep>) {
        recycler.adapter = PrayerStepAdapter(list)
    }
}

// --- ADAPTER ---
// --- GÜNCELLENMİŞ ADAPTER (Açılır/Kapanır Özellikli) ---
class PrayerStepAdapter(private val steps: List<PrayerStep>) : RecyclerView.Adapter<PrayerStepAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txtStepTitle)
        val desc: TextView = view.findViewById(R.id.txtStepDesc)
        val arabic: TextView = view.findViewById(R.id.txtStepArabic)
        val reading: TextView = view.findViewById(R.id.txtStepReading)
        val meaning: TextView = view.findViewById(R.id.txtStepMeaning)
        val layoutReading: View = view.findViewById(R.id.layoutReading)
        val imgArrow: View = view.findViewById(R.id.imgExpandArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_prayer_step, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = steps[position]

        holder.title.text = item.title
        holder.desc.text = item.description

        // Eğer okunacak bir dua yoksa (Sadece hareketse), ok işaretini gizle ve tıklamayı kapat
        if (item.reading == null && item.arabic == null) {
            holder.imgArrow.visibility = View.GONE
            holder.layoutReading.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        } else {
            // Dua varsa OK işaretini göster
            holder.imgArrow.visibility = View.VISIBLE

            // Açık mı Kapalı mı kontrol et
            if (item.isExpanded) {
                holder.layoutReading.visibility = View.VISIBLE
                holder.imgArrow.rotation = 180f // Oku yukarı çevir

                holder.arabic.text = item.arabic
                holder.reading.text = item.reading
                holder.meaning.text = item.meaning
            } else {
                holder.layoutReading.visibility = View.GONE
                holder.imgArrow.rotation = 0f // Oku aşağı çevir
            }

            // Tıklama Olayı (Aç/Kapa)
            holder.itemView.setOnClickListener {
                item.isExpanded = !item.isExpanded // Durumu tersine çevir
                notifyItemChanged(position) // Sadece bu satırı yenile (Animasyonlu açılır)
            }
        }
    }

    override fun getItemCount() = steps.size
}

// --- VERİ HAVUZU (TÜM NAMAZLAR) ---
// Sıra ve rekat sayıları Diyanet Türkiye'ye göre (Diyanet Haber, Din İşleri Yüksek Kurulu).
// Akşam namazında ÖNCE FARZ, sonra sünnet kılınır; diğer dört vakitte sünnet(ler) farzdan önce.
//
// | Vakit   | Kılınış sırası (orderIndex)        | Rekat |
// | Sabah   | 1.Sünnet 2 → 2.Farz 2             | 4     |
// | Öğle    | 1.İlk Sünnet 4 → 2.Farz 4 → 3.Son Sünnet 2 | 10 |
// | İkindi  | 1.Sünnet 4 → 2.Farz 4             | 8     |
// | Akşam   | 1.Farz 3 → 2.Sünnet 2             | 5     |
// | Yatsı   | 1.İlk Sünnet 4 → 2.Farz 4 → 3.Son Sünnet 2 | 10 |
object PrayerDataHelper {

    private const val SABAH = "Sabah"
    private const val OGLE = "Öğle"
    private const val IKINDI = "İkindi"
    private const val AKSAM = "Akşam"
    private const val YATSI = "Yatsı"

    /**
     * Diyanet Turkey prayer section order. Akşam is the only prayer where Farz is performed first.
     */
    private fun getSectionsForPrayer(prayerName: String): List<PrayerSection> = when (prayerName) {
        SABAH -> listOf(
            PrayerSection("Sünnet", PrayerType.SUNNET, 2, 1),
            PrayerSection("Farz", PrayerType.FARZ, 2, 2)
        )
        OGLE -> listOf(
            PrayerSection("İlk Sünnet", PrayerType.ILK_SUNNET, 4, 1),
            PrayerSection("Farz", PrayerType.FARZ, 4, 2),
            PrayerSection("Son Sünnet", PrayerType.SON_SUNNET, 2, 3)
        )
        IKINDI -> listOf(
            PrayerSection("Sünnet", PrayerType.SUNNET, 4, 1),
            PrayerSection("Farz", PrayerType.FARZ, 4, 2)
        )
        AKSAM -> listOf(
            PrayerSection("Farz", PrayerType.FARZ, 3, 1),
            PrayerSection("Sünnet", PrayerType.SUNNET, 2, 2)
        )
        YATSI -> listOf(
            PrayerSection("İlk Sünnet", PrayerType.ILK_SUNNET, 4, 1),
            PrayerSection("Farz", PrayerType.FARZ, 4, 2),
            PrayerSection("Son Sünnet", PrayerType.SON_SUNNET, 2, 3)
        )
        else -> emptyList()
    }

    private fun getStepsForSection(prayerName: String, section: PrayerSection): List<PrayerStep> =
        when (prayerName) {
            SABAH -> when (section.type) {
                PrayerType.SUNNET -> buildSabahSunnetSteps()
                PrayerType.FARZ -> buildSabahFarzSteps()
                else -> emptyList()
            }
            OGLE -> when (section.type) {
                PrayerType.ILK_SUNNET -> buildOgleIlkSunnetSteps()
                PrayerType.FARZ -> buildOgleFarzSteps()
                PrayerType.SON_SUNNET -> buildOgleSonSunnetSteps()
                else -> emptyList()
            }
            IKINDI -> when (section.type) {
                PrayerType.SUNNET -> buildIkindiSunnetSteps()
                PrayerType.FARZ -> buildIkindiFarzSteps()
                else -> emptyList()
            }
            AKSAM -> when (section.type) {
                PrayerType.FARZ -> buildAksamFarzSteps()
                PrayerType.SUNNET -> buildAksamSunnetSteps()
                else -> emptyList()
            }
            YATSI -> when (section.type) {
                PrayerType.ILK_SUNNET -> buildYatsiIlkSunnetSteps()
                PrayerType.FARZ -> buildYatsiFarzSteps()
                PrayerType.SON_SUNNET -> buildYatsiSonSunnetSteps()
                else -> emptyList()
            }
            else -> emptyList()
        }

    private fun getOrderedSteps(prayerName: String): List<PrayerStep> =
        getSectionsForPrayer(prayerName)
            .sortedBy { it.orderIndex }
            .flatMap { getStepsForSection(prayerName, it) }

    // Ortak Dualar
    private val subhaneke = PrayerStep(
        "Sübhaneke", "Eller bağlıyken okunur. (Okumak için tıkla)",
        "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، وَتَبَارَكَ اسْمُكَ، وَتَعَالَى جَدُّكَ، وَلاَ إِلَهَ غَيْرُكَ",
        "Sübhânekellâhümme ve bi hamdik ve tebârakesmük ve teâlâ ceddük ve lâ ilâhe gayrük.",
        "Allah'ım! Sen eksik sıfatlardan pak ve uzaksın. Seni daima böyle tenzih eder ve överim. Senin adın mübarektir. Varlığın her şeyden üstündür. Senden başka ilah yoktur."
    )

    private val euzuBesmele = PrayerStep(
        "Euzü Besmele", "",
        "أَعُوذُ بِاللهِ مِنَ الشَّيْطَانِ الرَّجِيمِ , بِسْمِ اللهِ الرَّحْمٰنِ الرَّحِيمِ",
        "Eûzübillâhimineşşeytânirracîm, Bismillâhirrahmânirrahîm.",
        "Kovulmuş şeytandan Allah'a sığınırım. Rahman ve Rahim olan Allah'ın adıyla."
    )

    private val fatiha = PrayerStep(
        "Fatiha Suresi", "Besmele ile okunur.",
        "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ. الرَّحْمَٰنِ الرَّحِيمِ. مَالِكِ يَوْمِ الدِّينِ. إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ. اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ. صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
        "Elhamdülillâhi rabbil'alemin. Errahmânir'rahim. Mâliki yevmiddin. İyyâke na'budü ve iyyâke neste'în. İhdinessırâtel müstakîm. Sırâtellezine en'amte aleyhim ğayrilmağdûbi aleyhim ve leddâllîn.",
        "Hamd, Alemlerin Rabbi Allah'a mahsustur. O, Rahman ve Rahimdir. Din gününün sahibidir. (Allahım!) Yalnız sana ibadet ederiz ve yalnız senden yardım dileriz. Bizi doğru yola, kendilerine nimet verdiklerinin yoluna ilet; gazaba uğrayanlarınkine ve sapıklarınkine değil."
    )

    private val zammiSure = PrayerStep(
        "Zamm-ı Sure", "Bir miktar Kur'an okunur (Örn: Kevser).",
        "إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ. فَصَلِّ لِرَبِّكَ وَانْحَرْ. إِنَّ شَانِئَكَ هُوَ الْأَبْتَرُ",
        "İnnâ a'taynâkelkevser. Fesalli lirabbike venhar. İnne şânieke hüvel'ebter.",
        "Şüphesiz biz sana Kevser'i verdik. O Halde, Rabbin için namaz kıl, kurban kes. Doğrusu sana buğzeden, soyu kesik olanın ta kendisidir."
    )

    private val ruku = PrayerStep(
        "Rüku", "Tekbir ile eğilinir. 3 defa söylenir.",
        "سُبْحَانَ رَبِّيَ الْعَظِيمِ",
        "Sübhâne Rabbiyel Azîm",
        "Yüce Rabbim her türlü noksandan münezzehtir."
    )

    private val secde = PrayerStep(
        "Secde", "Yere kapanılır. 3 defa söylenir.",
        "سُبْحَانَ رَبِّيَ الْأَعْلَى",
        "Sübhâne Rabbiyel A'lâ",
        "Yüce Rabbim her türlü noksandan münezzehtir."
    )

    private val oturmaDualari = PrayerStep(
        "Oturuş Duaları", "Tahiyyat, Salli-Barik ve Rabbena okunur.",
        "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ...",
        "Ettehiyyâtü lillâhi vessalevâtü vettayyibât... (Tamamı çok uzun olduğu için kullanıcı tıkla okusun)",
        "Dil ile, beden ve mal ile yapılan bütün ibadetler Allah'a dır..."
    )

    private val selam = PrayerStep(
        "Selam", "Önce sağa, sonra sola selam verilir.",
        "السَّلامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ",
        "Esselâmü aleyküm ve rahmetullâh",
        "Allah'ın selamı üzerinize olsun."
    )
    // —— Sabah: 2 Sünnet (order 1) + 2 Farz (order 2) — Diyanet ——
    private fun buildSabahSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Sünnet)", "Sabah namazının sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Sabah namazının sünnetini kılmaya.", null),
        PrayerStep("1. Rekat - Başlangıç", "Tekbir alınır ve eller bağlanır.", "الله أكبر", "Allahu Ekber", "Allah en büyüktür."),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat - Kıyam", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildSabahFarzSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Farz)", "Sabah namazının farzına niyet edilir.", null, "Niyet ettim Allah rızası için Sabah namazının farzını kılmaya.", null),
        PrayerStep("1. Rekat - Başlangıç", "Tekbir alınır ve eller bağlanır.", "الله أكبر", "Allahu Ekber", "Allah en büyüktür."),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat - Kıyam", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    fun getSabah(): List<PrayerStep> = getOrderedSteps(SABAH)

    // —— Öğle: 4 İlk Sünnet (1) + 4 Farz (2) + 2 Son Sünnet (3) — Diyanet ——
    private fun buildOgleIlkSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (İlk Sünnet)", "Öğle namazının ilk sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Öğle namazının ilk sünnetini kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Sadece Tahiyyat okunur.", null, "Ettehiyyâtü...", null),
        PrayerStep("3. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildOgleFarzSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Farz)", "Öğle namazının farzına niyet edilir.", null, "Niyet ettim Allah rızası için Öğle namazının farzını kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Sadece Tahiyyat okunur.", null, "Ettehiyyâtü...", null),
        PrayerStep("3. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildOgleSonSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Son Sünnet)", "Öğle namazının son sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Öğle namazının son sünnetini kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    fun getOgle(): List<PrayerStep> = getOrderedSteps(OGLE)

    // —— İkindi: 4 Sünnet (1) + 4 Farz (2) — Diyanet ——
    private fun buildIkindiSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Bilgi", "İkindi namazının sünneti gayri müekkeddir. İlk oturuşta Salli-Barik de okunur.", null, null, null),
        PrayerStep("Niyet (Sünnet)", "İkindi namazının sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için İkindi namazının sünnetini kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Tahiyyat, Salli ve Barik okunur.", null, null, null),
        PrayerStep("3. Rekat", "Kalkınca Sübhaneke, Euzu Besmele, Fatiha, Zammı Sure.", null, null, null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Fatiha ve Zammı Sure.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildIkindiFarzSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Farz)", "İkindi namazının farzına niyet edilir.", null, "Niyet ettim Allah rızası için İkindi namazının farzını kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Sadece Tahiyyat okunur.", null, "Ettehiyyâtü...", null),
        PrayerStep("3. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    fun getIkindi(): List<PrayerStep> = getOrderedSteps(IKINDI)

    // —— Akşam: 3 Farz (order 1) + 2 Sünnet (order 2) — Diyanet: önce farz kılınır ——
    private fun buildAksamFarzSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Farz)", "Akşam namazının farzına niyet edilir.", null, "Niyet ettim Allah rızası için Akşam namazının farzını kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Sadece Tahiyyat.", null, null, null),
        PrayerStep("3. Rekat", "Ayağa kalkılır. SADECE FATİHA okunur (Zammı sure yok).", null, null, null),
        fatiha, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildAksamSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Sünnet)", "Akşam namazının sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Akşam namazının sünnetini kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    fun getAksam(): List<PrayerStep> = getOrderedSteps(AKSAM)

    // —— Yatsı: 4 İlk Sünnet (1) + 4 Farz (2) + 2 Son Sünnet (3) — Diyanet ——
    private fun buildYatsiIlkSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (İlk Sünnet)", "Yatsı namazının ilk sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Yatsı namazının ilk sünnetini kılmaya.", null),
        PrayerStep("Bilgi", "Yatsı sünneti de İkindi sünneti gibi kılınır (4 Rekat).", null, null, null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Tahiyyat, Salli, Barik", null, null, null),
        PrayerStep("3. Rekat", "Sübhaneke, Fatiha, Zammı Sure", null, null, null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Fatiha, Zammı Sure", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildYatsiFarzSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Farz)", "Yatsı namazının farzına niyet edilir.", null, "Niyet ettim Allah rızası için Yatsı namazının farzını kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("İlk Oturuş", "Sadece Tahiyyat okunur.", null, "Ettehiyyâtü...", null),
        PrayerStep("3. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("4. Rekat", "Ayağa kalkılır.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    private fun buildYatsiSonSunnetSteps(): List<PrayerStep> = listOf(
        PrayerStep("Niyet (Son Sünnet)", "Yatsı namazının son sünnetine niyet edilir.", null, "Niyet ettim Allah rızası için Yatsı namazının son sünnetini kılmaya.", null),
        PrayerStep("1. Rekat", "Tekbir ve Kıyam.", null, "Allahu Ekber", null),
        subhaneke, euzuBesmele, fatiha, zammiSure, ruku, secde, secde,
        PrayerStep("2. Rekat", "Kıyam.", null, null, null),
        fatiha, zammiSure, ruku, secde, secde,
        oturmaDualari, selam
    )

    fun getYatsi(): List<PrayerStep> = getOrderedSteps(YATSI)
}