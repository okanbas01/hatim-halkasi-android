package com.example.sharedkhatm

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    companion object {
        // ✅ Tek yerden aç/kapa (ŞU AN KAPALI)
        private const val ENABLE_QURAN_GUIDE_CARD = false
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI
    private lateinit var txtNextPrayerName: TextView
    private lateinit var txtNextPrayerTime: TextView
    private lateinit var txtPrayerDesc: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var txtLocationName: TextView
    private lateinit var imgLocationStatus: ImageView
    private lateinit var cardPrayerTimes: CardView
    private lateinit var imgProfile: ImageView
    private lateinit var chipLocation: View

    // ✅ Gönül Rehberi kartı
    private lateinit var cardQuranGuide: MaterialCardView

    private val prayerViewModel: PrayerTimesViewModel by activityViewModels()

    // ✅ FeatureGate listener
    private var gateReg: ListenerRegistration? = null
    @Volatile private var gonulEnabled: Boolean = true
    @Volatile private var gonulDisabledMsg: String = "Bu özellik şu an bakımda."

    // Vakit Text
    private lateinit var timeFajr: TextView
    private lateinit var timeSunrise: TextView
    private lateinit var timeDhuhr: TextView
    private lateinit var timeAsr: TextView
    private lateinit var timeMaghrib: TextView   // ✅ EKLENDİ
    private lateinit var timeIsha: TextView

    private lateinit var txtDailyVerse: TextView
    private lateinit var txtDailySource: TextView

    private val PREFS_LOC = "LocationPrefs"
    private var countDownTimer: CountDownTimer? = null

    private val turkeyCities = arrayOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin", "Aydın", "Balıkesir",
        "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli",
        "Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari",
        "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir",
        "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir",
        "Niğde", "Ordu", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat",
        "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman",
        "Kırıkkale", "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"
    )

    // Günün Sözü (Kısa Örnek)
    private val dailyVerses = listOf(
        Pair("Şüphesiz güçlükle beraber bir kolaylık vardır.", "İnşirah Suresi, 5. Ayet"),
        Pair("Rabbin seni terk etmedi ve sana darılmadı.", "Duha Suresi, 3. Ayet"),
        Pair("Allah, kimseye gücünün yeteceğinden fazlasını yüklemez.", "Bakara Suresi, 286. Ayet"),
        Pair("Sabredenleri müjdele.", "Bakara Suresi, 155. Ayet"),
        Pair("Bilsinler ki, kalpler ancak Allah'ı anmakla huzur bulur.", "Ra'd Suresi, 28. Ayet"),
        Pair("Yeryüzünde böbürlenerek yürüme.", "İsra Suresi, 37. Ayet"),
        Pair("İyilikle kötülük bir olmaz. Kötülüğü en güzel bir şekilde sav.", "Fussilet Suresi, 34. Ayet"),
        Pair("De ki: Rabbim ilmimi artır.", "Taha Suresi, 114. Ayet"),
        Pair("Müminler ancak kardeştirler.", "Hucurat Suresi, 10. Ayet"),
        Pair("O, her an yeni bir iştedir.", "Rahman Suresi, 29. Ayet"),
        Pair("Allah size yardım ederse, artık size galip gelecek yoktur.", "Al-i İmran, 160. Ayet"),
        Pair("Şüphesiz Allah, tevekkül edenleri sever.", "Al-i İmran, 159. Ayet"),
        Pair("Rabbimiz! Bize dünyada da iyilik ver, ahirette de iyilik ver.", "Bakara Suresi, 201. Ayet"),
        Pair("Kim Allah'tan korkarsa, Allah ona bir çıkış yolu ihsan eder.", "Talak Suresi, 2. Ayet"),
        Pair("Allah sabredenlerle beraberdir.", "Bakara Suresi, 153. Ayet"),
        Pair("Sizin en hayırlınız, Kur'an'ı öğrenen ve öğretendir.", "Hadis-i Şerif"),
        Pair("Temizlik imanın yarısıdır.", "Hadis-i Şerif"),
        Pair("Kolaylaştırınız, zorlaştırmayınız; müjdeleyiniz, nefret ettirmeyiniz.", "Hadis-i Şerif"),
        Pair("Ameller niyetlere göredir.", "Hadis-i Şerif"),
        Pair("Hiçbiriniz, kendisi için istediğini kardeşi için de istemedikçe iman etmiş olmaz.", "Hadis-i Şerif"),
        Pair("Merhamet etmeyene merhamet olunmaz.", "Hadis-i Şerif"),
        Pair("Dua, müminin silahıdır.", "Hadis-i Şerif"),
        Pair("Güzel söz sadakadır.", "Hadis-i Şerif"),
        Pair("İnsanların en hayırlısı, insanlara faydalı olandır.", "Hadis-i Şerif"),
        Pair("Cennet annelerin ayakları altındadır.", "Hadis-i Şerif"),
        Pair("Bizi aldatan bizden değildir.", "Hadis-i Şerif"),
        Pair("Veren el, alan elden üstündür.", "Hadis-i Şerif"),
        Pair("İki günü eşit olan ziyandadır.", "Hadis-i Şerif"),
        Pair("Tebessüm etmek sadakadır.", "Hadis-i Şerif"),
        Pair("Mümin, bir delikten iki defa ısırılmaz.", "Hadis-i Şerif"),
        Pair("Şüphesiz namaz, hayâsızlıktan ve kötülükten alıkoyar.", "Ankebut Suresi, 45. Ayet"),
        Pair("Rabbimiz! Bizi doğru yola ilettikten sonra kalplerimizi eğriltme.", "Al-i İmran, 8. Ayet"),
        Pair("Allah, tevbe edenleri sever.", "Bakara Suresi, 222. Ayet"),
        Pair("Kullarıma söyle, sözün en güzelini söylesinler.", "İsra Suresi, 53. Ayet"),
        Pair("Gevşemeyin, üzülmeyin; eğer inanıyorsanız üstün gelecek olan sizsiniz.", "Al-i İmran, 139. Ayet"),
        Pair("O (Allah), hanginizin daha güzel amel yapacağını denemek için ölümü ve hayatı yarattı.", "Mülk Suresi, 2. Ayet"),
        Pair("Andolsun, insanı biz yarattık ve nefsinin ona ne vesveseler verdiğini biliriz.", "Kaf Suresi, 16. Ayet"),
        Pair("Allah’ın rahmetinden ümidinizi kesmeyin.", "Zümer Suresi, 53. Ayet"),
        Pair("Biz seni ancak alemlere rahmet olarak gönderdik.", "Enbiya Suresi, 107. Ayet"),
        Pair("Müminler o kimselerdir ki, Allah anıldığı zaman kalpleri titrer.", "Enfal Suresi, 2. Ayet"),
        Pair("Komşusu açken tok yatan bizden değildir.", "Hadis-i Şerif"),
        Pair("Hasetten sakının. Çünkü ateşin odunu yiyip bitirdiği gibi haset de iyilikleri yer bitirir.", "Hadis-i Şerif"),
        Pair("Kim bir hayra vesile olursa, o hayrı yapan gibi sevap alır.", "Hadis-i Şerif"),
        Pair("Allah sizin dış görünüşünüze ve mallarınıza bakmaz, kalplerinize ve amellerinize bakar.", "Hadis-i Şerif"),
        Pair("Müslüman, elinden ve dilinden diğer Müslümanların emin olduğu kimsedir.", "Hadis-i Şerif"),
        Pair("Kıyamet kopuyor olsa bile, elinizdeki fidanı dikiniz.", "Hadis-i Şerif"),
        Pair("Mazlumun bedduasından sakın. Çünkü onunla Allah arasında perde yoktur.", "Hadis-i Şerif"),
        Pair("Sadaka malı eksiltmez.", "Hadis-i Şerif"),
        Pair("Hayra anahtar, şerre kilit olun.", "Hadis-i Şerif"),
        Pair("Ölüm, vaiz olarak sana yeter.", "Hadis-i Şerif"),
        Pair("İlim Çin’de de olsa gidip alınız.", "Hadis-i Şerif"),
        Pair("Utanmadıktan sonra dilediğini yap.", "Hadis-i Şerif"),
        Pair("İşçiye ücretini, teri kurumadan veriniz.", "Hadis-i Şerif"),
        Pair("Anne ve babasına iyilik edene müjdeler olsun! Allah onun ömrünü uzatır.", "Hadis-i Şerif"),
        Pair("Sizden biriniz, kendisi için sevdiğini kardeşi için de sevmedikçe iman etmiş olmaz.", "Hadis-i Şerif"),
        Pair("Kim ilim öğrenmek için yola çıkarsa, Allah ona cennet yolunu kolaylaştırır.", "Hadis-i Şerif"),
        Pair("Mümin, müminin aynasıdır.", "Hadis-i Şerif"),
        Pair("Sabır, ilk sarsıntı anındadır.", "Hadis-i Şerif"),
        Pair("Hayra vesile olan, hayrı yapan gibidir.", "Hadis-i Şerif"),
        Pair("Zenginlik mal çokluğu değil, gönül tokluğudur.", "Hadis-i Şerif"),
        Pair("Kim susarsa kurtulur.", "Hadis-i Şerif"),
        Pair("İnsanlara merhamet etmeyene, Allah da merhamet etmez.", "Hadis-i Şerif"),
        Pair("Müslüman, Müslümanın kardeşidir. Ona zulmetmez, onu yardımsız bırakmaz.", "Hadis-i Şerif"),
        Pair("Bir kötülük gördüğünüzde onu elinizle düzeltin.", "Hadis-i Şerif"),
        Pair("Dünya müminin zindanı, kâfirin cennetidir.", "Hadis-i Şerif"),
        Pair("En faziletli amel, vaktinde kılınan namazdır.", "Hadis-i Şerif"),
        Pair("Kişi sevdiği ile beraberdir.", "Hadis-i Şerif"),
        Pair("Danışan dağları aşmış, danışmayan düz yolda şaşmış.", "Hadis-i Şerif"),
        Pair("Güçlü kimse, güreşte rakibini yenen değil, öfke anında nefsine hakim olandır.", "Hadis-i Şerif"),
        Pair("Allah güzeldir, güzeli sever.", "Hadis-i Şerif"),
        Pair("Komşu hakkı, Tanrı hakkıdır.", "Hadis-i Şerif"),
        Pair("Misafire ikram etmek imandandır.", "Hadis-i Şerif"),
        Pair("Yalan, rızkı azaltır.", "Hadis-i Şerif"),
        Pair("Sıla-i rahim ömrü uzatır.", "Hadis-i Şerif"),
        Pair("Hediyeleşin, birbirinizi sevin.", "Hadis-i Şerif"),
        Pair("Mümin, bal arısı gibidir; hep güzel şeyler yer ve hep güzel şeyler üretir.", "Hadis-i Şerif"),
        Pair("İnsanlar uykudadır, ölünce uyanırlar.", "Hadis-i Şerif"),
        Pair("Kalbinde zerre kadar kibir olan cennete giremez.", "Hadis-i Şerif"),
        Pair("Mazlumun duası ile Allah arasında perde yoktur.", "Hadis-i Şerif"),
        Pair("Hiç ölmeyecekmiş gibi dünya için, yarın ölecekmiş gibi ahiret için çalış.", "Hadis-i Şerif"),
        Pair("Namaz dinin direğidir.", "Hadis-i Şerif"),
        Pair("Cennet kılıçların gölgesi altındadır.", "Hadis-i Şerif"),
        Pair("Her kim Allah için tevazu gösterirse, Allah onu yükseltir.", "Hadis-i Şerif"),
        Pair("Sizin en hayırlınız, ahlakı en güzel olanınızdır.", "Hadis-i Şerif"),
        Pair("Allah'a ve ahiret gününe iman eden, ya hayır söylesin ya da sussun.", "Hadis-i Şerif"),
        Pair("İman, sabır ve hoşgörüdür.", "Hadis-i Şerif"),
        Pair("Zulümden sakınınız. Çünkü zulüm, kıyamet gününde karanlıklardır.", "Hadis-i Şerif"),
        Pair("Sadaka, suyun ateşi söndürdüğü gibi hataları söndürür.", "Hadis-i Şerif"),
        Pair("Müslüman, dilinden ve elinden Müslümanların zarar görmediği kimsedir.", "Hadis-i Şerif"),
        Pair("Kıskançlıktan kaçının; çünkü ateşin odunu yediği gibi kıskançlık da iyilikleri yer bitirir.", "Hadis-i Şerif"),
        Pair("Allah sizin suretlerinize ve mallarınıza bakmaz, ancak kalplerinize ve amellerinize bakar.", "Hadis-i Şerif"),
        Pair("Dünya, ahiretin tarlasıdır.", "Hadis-i Şerif"),
        Pair("İstişare eden pişman olmaz.", "Hadis-i Şerif"),
        Pair("Acele şeytandan, teenni (düşünerek hareket etmek) Allah'tandır.", "Hadis-i Şerif"),
        Pair("Tövbe eden, hiç günah işlememiş gibidir.", "Hadis-i Şerif"),
        Pair("İki nimet vardır ki, insanların çoğu onların kıymetini bilmez: Sağlık ve boş vakit.", "Hadis-i Şerif"),
        Pair("Allah katında amellerin en sevimlisi, az da olsa devamlı olanıdır.", "Hadis-i Şerif"),
        Pair("Haksızlık karşısında susan dilsiz şeytandır.", "Hadis-i Şerif"),
        Pair("Cömert insan Allah'a, insanlara ve cennete yakındır.", "Hadis-i Şerif"),
        Pair("Rızkın onda dokuzu ticarettedir.", "Hadis-i Şerif"),
        Pair("Çalışan, Allah'ın dostudur.", "Hadis-i Şerif"),
        Pair("Namazın dindeki yeri, başın vücuttaki yeri gibidir.", "Hadis-i Şerif"),
        Pair("Bir Müslümanın, din kardeşine üç günden fazla küs durması helal değildir.", "Hadis-i Şerif"),
        Pair("Gıybet, kardeşinin ölü etini yemek gibidir.", "Hadis-i Şerif"),
        Pair("Söz taşıyan (koğuculuk yapan) cennete giremez.", "Hadis-i Şerif"),
        Pair("Anne cennet kapılarının ortasındadır.", "Hadis-i Şerif"),
        Pair("Babanın evladına duası, peygamberin ümmetine duası gibidir.", "Hadis-i Şerif"),
        Pair("Yetimi koruyup kollayanla ben, cennette yan yana olacağız.", "Hadis-i Şerif"),
        Pair("Kim bir oruçluya iftar ettirirse, oruçlu kadar sevap kazanır.", "Hadis-i Şerif"),
        Pair("Oruç, cehennem ateşine karşı bir kalkandır.", "Hadis-i Şerif"),
        Pair("Sahur yemeği yiyiniz, çünkü sahurda bereket vardır.", "Hadis-i Şerif"),
        Pair("Cuma günü yapılan duaların kabul olduğu bir saat vardır.", "Hadis-i Şerif"),
        Pair("Kim Cuma günü Gusül abdesti alırsa günahlarına kefaret olur.", "Hadis-i Şerif"),
        Pair("Kur'an okuyunuz. Çünkü o, kıyamet günü sahibine şefaatçi olarak gelecektir.", "Hadis-i Şerif"),
        Pair("Sizin en hayırlınız, eşine karşı en hayırlı olanınızdır.", "Hadis-i Şerif"),
        Pair("Kadınlar size Allah'ın emanetidir.", "Hadis-i Şerif"),
        Pair("Cennet, kılıçların gölgesi altındadır.", "Hadis-i Şerif"),
        Pair("Allah, temizdir ve temizi sever.", "Hadis-i Şerif"),
        Pair("Misvak kullanmak, ağzın temizliği ve Rabbin rızasıdır.", "Hadis-i Şerif"),
        Pair("Abdest üzerine abdest almak, nur üstüne nurdur.", "Hadis-i Şerif"),
        Pair("Namaz, müminin miracıdır.", "Hadis-i Şerif"),
        Pair("Gözümün nuru namazdır.", "Hadis-i Şerif"),
        Pair("Sabah namazının iki rekat sünneti, dünyadan ve içindekilerden hayırlıdır.", "Hadis-i Şerif"),
        Pair("İkindi namazını terk edenin ameli boşa gider.", "Hadis-i Şerif"),
        Pair("Yatsı namazını cemaatle kılan, gecenin yarısını ibadetle geçirmiş gibidir.", "Hadis-i Şerif"),
        Pair("Sabah namazını cemaatle kılan, bütün geceyi ibadetle geçirmiş gibidir.", "Hadis-i Şerif"),
        Pair("Ezan ile kamet arasında yapılan dua reddolunmaz.", "Hadis-i Şerif"),
        Pair("Secde, kulun Rabbine en yakın olduğu andır.", "Hadis-i Şerif"),
        Pair("Allah'ım! Beni, anne babamı ve bütün müminleri bağışla.", "İbrahim Suresi, 41. Ayet"),
        Pair("Rabbimiz! Bize dünyada da iyilik ver, ahirette de iyilik ver ve bizi ateş azabından koru.", "Bakara Suresi, 201. Ayet"),
        Pair("Rabbimiz! Üzerimize sabır yağdır ve canımızı Müslüman olarak al.", "Araf Suresi, 126. Ayet"),
        Pair("Rabbim! Gireceğim yere dürüstlükle girmemi, çıkacağım yerden dürüstlükle çıkmamı sağla.", "İsra Suresi, 80. Ayet"),
        Pair("Rabbim! Göğsümü genişlet, işimi kolaylaştır.", "Taha Suresi, 25-26. Ayet"),
        Pair("Rabbimiz! Bizi hidayete erdirdikten sonra kalplerimizi eğriltme.", "Al-i İmran, 8. Ayet"),
        Pair("Rabbim! Beni ve soyumu namazı dosdoğru kılanlardan eyle.", "İbrahim Suresi, 40. Ayet"),
        Pair("Rabbimiz! Hesap görüleceği gün beni, ana babamı ve müminleri bağışla.", "İbrahim Suresi, 41. Ayet"),
        Pair("Ey Rabbim! Şeytanların vesveselerinden sana sığınırım.", "Müminun Suresi, 97. Ayet"),
        Pair("Rabbimiz! Bize gözümüzü aydınlatacak eşler ve zürriyetler bağışla.", "Furkan Suresi, 74. Ayet"),
        Pair("Rabbim! İlmimi artır.", "Taha Suresi, 114. Ayet"),
        Pair("Hasbunallahu ve ni'mel vekil (Allah bize yeter, O ne güzel vekildir).", "Al-i İmran, 173. Ayet"),
        Pair("La ilahe illa ente sübhaneke inni küntü minezzalimin.", "Enbiya Suresi, 87. Ayet"),
        Pair("Allah, kuluna kafi değil midir?", "Zümer Suresi, 36. Ayet"),
        Pair("Şüphesiz benim namazım, ibadetlerim, hayatım ve ölümüm alemlerin Rabbi olan Allah içindir.", "Enam Suresi, 162. Ayet"),
        Pair("De ki: Eğer Allah'ı seviyorsanız bana uyun ki, Allah da sizi sevsin.", "Al-i İmran, 31. Ayet"),
        Pair("Müminler ancak o kimselerdir ki, Allah anıldığı zaman kalpleri ürperir.", "Enfal Suresi, 2. Ayet"),
        Pair("Onlar, ayakta dururken, otururken, yanları üzerine yatarken Allah'ı zikrederler.", "Al-i İmran, 191. Ayet"),
        Pair("Ey iman edenler! Allah'ı çokça zikredin.", "Ahzab Suresi, 41. Ayet"),
        Pair("Kalpler ancak Allah'ı zikretmekle huzur bulur.", "Rad Suresi, 28. Ayet"),
        Pair("Beni zikredin ki ben de sizi zikredeyim.", "Bakara Suresi, 152. Ayet"),
        Pair("Allah'ın rahmetinden ümidinizi kesmeyin.", "Zümer Suresi, 53. Ayet"),
        Pair("Şüphesiz Allah, çokça tevbe edenleri sever.", "Bakara Suresi, 222. Ayet"),
        Pair("Allah, size kolaylık diler, zorluk dilemez.", "Bakara Suresi, 185. Ayet"),
        Pair("İyilikle kötülük bir olmaz. Sen (kötülüğü) en güzel olanla sav.", "Fussilet Suresi, 34. Ayet"),
        Pair("Affet, marufu emret ve cahillerden yüz çevir.", "Araf Suresi, 199. Ayet"),
        Pair("Muhakkak ki Allah, adaleti, iyiliği ve akrabaya yardım etmeyi emreder.", "Nahl Suresi, 90. Ayet"),
        Pair("Yiyin, için fakat israf etmeyin.", "Araf Suresi, 31. Ayet"),
        Pair("Allah, israf edenleri sevmez.", "Enam Suresi, 141. Ayet"),
        Pair("Biz insanı en güzel biçimde yarattık.", "Tin Suresi, 4. Ayet"),
        Pair("İnsan için ancak çalıştığının karşılığı vardır.", "Necm Suresi, 39. Ayet"),
        Pair("Kim zerre miktarı hayır işlerse onu görür.", "Zilzal Suresi, 7. Ayet"),
        Pair("Kim zerre miktarı şer işlerse onu görür.", "Zilzal Suresi, 8. Ayet"),
        Pair("O gün, ne mal fayda verir ne de evlat.", "Şuara Suresi, 88. Ayet"),
        Pair("Her nefis ölümü tadacaktır.", "Al-i İmran, 185. Ayet"),
        Pair("Kıyamet saati mutlaka gelecektir.", "Hicr Suresi, 85. Ayet"),
        Pair("O gün, sura üflenir ve bölük bölük gelirsiniz.", "Nebe Suresi, 18. Ayet"),
        Pair("Rabbinize yalvara yakara ve gizlice dua edin.", "Araf Suresi, 55. Ayet"),
        Pair("Bana dua edin, size icabet edeyim.", "Mümin Suresi, 60. Ayet"),
        Pair("Allah, rızkı dilediğine bol verir, dilediğine kısar.", "Rad Suresi, 26. Ayet"),
        Pair("Eğer Allah sana bir zarar dokundurursa, onu O'ndan başka giderecek yoktur.", "Yunus Suresi, 107. Ayet"),
        Pair("Göklerde ve yerde ne varsa hepsi Allah'ı tesbih eder.", "Haşr Suresi, 1. Ayet"),
        Pair("Ey iman edenler! Allah'tan korkun ve doğrularla beraber olun.", "Tevbe Suresi, 119. Ayet"),
        Pair("Allah, işiten ve bilendir.", "Bakara Suresi, 224. Ayet"),
        Pair("O, yarattığı her şeyi güzel yaratmıştır.", "Secde Suresi, 7. Ayet"),
        Pair("Şüphesiz Allah, tevekkül edenleri sever.", "Al-i İmran, 159. Ayet"),
        Pair("Yeryüzünde yürüyen her canlının rızkı Allah'a aittir.", "Hud Suresi, 6. Ayet"),
        Pair("Allah, sabredenlerle beraberdir.", "Enfal Suresi, 46. Ayet"),
        Pair("Müminler, ancak kardeştirler. Öyleyse kardeşlerinizin arasını düzeltin.", "Hucurat Suresi, 10. Ayet"),
        Pair("Kim Allah'a ve ahiret gününe iman ediyorsa, misafirine ikram etsin.", "Hadis-i Şerif"),
        Pair("Kim Allah'a ve ahiret gününe iman ediyorsa, komşusuna eziyet etmesin.", "Hadis-i Şerif"),
        Pair("Kardeşine gülümsemen senin için bir sadakadır.", "Hadis-i Şerif"),
        Pair("İnsanlara teşekkür etmeyen, Allah'a şükretmiş olmaz.", "Hadis-i Şerif"),
        Pair("Mazlumun bedduasından sakının, çünkü onunla Allah arasında perde yoktur.", "Hadis-i Şerif"),
        Pair("Sözü söyleyen değil, söyleten önemlidir.", "Hadis-i Şerif"),
        Pair("İlim, müminin yitiğidir; nerede bulursa onu alır.", "Hadis-i Şerif"),
        Pair("Mümin, bir delikten iki defa ısırılmaz.", "Hadis-i Şerif"),
        Pair("Kolaylaştırınız, zorlaştırmayınız; müjdeleyiniz, nefret ettirmeyiniz.", "Hadis-i Şerif"),
        Pair("Amellerin kıymeti niyetlere göredir.", "Hadis-i Şerif"),
        Pair("Sizin en hayırlınız, eşine ve ailesine en hayırlı olanınızdır.", "Hadis-i Şerif"),
        Pair("Cennet, annelerin ayakları altındadır.", "Hadis-i Şerif"),
        Pair("Veren el, alan elden üstündür.", "Hadis-i Şerif"),
        Pair("Müslüman, elinden ve dilinden insanların emin olduğu kimsedir.", "Hadis-i Şerif"),
        Pair("Haset, ateşin odunu yediği gibi iyilikleri yer bitirir.", "Hadis-i Şerif"),
        Pair("Öfke şeytandandır, şeytan ateşten yaratılmıştır. Ateş su ile söndürülür.", "Hadis-i Şerif"),
        Pair("Biriniz öfkelendiğinde sussun.", "Hadis-i Şerif"),
        Pair("Merhamet etmeyene merhamet olunmaz.", "Hadis-i Şerif"),
        Pair("Allah güzeldir, güzelliği sever.", "Hadis-i Şerif"),
        Pair("Temizlik imanın yarısıdır.", "Hadis-i Şerif"),
        Pair("Namaz, dinin direğidir.", "Hadis-i Şerif"),
        Pair("Oruç sabrın yarısıdır.", "Hadis-i Şerif"),
        Pair("Her şeyin bir zekatı vardır, bedenin zekatı da oruçtur.", "Hadis-i Şerif"),
        Pair("Kur'an okuyunuz; zira o, kıyamet gününde sahibine şefaatçi olacaktır.", "Hadis-i Şerif"),
        Pair("Sizin en hayırlınız, Kur'an'ı öğrenen ve öğretendir.", "Hadis-i Şerif"),
        Pair("İki günü eşit olan ziyandadır.", "Hadis-i Şerif"),
        Pair("Hiçbiriniz, kendisi için istediğini kardeşi için de istemedikçe iman etmiş olmaz.", "Hadis-i Şerif"),
        Pair("Kim bir hayra vesile olursa, o hayrı işleyen gibi sevap alır.", "Hadis-i Şerif"),
        Pair("Mümin, müminin aynasıdır.", "Hadis-i Şerif"),
        Pair("Kişi sevdiği ile beraberdir.", "Hadis-i Şerif"),
        Pair("Dünya müminin zindanı, kafirin cennetidir.", "Hadis-i Şerif"),
        Pair("Allah katında en sevimli amel, az da olsa devamlı olanıdır.", "Hadis-i Şerif"),
        Pair("Sadaka belayı defeder ve ömrü uzatır.", "Hadis-i Şerif"),
        Pair("Dua müminin silahıdır.", "Hadis-i Şerif"),
        Pair("Ezan ile kamet arasında yapılan dua reddedilmez.", "Hadis-i Şerif"),
        Pair("Cuma günü öyle bir an vardır ki, o anda yapılan dua reddedilmez.", "Hadis-i Şerif"),
        Pair("Zulümden sakının, çünkü zulüm kıyamet gününde karanlıklardır.", "Hadis-i Şerif"),
        Pair("Güçlü kimse, güreşte rakibini yenen değil, öfke anında nefsine hakim olandır.", "Hadis-i Şerif"),
        Pair("İstişare eden pişman olmaz.", "Hadis-i Şerif"),
        Pair("Tövbe eden, hiç günah işlememiş gibidir.", "Hadis-i Şerif"),
        Pair("Allah'ın rızası, anne babanın rızasındadır.", "Hadis-i Şerif"),
        Pair("Cennetin anahtarı namazdır.", "Hadis-i Şerif"),
        Pair("Namazın anahtarı abdesttir.", "Hadis-i Şerif"),
        Pair("Gıybet, kardeşinin ölü etini yemek gibidir.", "Hadis-i Şerif"),
        Pair("Yalan, rızkı daraltır.", "Hadis-i Şerif"),
        Pair("Sıla-i rahim, ömrü uzatır.", "Hadis-i Şerif"),
        Pair("Hediyeleşin, birbirinizi sevin.", "Hadis-i Şerif"),
        Pair("İnsanlar uykudadır, ölünce uyanırlar.", "Hadis-i Şerif"),
        Pair("Her kim Allah için tevazu gösterirse, Allah onu yükseltir.", "Hadis-i Şerif"),
        Pair("Komşusu açken tok yatan bizden değildir.", "Hadis-i Şerif"),
        Pair("İşçinin hakkını, teri kurumadan veriniz.", "Hadis-i Şerif"),
        Pair("Bizi aldatan bizden değildir.", "Hadis-i Şerif"),
        Pair("Şüphesiz Allah, sizin suretlerinize ve mallarınıza bakmaz; kalplerinize ve amellerinize bakar.", "Hadis-i Şerif"),
        Pair("Hayra anahtar, şerre kilit olun.", "Hadis-i Şerif"),
        Pair("Ölüm, nasihatçi olarak yeter.", "Hadis-i Şerif"),
        Pair("Akıllı kimse, nefsini hesaba çeken ve ölümden sonrası için çalışandır.", "Hadis-i Şerif"),
        Pair("Aciz kimse, nefsine uyup Allah'tan bağışlanma umandır.", "Hadis-i Şerif"),
        Pair("İyilik, güzel ahlaktır.", "Hadis-i Şerif"),
        Pair("Müslüman, insanların elinden ve dilinden emin olduğu kimsedir.", "Hadis-i Şerif"),
        Pair("Kalbinde zerre kadar kibir olan cennete giremez.", "Hadis-i Şerif"),
        Pair("Doğruluk iyiliğe, iyilik cennete götürür.", "Hadis-i Şerif"),
        Pair("Yalan kötülüğe, kötülük cehenneme götürür.", "Hadis-i Şerif"),
        Pair("Münafığın alameti üçtür: Konuştuğunda yalan söyler, söz verdiğinde sözünde durmaz, emanete hıyanet eder.", "Hadis-i Şerif"),
        Pair("Kim bir oruçluya iftar ettirirse, oruçlunun sevabından bir şey eksilmeden ona da sevap yazılır.", "Hadis-i Şerif"),
        Pair("Ramazan ayı, sabır ayıdır. Sabrın sevabı ise cennettir.", "Hadis-i Şerif"),
        Pair("Allah'ım! Senden hidayet, takva, iffet ve gönül zenginliği isterim.", "Hadis-i Şerif"),
        Pair("Allah'ım! Beni, sevgini ve beni sana yaklaştıracak olanların sevgisini nasip eyle.", "Hadis-i Şerif"),
        Pair("Ey kalpleri halden hale çeviren Allah'ım! Kalbimi dinin üzere sabit kıl.", "Hadis-i Şerif"),
        Pair("Allah'ım! Cehennem azabından, kabir azabından, hayat ve ölüm fitnesinden sana sığınırım.", "Hadis-i Şerif"),
        Pair("Allah'ım! Bildiğim ve bilmediğim bütün hayırları senden isterim.", "Hadis-i Şerif"),
        Pair("Allah'ım! Bildiğim ve bilmediğim bütün şerlerden sana sığınırım.", "Hadis-i Şerif"),
        Pair("Rabbimiz! Bize dünyada da iyilik ver, ahirette de iyilik ver ve bizi ateş azabından koru.", "Bakara Suresi, 201. Ayet"),
        Pair("Rabbimiz! Bizi hidayete erdirdikten sonra kalplerimizi eğriltme.", "Al-i İmran, 8. Ayet"),
        Pair("Rabbim! Beni ve soyumu namazı dosdoğru kılanlardan eyle.", "İbrahim Suresi, 40. Ayet"),
        Pair("Rabbim! İlmimi artır.", "Taha Suresi, 114. Ayet"),
        Pair("Rabbim! Göğsümü genişlet, işimi kolaylaştır.", "Taha Suresi, 25-26. Ayet"),
        Pair("Şüphesiz benim namazım, ibadetlerim, hayatım ve ölümüm alemlerin Rabbi olan Allah içindir.", "Enam Suresi, 162. Ayet"),
        Pair("Hasbunallahu ve ni'mel vekil (Allah bize yeter, O ne güzel vekildir).", "Al-i İmran, 173. Ayet"),
        Pair("La ilahe illa ente sübhaneke inni küntü minezzalimin.", "Enbiya Suresi, 87. Ayet"),
        Pair("Allah, kuluna kafi değil midir?", "Zümer Suresi, 36. Ayet"),
        Pair("Ey iman edenler! Sabır ve namazla Allah'tan yardım isteyin.", "Bakara Suresi, 153. Ayet"),
        Pair("Şüphesiz Allah, sabredenlerle beraberdir.", "Bakara Suresi, 153. Ayet"),
        Pair("Allah bir kimseye hayır dilerse, onu dinde fakih (anlayışlı) kılar.", "Hadis-i Şerif"),
        Pair("Mümin, bir ağaç gibidir; yaprakları dökülmez (her daim canlıdır).", "Hadis-i Şerif"),
        Pair("Cemaat rahmettir, ayrılık azaptır.", "Hadis-i Şerif"),
        Pair("Kim bir Müslümanın dünya sıkıntılarından birini giderirse, Allah da onun kıyamet günü sıkıntılarından birini giderir.", "Hadis-i Şerif"),
        Pair("Kim bir Müslümanın ayıbını örterse, Allah da dünya ve ahirette onun ayıbını örter.", "Hadis-i Şerif"),
        Pair("Allah, kulunun yardımındadır; kul, kardeşinin yardımında olduğu sürece.", "Hadis-i Şerif"),
        Pair("Sadaka vermekle mal eksilmez.", "Hadis-i Şerif"),
        Pair("Affetmek, izzeti artırır.", "Hadis-i Şerif"),
        Pair("Tevazu göstereni Allah yükseltir.", "Hadis-i Şerif")
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                if (!isAdded) return@registerForActivityResult
                saveLocationMode("GPS")
                txtLocationName.text = "Konum alınıyor..."
                imgLocationStatus.setImageResource(android.R.drawable.ic_menu_mylocation)
                checkGpsAndGetLocation()
            } else {
                if (!hasSavedLocation()) {
                    updateLocationUI(false, "Şehir Seçiniz")
                    showLocationSelectionDialog()
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ayarlar ekranından şehir değişince anında yakala
        parentFragmentManager.setFragmentResultListener("city_update_request", viewLifecycleOwner) { _, bundle ->
            val shouldRefresh = bundle.getBoolean("refresh", false)
            if (shouldRefresh) {
                val prefs = requireContext().getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
                val newCity = prefs.getString("savedCity", null)
                if (newCity != null) {
                    txtLocationName.text = "$newCity (Yükleniyor...)"
                    findCoordinatesForCity(newCity)
                }
            }
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initViews(view)

        // ✅ ÖNEMLİ: FeatureGate sadece ileride kart açılırsa devreye girsin
        if (ENABLE_QURAN_GUIDE_CARD) {
            bindGonulFeatureGate()
        }

        setDailyVerse()
        loadUserInfo()

        prayerViewModel.loadFromPrefs()
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            scheduleFromSavedPrefsIfPossible(ctx)
        }

        prayerViewModel.prayerState.observe(viewLifecycleOwner, Observer { state ->
            if (!isAdded) return@Observer
            txtLocationName.text = if (state.isLoading && state.locationName.isNotBlank()) "${state.locationName} (Yükleniyor...)" else state.locationName
            imgLocationStatus.setImageResource(
                if (state.isGps) android.R.drawable.ic_menu_mylocation else android.R.drawable.ic_menu_mapmode
            )
            timeFajr.text = state.fajr
            timeSunrise.text = state.sunrise
            timeDhuhr.text = state.dhuhr
            timeAsr.text = state.asr
            timeMaghrib.text = state.maghrib
            timeIsha.text = state.isha
            txtNextPrayerName.text = state.nextPrayerName
            txtNextPrayerTime.text = state.nextPrayerTime
            if (state.hasData) {
                startCountdown(state.nextPrayerTime, state.nextPrayerDesc)
                updateAllWidgets()
            } else {
                countDownTimer?.cancel()
                txtCountdown.text = "--:--"
                txtPrayerDesc.text = ""
            }
        })

        // Uygulama açılışında HomeActivity konum izni istiyor; izin verilince callback Activity'de kalıyor.
        // Dashboard açıldığında izin varsa ama kayıtlı konum yoksa otomatik konum al.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !hasSavedLocation()) {
            txtLocationName.text = "Konum alınıyor..."
            imgLocationStatus.setImageResource(android.R.drawable.ic_menu_mylocation)
            checkGpsAndGetLocation()
        }
    }

    private fun initViews(view: View) {
        val cardContinue = view.findViewById<CardView>(R.id.cardContinueReading)

        chipLocation = view.findViewById(R.id.chipLocation)
        imgProfile = view.findViewById(R.id.imgProfileAvatar)
        imgLocationStatus = view.findViewById(R.id.imgLocationStatus)
        cardPrayerTimes = view.findViewById(R.id.cardPrayerTimes)

        txtNextPrayerName = view.findViewById(R.id.txtNextPrayerName)
        txtNextPrayerTime = view.findViewById(R.id.txtNextPrayerTime)
        txtPrayerDesc = view.findViewById(R.id.txtPrayerDesc)
        txtCountdown = view.findViewById(R.id.txtCountdown)
        txtLocationName = view.findViewById(R.id.txtLocationName)

        timeFajr = view.findViewById(R.id.timeFajr)
        timeSunrise = view.findViewById(R.id.timeSunrise)
        timeDhuhr = view.findViewById(R.id.timeDhuhr)
        timeAsr = view.findViewById(R.id.timeAsr)
        timeMaghrib = view.findViewById(R.id.timeMaghrib) // ✅ EKLENDİ (XML id bu olmalı)
        timeIsha = view.findViewById(R.id.timeIsha)

        txtDailyVerse = view.findViewById(R.id.txtDailyVerse)
        txtDailySource = view.findViewById(R.id.txtDailySource)

        // ✅ Gönül Rehberi kartı (HARD-HIDE: flicker dahil %100 kapalı)
        cardQuranGuide = view.findViewById(R.id.cardQuranGuide)
        cardQuranGuide.visibility = View.GONE
        cardQuranGuide.isSaveEnabled = false
        cardQuranGuide.isClickable = false
        cardQuranGuide.isFocusable = false

        // ✅ Kapalıysa burada bitir: Kart asla görünmez, listener bağlanmaz
        if (!ENABLE_QURAN_GUIDE_CARD) {
            gateReg?.remove()
            gateReg = null
        } else {
            cardQuranGuide.isClickable = true
            cardQuranGuide.isFocusable = true

            cardQuranGuide.setOnClickListener {
                if (!gonulEnabled) {
                    Toast.makeText(requireContext(), gonulDisabledMsg, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, QuranChatFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        val btnNotifSettings = view.findViewById<View>(R.id.btnNotificationSettings)
        btnNotifSettings.setOnClickListener {
            val bottomSheet = PrayerSettingsBottomSheet()
            bottomSheet.show(parentFragmentManager, "PrayerSettings")
        }

        imgProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ProfileFragment())
                .addToBackStack(null).commit()
        }

        chipLocation.setOnClickListener { showLocationSelectionDialog() }

        cardContinue.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
            val type = prefs.getString("lastReadType", null)
            val value = prefs.getInt("lastReadValue", 0)
            if (type != null && value > 0) {
                val intent = Intent(requireContext(), ReadJuzActivity::class.java)
                intent.putExtra(if (type == "JUZ") "juzNumber" else "surahNumber", value)
                if (type == "JUZ") {
                    val hatimId = prefs.getString("lastReadHatimId", null)
                    if (hatimId != null) intent.putExtra("hatimId", hatimId)
                }
                // Son okunan ayet pozisyonunu geçir - direkt o ayete gidecek
                val lastAyahIndex = prefs.getInt("lastReadAyahIndex", -1)
                if (lastAyahIndex >= 0) {
                    intent.putExtra("restoreAyahIndex", lastAyahIndex)
                }
                startActivity(intent)
            } else {
                val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                bottomNav.selectedItemId = R.id.nav_quran
            }
        }

        updateContinueCardText(view)
        updateDailyProgress(view)
        updateBadgeIndicator(view)
    }
    
    /** Rozet göstergesi - ana sayfada küçük gösterim */
    private fun updateBadgeIndicator(view: View) {
        try {
            val badgeCount = BadgeManager.getUnlockedCount(requireContext())
            if (badgeCount > 0) {
                // Rozet göstergesi eklenebilir (isteğe bağlı)
                // Şimdilik sadece günlük progress kartında gösteriliyor
            }
        } catch (e: Exception) {
            // Hata durumunda sessizce devam et
        }
    }
    
    /** Günlük seri ve mikro hedef gösterimi - hafif, performans odaklı */
    private fun updateDailyProgress(view: View) {
        val cardDailyProgress = view.findViewById<MaterialCardView>(R.id.cardDailyProgress)
        val txtStreak = view.findViewById<TextView>(R.id.txtStreak)
        val txtDailyGoal = view.findViewById<TextView>(R.id.txtDailyGoal)
        
        try {
            val streakDays = ProgressManager.getStreakDays(requireContext())
            val (pages, minutes) = ProgressManager.getDailyProgress(requireContext())
            
            if (streakDays > 0 || pages > 0) {
                cardDailyProgress.visibility = View.VISIBLE
                
                if (streakDays > 0) {
                    txtStreak.text = "📿 $streakDays Günlük Okuma Serin"
                    if (streakDays >= 7) {
                        txtStreak.text = "📿 $streakDays Günlük Okuma Serin 🌿"
                    }
                } else {
                    txtStreak.text = "📿 Okuma Serine Başla"
                }
                
                if (pages > 0) {
                    txtDailyGoal.text = "Bugün: $pages sayfa"
                } else {
                    txtDailyGoal.text = "Bugün: 0 sayfa"
                }
            } else {
                // İlk gün veya henüz okuma yok - kartı gizle
                cardDailyProgress.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Hata durumunda kartı gizle - uygulama çökmesin
            cardDailyProgress.visibility = View.GONE
        }
    }

    private fun bindGonulFeatureGate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                try {
                    FeatureGate.refreshRemoteConfig()
                } catch (_: Throwable) {}

                gateReg?.remove()
                gateReg = FeatureGate.listenKillSwitch { state ->
                    gonulEnabled = state.enabled
                    gonulDisabledMsg = state.message

                    if (!isAdded) return@listenKillSwitch
                    if (::cardQuranGuide.isInitialized) {
                        cardQuranGuide.post {
                            if (!ENABLE_QURAN_GUIDE_CARD) {
                                cardQuranGuide.visibility = View.GONE
                            } else {
                                cardQuranGuide.visibility =
                                    if (state.enabled) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }
            }
        }
    }


    /** ANR fix: prefs + cache write + alarm schedule off main thread. */
    private fun scheduleFromSavedPrefsIfPossible(context: Context) {
        val appPrefs = context.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
        val pre = appPrefs.getBoolean("notif_prayer", true)
        val ker = appPrefs.getBoolean("notif_kerahat", false)
        if (!pre && !ker) return

        val loc = context.getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
        val fajr = loc.getString("saved_fajr", null)
        val sunrise = loc.getString("saved_sunrise", null)
        val dhuhr = loc.getString("saved_dhuhr", null)
        val asr = loc.getString("saved_asr", null)
        val maghrib = loc.getString("saved_maghrib", null)
        val isha = loc.getString("saved_isha", null)

        if (fajr.isNullOrBlank() || sunrise.isNullOrBlank() || dhuhr.isNullOrBlank()
            || asr.isNullOrBlank() || maghrib.isNullOrBlank() || isha.isNullOrBlank()
        ) return

        val lite = PrayerTimesLite(
            fajr = fajr!!,
            sunrise = sunrise!!,
            dhuhr = dhuhr!!,
            asr = asr!!,
            maghrib = maghrib!!,
            isha = isha!!
        )
        PrayerTimesCache.write(context, lite)
        PrayerReminderScheduler(context).rescheduleAll(lite)
    }

    private fun startCountdown(targetTimeStr: String, desc: String) {
        countDownTimer?.cancel()
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        val parts = targetTimeStr.split(":")
        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        calendar.set(Calendar.MINUTE, parts[1].toInt())
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis < now) calendar.add(Calendar.DAY_OF_MONTH, 1)

        val targetMillis = calendar.timeInMillis
        val diff = targetMillis - now

        countDownTimer = object : CountDownTimer(diff, 60000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                txtCountdown.text = if (hours > 0) "$hours sa $minutes dk" else "$minutes dakika"
                txtPrayerDesc.text = "$desc'ne kalan süre"
            }

            override fun onFinish() {
                if (!isAdded) return
                txtCountdown.text = "Vakit Girdi"
                prayerViewModel.loadFromPrefs()
            }
        }.start()
    }

    private fun loadUserInfo() {
        val txtName = view?.findViewById<TextView>(R.id.txtDashboardName) ?: return
        val user = auth.currentUser
        if (user != null) {
            if (user.isAnonymous) txtName.text = "Misafir"
            else {
                txtName.text = user.displayName ?: "Kardeşim"
                db.collection("users").document(user.uid).get().addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    val name = it.getString("name")
                    if (name != null) txtName.text = name
                }
            }
        }
    }

    private fun updateContinueCardText(view: View) {
        try {
            val txtCardTitle = view.findViewById<TextView>(R.id.txtCardTitle)
            val txtCardSub = view.findViewById<TextView>(R.id.txtCardSub)
            val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
            val lastReadName = prefs.getString("lastReadName", null)
            if (lastReadName != null) {
                txtCardTitle.text = "Okumaya Devam Et"
                txtCardSub.text = lastReadName
            }
        } catch (_: Exception) { }
    }

    /** GPS kapalı olsa da dene (FusedLocation ağ konumu kullanabilir). */
    private fun checkGpsAndGetLocation() {
        if (!isAdded) return
        getLocation()
    }

    private fun getLocation() {
        if (!isAdded || context == null) return
        txtLocationName.text = "Konum alınıyor..."
        imgLocationStatus.setImageResource(android.R.drawable.ic_menu_mylocation)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) processLocation(location)
                else requestNewLocationData()
            }.addOnFailureListener { requestNewLocationData() }
        } catch (_: SecurityException) { }
    }

    /** İzin verildikten sonra otomatik konum al: yüksek doğruluk, 25 sn zaman aşımı. */
    private var locationTimeoutHandler: Handler? = null
    private var locationTimeoutRunnable: Runnable? = null

    private fun requestNewLocationData() {
        if (!isAdded) return
        try {
            val cancellationTokenSource = CancellationTokenSource()
            locationTimeoutRunnable?.let { locationTimeoutHandler?.removeCallbacks(it) }
            locationTimeoutRunnable = Runnable {
                if (!isAdded) return@Runnable
                cancellationTokenSource.cancel()
            }
            locationTimeoutHandler = Handler(Looper.getMainLooper())
            locationTimeoutHandler?.postDelayed(locationTimeoutRunnable!!, 25_000)

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location: Location? ->
                    locationTimeoutRunnable?.let { locationTimeoutHandler?.removeCallbacks(it) }
                    if (!isAdded) return@addOnSuccessListener
                    if (location != null) processLocation(location)
                    else {
                        Toast.makeText(requireContext(), "Konum alınamadı. GPS'i açın veya şehir seçin.", Toast.LENGTH_LONG).show()
                        loadSavedLocationFromPrefs()
                    }
                }
                .addOnFailureListener {
                    locationTimeoutRunnable?.let { locationTimeoutHandler?.removeCallbacks(it) }
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Konum alınamadı. Şehir seçebilirsiniz.", Toast.LENGTH_LONG).show()
                        loadSavedLocationFromPrefs()
                    }
                }
        } catch (_: SecurityException) { }
    }

    /**
     * Önce vakitleri hemen çek (kullanıcı hızlı görsün), şehir adını arka planda al (düşük cihazı kasmasın).
     */
    private fun processLocation(location: Location) {
        if (!isAdded) return
        saveLocationMode("GPS")
        saveLocationPreference("Konumum", location.latitude, location.longitude)
        prayerViewModel.fetchPrayerTimes(location.latitude, location.longitude)

        viewLifecycleOwner.lifecycleScope.launch {
            val addressText = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val district = addresses[0].subAdminArea ?: ""
                        val city = addresses[0].adminArea ?: ""
                        if (district.isNotEmpty()) "$district, $city" else city
                    } else "Konumum"
                } catch (_: Exception) {
                    "Konumum"
                }
            }
            if (!isAdded) return@launch
            saveLocationPreference(addressText, location.latitude, location.longitude)
            prayerViewModel.loadFromPrefs()
        }
    }

    private fun saveLocationMode(mode: String) {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
        prefs.edit().putString("locationMode", mode).apply()
    }

    private fun loadSavedLocationFromPrefs() {
        val prefs = requireContext().getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
        val savedCity = prefs.getString("savedCity", null)
        val mode = prefs.getString("locationMode", "UNKNOWN")
        if (savedCity != null) updateLocationUI(mode == "GPS", savedCity)
        else txtLocationName.text = "Konum Seçiniz"
    }

    private fun hasSavedLocation(): Boolean {
        val prefs = requireContext().getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
        return prefs.contains("savedCity")
    }

    private fun saveLocationPreference(city: String, lat: Double, long: Double) {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences(PREFS_LOC, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("savedCity", city)
            .putFloat("lat", lat.toFloat())
            .putFloat("long", long.toFloat())
            .apply()
    }

    private fun updateLocationUI(isGps: Boolean, text: String) {
        if (!isAdded) return
        txtLocationName.text = text
        imgLocationStatus.setImageResource(
            if (isGps) android.R.drawable.ic_menu_mylocation else android.R.drawable.ic_menu_mapmode
        )
    }

    private fun showLocationSelectionDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_location_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnGPS = dialog.findViewById<View>(R.id.btnSelectGPS)
        val btnManual = dialog.findViewById<View>(R.id.btnSelectManual)
        val btnCancel = dialog.findViewById<View>(R.id.btnCancelLocation)

        btnGPS.setOnClickListener {
            dialog.dismiss()
            saveLocationMode("GPS")
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                checkGpsAndGetLocation()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        btnManual.setOnClickListener {
            dialog.dismiss()
            showCityListDialog()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCityListDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Şehir Seçiniz")
            .setItems(turkeyCities) { _, which ->
                saveLocationMode("MANUAL")
                findCoordinatesForCity(turkeyCities[which])
            }.show()
    }

    private fun findCoordinatesForCity(cityName: String) {
        txtLocationName.text = "Ayarlanıyor..."
        Thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocationName("$cityName, Turkey", 1)

                requireActivity().runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val location = addresses[0]
                        saveLocationPreference(cityName, location.latitude, location.longitude)
                        prayerViewModel.fetchPrayerTimes(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(context, "Bulunamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun setDailyVerse() {
        if (dailyVerses.isEmpty()) return
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val index = dayOfYear % dailyVerses.size
        val verse = dailyVerses[index]
        txtDailyVerse.text = "﴾ ${verse.first} ﴿"
        txtDailySource.text = verse.second
    }

    override fun onResume() {
        super.onResume()
        // İzin uygulama açılışında (HomeActivity) verildi; diyalog kapanınca burada tetiklenir.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !hasSavedLocation() && txtLocationName.text != "Konum alınıyor...") {
            txtLocationName.text = "Konum alınıyor..."
            imgLocationStatus.setImageResource(android.R.drawable.ic_menu_mylocation)
            checkGpsAndGetLocation()
        }
    }

    override fun onDestroyView() {
        gateReg?.remove()
        gateReg = null
        countDownTimer?.cancel()
        locationTimeoutRunnable?.let { locationTimeoutHandler?.removeCallbacks(it) }
        locationTimeoutHandler = null
        locationTimeoutRunnable = null
        super.onDestroyView()
    }

    private fun updateAllWidgets() {
        try {
            val context = requireContext()
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, HatimWidget::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allWidgetIds) {
                HatimWidget.updateAppWidget(context, appWidgetManager, id)
            }
        } catch (_: Exception) { }
    }
}
