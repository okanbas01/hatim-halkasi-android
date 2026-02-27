package com.example.sharedkhatm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EsmaFragment : Fragment(R.layout.fragment_esma) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Geri Butonu
        view.findViewById<View>(R.id.btnBackEsma).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerEsma)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // 99 İsmin Tam Listesi
        val esmaList = getEsmaList()

        recyclerView.adapter = EsmaAdapter(esmaList)
    }

    private fun getEsmaList(): List<EsmaName> {
        return listOf(
            EsmaName("الله", "Allah", "Eşi ve benzeri olmayan, bütün noksan sıfatlardan münezzeh.", 66),
            EsmaName("الرَّحْمَن", "Er-Rahman", "Dünyada bütün mahlükata merhamet eden.", 298),
            EsmaName("الرَّحِيم", "Er-Rahim", "Ahirette sadece müminlere merhamet eden.", 258),
            EsmaName("الْمَلِك", "El-Melik", "Mülkün, kainatın yegane sahibi.", 90),
            EsmaName("الْقُدُّوس", "El-Kuddüs", "Her türlü eksiklikten uzak, pak ve temiz.", 170),
            EsmaName("السَّلام", "Es-Selam", "Her türlü tehlikeden selamete çıkaran.", 131),
            EsmaName("الْمُؤْمِن", "El-Mü'min", "Güven veren, emin kılan, koruyan.", 137),
            EsmaName("الْمُهَيْمِن", "El-Müheymin", "Her şeyi görüp gözeten, her varlığın yaptıklarından haberdar olan.", 145),
            EsmaName("الْعَزِيز", "El-Aziz", "İzzet sahibi, her şeye galip gelen.", 94),
            EsmaName("الْجَبَّار", "El-Cebbar", "Azamet ve kudret sahibi, dilediğini yapan.", 206),
            EsmaName("الْمُتَكَبِّر", "El-Mütekebbir", "Büyüklükte eşi, benzeri olmayan.", 662),
            EsmaName("الْخَالِق", "El-Halik", "Yaratan, yoktan var eden.", 731),
            EsmaName("الْبَارِئ", "El-Bari", "Her şeyi kusursuz ve uyumlu yaratan.", 214),
            EsmaName("الْمُصَوِّر", "El-Musavvir", "Varlıklara şekil ve suret veren.", 336),
            EsmaName("الْغَفَّار", "El-Gaffar", "Günahları örten ve çok mağfiret eden.", 1281),
            EsmaName("الْقَهَّار", "El-Kahhar", "Her şeye galip gelen ve hakim olan.", 306),
            EsmaName("الْوَهَّاب", "El-Vehhab", "Karşılıksız bolca veren, ihsan eden.", 14),
            EsmaName("الرَّزَّاق", "Er-Rezzak", "Bütün mahlükatın rızkını veren.", 308),
            EsmaName("الْفَتَّاح", "El-Fettah", "Her türlü müşkülleri açan ve kolaylaştıran.", 489),
            EsmaName("الْعَلِيم", "El-Alim", "Gizli ve açık, her şeyi en iyi bilen.", 150),
            EsmaName("الْقَابِض", "El-Kabid", "Dilediğine darlık veren, sıkan.", 903),
            EsmaName("الْبَاسِط", "El-Basit", "Dilediğine bolluk veren, açan.", 72),
            EsmaName("الْخَافِض", "El-Hafid", "Dereceleri alçaltan, kafirleri perişan eden.", 1481),
            EsmaName("الرَّافِع", "Er-Rafi", "Şeref verip yükselten.", 351),
            EsmaName("الْمُعِزّ", "El-Muiz", "Dilediğini aziz eden, izzet veren.", 117),
            EsmaName("الْمُذِلّ", "El-Müzil", "Dilediğini zillete düşüren, hor ve hakir eden.", 770),
            EsmaName("السَّمِيع", "Es-Semi", "Her şeyi en iyi işiten.", 180),
            EsmaName("الْبَصِير", "El-Basir", "Gizli ve açık, her şeyi en iyi gören.", 302),
            EsmaName("الْحَكَم", "El-Hakem", "Mutlak hakim, hakkı batıldan ayıran.", 68),
            EsmaName("الْعَدْل", "El-Adl", "Mutlak adil, çok adaletli.", 104),
            EsmaName("اللَّطِيف", "El-Latif", "Lütuf ve ihsan sahibi, her şeye vakıf.", 129),
            EsmaName("الْخَبِير", "El-Habir", "Her şeyden haberdar olan, gizli taraflarını bilen.", 812),
            EsmaName("الْحَلِيم", "El-Halim", "Yumuşak davranan, hilm sahibi.", 88),
            EsmaName("الْعَظِيم", "El-Azim", "Büyüklükte benzeri olmayan.", 1020),
            EsmaName("الْغَفُور", "El-Gafur", "Affı ve mağfireti pek çok olan.", 1286),
            EsmaName("الشَّكُور", "Es-Şekur", "Az amele çok sevap veren.", 526),
            EsmaName("الْعَلِيّ", "El-Aliyy", "Yüceler yücesi, çok yüce.", 110),
            EsmaName("الْكَبِير", "El-Kebir", "Büyüklükte eşi olmayan.", 232),
            EsmaName("الْحَفِيظ", "El-Hafiz", "Her şeyi koruyan ve saklayan.", 998),
            EsmaName("الْمُقِيت", "El-Mukit", "Rızıkları yaratan ve bedene gönderen.", 550),
            EsmaName("الْحَسِيب", "El-Hasib", "Herkesin hesabını en iyi gören.", 80),
            EsmaName("الْجَلِيل", "El-Celil", "Celal ve azamet sahibi.", 73),
            EsmaName("الْكَرِيم", "El-Kerim", "Çok ikram eden, cömert.", 270),
            EsmaName("الرَّقِيب", "Er-Rakib", "Her varlığı, her işi her an gözetleyen.", 312),
            EsmaName("الْمُجِيب", "El-Mucib", "Duaları ve istekleri kabul eden.", 55),
            EsmaName("الْوَاسِع", "El-Vasi", "Rahmeti, kudreti ve ilmi ile her şeyi kuşatan.", 137),
            EsmaName("الْحَكِيم", "El-Hakim", "Her işi hikmetli olan.", 78),
            EsmaName("الْوَدُود", "El-Vedud", "İyi kullarını seven, sevilmeye layık olan.", 20),
            EsmaName("الْمَجِيد", "El-Mecid", "Şanı çok büyük ve yüce olan.", 57),
            EsmaName("الْبَاعِث", "El-Bais", "Ölüleri dirilten, peygamber gönderen.", 573),
            EsmaName("الشَّهِيد", "Es-Şehid", "Her zamanda ve her yerde hazır ve nazır olan.", 319),
            EsmaName("الْحَقّ", "El-Hakk", "Varlığı hiç değişmeden duran, var olan, hakkı ortaya çıkaran.", 108),
            EsmaName("الْوَكِيل", "El-Vekil", "Kulların işlerini bitiren, tevekkül edenlerin işini en iyi yoluna koyan.", 66),
            EsmaName("الْقَوِيّ", "El-Kaviyy", "Kudreti en üstün ve hiç azalmayan.", 116),
            EsmaName("الْمَتِين", "El-Metin", "Kuvvet ve kudret kaynağı, pek güçlü.", 500),
            EsmaName("الْوَلِيّ", "El-Veliyy", "İnananların dostu, onları seven ve yardım eden.", 46),
            EsmaName("الْحَمِيد", "El-Hamid", "Her türlü hamd ve senaya layık olan.", 62),
            EsmaName("الْمُحْصِي", "El-Muhsi", "Yarattığı ve yaratacağı bütün varlıkların sayısını bilen.", 148),
            EsmaName("الْمُبْدِئ", "El-Mubdi", "Mahlukatı maddesiz ve örneksiz olarak baştan yaratan.", 57),
            EsmaName("الْمُعِيد", "El-Muid", "Yaratılmışları yok ettikten sonra tekrar yaratan.", 124),
            EsmaName("الْمُحْيِي", "El-Muhyi", "İhya eden, dirilten, can veren.", 68),
            EsmaName("الْمُمِيت", "El-Mumit", "Her canlıya ölümü tattıran.", 490),
            EsmaName("الْحَيّ", "El-Hayy", "Ezeli ve ebedi hayat sahibi.", 18),
            EsmaName("الْقَيُّوم", "El-Kayyum", "Varlıkları diri tutan, zatı ile kaim olan.", 156),
            EsmaName("الْوَاجِد", "El-Vacid", "Kendisinden hiçbir şey gizli kalmayan, dilediğini bulan.", 196),
            EsmaName("الْمَاجِد", "El-Macid", "Kadri ve şanı büyük, kerem ve müsamahası bol.", 48),
            EsmaName("الْوَاحِد", "El-Vahid", "Zat, sıfat ve fiillerinde benzeri olmayan, tek.", 19),
            EsmaName("الصَّمَد", "Es-Samed", "Hiçbir şeye muhtaç olmayan, herkesin muhtaç olduğu.", 134),
            EsmaName("الْقَادِر", "El-Kadir", "Dilediğini dilediği gibi yaratmaya muktedir olan.", 305),
            EsmaName("الْمُقْتَدِر", "El-Muktedir", "Dilediği gibi tasarruf eden, her şeyi kolayca yaratan.", 744),
            EsmaName("الْمُقَدِّم", "El-Mukaddim", "Dilediğini yükselten, öne geçiren.", 184),
            EsmaName("الْمُؤَخِّر", "El-Muahhir", "Dilediğini alçaltan, sona geriye bırakan.", 847),
            EsmaName("الْأَوَّل", "El-Evvel", "Ezeli olan, varlığının başlangıcı olmayan.", 37),
            EsmaName("الْآخِر", "El-Ahir", "Ebedi olan, varlığının sonu olmayan.", 801),
            EsmaName("الظَّاهِر", "Ez-Zahir", "Varlığı sayısız delillerle açık olan.", 1106),
            EsmaName("الْبَاطِن", "El-Batin", "Akılların idrak edemeyeceği, yüceliği gizli olan.", 62),
            EsmaName("الْوَالِي", "El-Vali", "Bütün kâinatı idare eden.", 47),
            EsmaName("الْمُتَعَالِي", "El-Müteali", "Son derece yüce olan.", 551),
            EsmaName("الْبَرّ", "El-Berr", "İyilik ve ihsanı bol, iyilik ve güzellik sahibi.", 202),
            EsmaName("التَّوَّاب", "Et-Tevvab", "Tövbeleri kabul edip, günahları bağışlayan.", 409),
            EsmaName("الْمُنْتَقِم", "El-Müntekim", "Asilerin, zalimlerin cezasını veren.", 630),
            EsmaName("الْعَفُوّ", "El-Afuvv", "Affeden, mağfiret eden.", 156),
            EsmaName("الرَّؤُوف", "Er-Rauf", "Çok merhametli, pek şefkatli.", 287),
            EsmaName("مَالِكُ الْمُلْك", "Malikü'l-Mülk", "Mülkün, her varlığın sahibi.", 212),
            EsmaName("ذُو الْجَلَالِ وَالْإِكْرَام", "Zü'l-Celali ve'l-İkram", "Celal, büyüklük ve ikram sahibi.", 1100),
            EsmaName("الْمُقْسِط", "El-Muksit", "Bütün işlerini denk, birbirine uygun ve yerli yerinde yapan.", 209),
            EsmaName("الْجَامِع", "El-Cami", "İstediğini istedigi zaman istediği yerde toplayan.", 114),
            EsmaName("الْغَنِيّ", "El-Ganiyy", "Çok zengin, hiç bir şeye muhtaç olmayan.", 1060),
            EsmaName("الْمُغْنِي", "El-Mugni", "Dilediğine zenginlik veren.", 1100),
            EsmaName("الْمَانِع", "El-Mani", "Bazı şeylerin meydana gelmesine izin vermeyen.", 161),
            EsmaName("الضَّارّ", "Ed-Darr", "Elem ve zarar verecek şeyleri yaratan.", 1001),
            EsmaName("النَّافِع", "En-Nafi", "Hayır ve menfaat verecek şeyleri yaratan.", 201),
            EsmaName("النُّور", "En-Nur", "Alemleri nurlandıran, dilediğine nur veren.", 256),
            EsmaName("الْهَادِي", "El-Hadi", "Hidayet veren, doğru yolu gösteren.", 20),
            EsmaName("الْبَدِيع", "El-Bedi", "Eşi ve benzeri olmayan güzellikler yaratan.", 86),
            EsmaName("الْبَاقِي", "El-Baki", "Varlığının sonu olmayan, ebedi olan.", 113),
            EsmaName("الْوَارِث", "El-Varis", "Her şeyin asıl sahibi olan.", 707),
            EsmaName("الرَّشِيد", "Er-Reşid", "İrşada muhtaç olmayan, doğru yolu gösteren.", 514),
            EsmaName("الصَّبُور", "Es-Sabur", "Ceza vermede acele etmeyen.", 298)
        )
    }
}