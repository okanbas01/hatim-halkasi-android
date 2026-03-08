package com.example.sharedkhatm

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.PlaybackException
interface QuranApiService {
    @GET("v1/juz/{juzNumber}/{edition}")
    fun getJuz(
        @Path("juzNumber") juzNumber: Int,
        @Path("edition") edition: String
    ): Call<QuranApiResponse>

    @GET("v1/surah/{surahNumber}/{edition}")
    fun getSurah(
        @Path("surahNumber") surahNumber: Int,
        @Path("edition") edition: String
    ): Call<QuranApiResponse>
}

class ReadJuzActivity : AppCompatActivity() {

    private lateinit var ayahAdapter: AyahAdapter
    private lateinit var ayahList: ArrayList<Ayah>
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerAyahs: RecyclerView

    private lateinit var mainLayout: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var txtTitle: TextView
    private lateinit var txtLangLabel: TextView

    private lateinit var cardPlayer: CardView
    private lateinit var btnPlayPause: ImageView
    private lateinit var txtPlayerStatus: TextView
    private lateinit var btnClosePlayer: ImageView
    private lateinit var btnOpenPlayer: ImageView
    private lateinit var txtSpeed: TextView

    private var textSize: Float = 26f
    private var isNightMode: Boolean = false
    private var currentMode: Int = 0

    private var player: ExoPlayer? = null
    private var isAudioPlaying: Boolean = false
    private var currentAudioIndex: Int = 0
    private var audioUrls = ArrayList<String?>()
    private var bufferingTimeoutRunnable: Runnable? = null
    private var isBufferingTimeoutActive: Boolean = false

    // 🔥 HIZ
    private var currentPlaybackSpeed: Float = 1.0f
    private val AUDIO_PREFS = "AudioSettings"

    private var currentJuzNumber: Int = 0
    private var currentSurahNumber: Int = 0
    private var currentHatimId: String? = null
    private val PREFS_NAME = "ReadingSettings"
    private var isFromYasinTab: Boolean = false
    private var completedReadingThisSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_juz)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })

        val btnPrev = findViewById<ImageView>(R.id.btnPrev)
        val btnNext = findViewById<ImageView>(R.id.btnNext)

        btnPrev.setOnClickListener {
            if (currentAudioIndex > 0) {
                playAudio(currentAudioIndex - 1)
            }
        }

        btnNext.setOnClickListener {
            if (currentAudioIndex < audioUrls.size - 1) {
                playAudio(currentAudioIndex + 1)
            }
        }

        // Intent verileri
        currentJuzNumber = intent.getIntExtra("juzNumber", 0)
        currentSurahNumber = intent.getIntExtra("surahNumber", 0)
        currentHatimId = intent.getStringExtra("hatimId")
        isFromYasinTab = intent.getBooleanExtra("fromYasinTab", false)
        val restoreAyahIndex = intent.getIntExtra("restoreAyahIndex", -1)

        // View binding
        mainLayout = findViewById(R.id.mainLayoutRead)
        topBar = findViewById(R.id.topBarRead)
        txtTitle = findViewById(R.id.txtReadTitle)
        txtLangLabel = findViewById(R.id.txtLangLabel)
        progressBar = findViewById(R.id.progressBarRead)
        recyclerAyahs = findViewById(R.id.recyclerAyahs)

        val btnBackRead = findViewById<ImageView>(R.id.btnBackRead)
        val btnZoomIn = findViewById<ImageView>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<ImageView>(R.id.btnZoomOut)
        val btnToggleTheme = findViewById<ImageView>(R.id.btnToggleTheme)
        val btnToggleLanguage = findViewById<LinearLayout>(R.id.btnToggleLanguage)

        btnOpenPlayer = findViewById(R.id.btnOpenPlayer)
        cardPlayer = findViewById(R.id.cardPlayer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        txtPlayerStatus = findViewById(R.id.txtPlayerStatus)
        btnClosePlayer = findViewById(R.id.btnClosePlayer)
        txtSpeed = findViewById(R.id.txtSpeed)

        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)

        // Başlık
        if (currentJuzNumber > 0)
            txtTitle.text = "$currentJuzNumber. Cüz"
        else if (currentSurahNumber > 0)
            txtTitle.text = SurahNames.getName(currentSurahNumber)

        // Recycler – stable ID + payload ile highlight kayması önlenir
        recyclerAyahs.layoutManager = LinearLayoutManager(this)
        recyclerAyahs.setHasFixedSize(true)
        (recyclerAyahs.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        ayahList = ArrayList()
        ayahAdapter = AyahAdapter(ayahList)
        ayahAdapter.setHasStableIds(true)
        ayahAdapter.loadFont(this)
        recyclerAyahs.adapter = ayahAdapter

        loadSettings()
        fetchCombinedData()
        loadPlaybackSpeed()
        updateSpeedUI()

        // -----------------------------
        // 🔥 EXOPLAYER INIT
        // -----------------------------
        player = ExoPlayer.Builder(this).build()

        player?.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {

                when (state) {

                    Player.STATE_BUFFERING -> {
                        txtPlayerStatus.text = "Yükleniyor..."
                        // Buffering timeout başlat (8 saniye)
                        startBufferingTimeout()
                    }

                    Player.STATE_READY -> {
                        // Buffering timeout'u iptal et
                        cancelBufferingTimeout()

                        // Playback'i kesin başlat
                        player?.let { exoPlayer ->
                            if (!exoPlayer.isPlaying && isAudioPlaying) {
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        }

                        txtPlayerStatus.text = "Okunuyor"

                        val duration = player?.duration ?: 0
                        if (duration > 0) {
                            seekBar.max = duration.toInt()
                            txtTotalTime.text = formatTime(duration.toInt())
                        }

                        startSeekbarUpdate()
                    }

                    Player.STATE_ENDED -> {
                        cancelBufferingTimeout()
                        playAudio(currentAudioIndex + 1)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelBufferingTimeout()
                txtPlayerStatus.text = "Bağlantı hatası"
            }
        })

        // -----------------------------
        // 🔙 Geri
        // -----------------------------
        btnBackRead.setOnClickListener {
            navigateBack()
        }

        // -----------------------------
        // 🔍 Zoom
        // -----------------------------
        btnZoomIn.setOnClickListener {
            if (textSize < 60f) {
                textSize += 2f
                applySettings()
            }
        }

        btnZoomOut.setOnClickListener {
            if (textSize > 16f) {
                textSize -= 2f
                applySettings()
            }
        }

        // -----------------------------
        // 🌙 Tema
        // -----------------------------
        btnToggleTheme.setOnClickListener {
            isNightMode = !isNightMode
            applySettings()
        }

        // -----------------------------
        // 🌐 Dil
        // -----------------------------
        btnToggleLanguage.setOnClickListener {
            val popup = android.widget.PopupMenu(this, it)

            popup.menu.add(0, 0, 0, "📖  Arapça")
            popup.menu.add(0, 1, 1, "🇹🇷  Türkçe Meal")
            popup.menu.add(0, 2, 2, "🔤  Türkçe Okunuş")

            popup.setOnMenuItemClickListener { item ->
                currentMode = item.itemId
                updateLanguageUI()
                applySettings()
                true
            }

            popup.show()
        }

        // -----------------------------
        // 🔊 Player Aç
        // -----------------------------
        btnOpenPlayer.setOnClickListener {
            cardPlayer.visibility = View.VISIBLE
            if (audioUrls.isEmpty())
                fetchAudioData()
            else if (!isAudioPlaying)
                playAudio(currentAudioIndex)
        }

        // ▶ / ⏸
        btnPlayPause.setOnClickListener {
            if (isAudioPlaying) pauseAudio()
            else resumeAudio()
        }

        // ❌ Player Kapat
        btnClosePlayer.setOnClickListener {
            stopAudio()
            cardPlayer.visibility = View.GONE
        }

        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Hız
        txtSpeed.setOnClickListener {
            showSpeedDialog()
        }
    }
    private fun navigateBack() {
        if (completedReadingThisSession) {
            val screenContext = if (isFromYasinTab) com.example.sharedkhatm.ads.AdScreenContext.SENSITIVE else com.example.sharedkhatm.ads.AdScreenContext.NORMAL
            (application as? com.example.sharedkhatm.MyApplication)?.interstitialManager?.maybeShowInterstitial(this, screenContext) { finish() }
        } else {
            finish()
        }
    }

    private fun getApiCall(service: QuranApiService, edition: String): Call<QuranApiResponse> {
        return if (currentJuzNumber > 0) service.getJuz(currentJuzNumber, edition)
        else service.getSurah(currentSurahNumber, edition)
    }

    private fun buildQuranRetrofit(): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.alquran.cloud/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun fetchAudioData() {
        txtPlayerStatus.text = "Ses dosyaları hazırlanıyor..."
        btnPlayPause.isEnabled = false
        val retrofit = buildQuranRetrofit()
        val service = retrofit.create(QuranApiService::class.java)

        getApiCall(service, "ar.alafasy").enqueue(object : Callback<QuranApiResponse> {
            override fun onResponse(call: Call<QuranApiResponse>, response: Response<QuranApiResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val audioData = response.body()!!.data.ayahs
                    lifecycleScope.launch {
                        val urls = withContext(Dispatchers.Default) {
                            audioData.map { it.audio }
                        }
                        audioUrls.clear()
                        audioUrls.addAll(urls)
                        Log.d("AUDIO_DEBUG", "Audio count: ${audioUrls.size}")
                        btnPlayPause.isEnabled = true
                        txtPlayerStatus.text = "Çalmaya Hazır"
                        playAudio(0)
                    }
                } else txtPlayerStatus.text = "Hata!"
            }

            override fun onFailure(call: Call<QuranApiResponse>, t: Throwable) {
                txtPlayerStatus.text = "Hata!"
            }
        })
    }

    /** ANR önleme: Ağır işi bir sonraki mesaj döngüsüne erteleyerek ana iş parçacığının hemen dönmesini sağlar. */
    private fun playAudio(index: Int) {
        if (audioUrls.isEmpty()) {
            txtPlayerStatus.text = "Ses bulunamadı"
            return
        }
        if (index < 0 || index >= audioUrls.size) {
            stopAudio()
            return
        }
        updateRunnable?.let { handler.removeCallbacks(it) }
        cancelBufferingTimeout()

        val url = audioUrls[index] ?: return
        currentAudioIndex = index
        txtPlayerStatus.text = "Yükleniyor..."

        handler.post {
            if (!isFinishing && !isDestroyed) {
                ayahAdapter.updatePlayingIndex(index)
                scrollToPlayingAyah(index)
                player?.let { exoPlayer ->
                    if (exoPlayer.playbackState == Player.STATE_BUFFERING && isBufferingTimeoutActive) {
                        resetPlayer()
                    }
                }
                player?.apply {
                    stop()
                    clearMediaItems()
                    setMediaItem(MediaItem.fromUri(url))
                    playbackParameters = PlaybackParameters(currentPlaybackSpeed)
                    prepare()
                    playWhenReady = false
                }
                isAudioPlaying = true
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    /** Okunan ayeti görünür yapar; yumuşak scroll, üstte boşluk bırakır (kayma hissini azaltır) */
    private fun scrollToPlayingAyah(index: Int) {
        if (index < 0) return
        val lm = recyclerAyahs.layoutManager as? LinearLayoutManager ?: return
        val offsetPx = (48 * resources.displayMetrics.density).toInt()
        recyclerAyahs.post {
            lm.scrollToPositionWithOffset(index, offsetPx)
        }
    }

    private fun startSeekbarUpdate() {

        updateRunnable = object : Runnable {
            override fun run() {

                player?.let { exoPlayer ->

                    val position = exoPlayer.currentPosition
                    val duration = exoPlayer.duration

                    if (duration > 0) {
                        seekBar.max = duration.toInt()
                        txtTotalTime.text = formatTime(duration.toInt())
                    }

                    seekBar.progress = position.toInt()
                    txtCurrentTime.text = formatTime(position.toInt())

                    handler.postDelayed(this, 500)
                }
            }
        }

        handler.post(updateRunnable!!)
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun pauseAudio() {
        player?.playWhenReady = false
        isAudioPlaying = false
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    private fun resumeAudio() {
        player?.playWhenReady = true
        isAudioPlaying = true
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun stopAudio() {

        updateRunnable?.let { handler.removeCallbacks(it) }
        cancelBufferingTimeout()

        player?.stop()

        isAudioPlaying = false

        seekBar.progress = 0
        txtCurrentTime.text = "00:00"
        txtTotalTime.text = "00:00"

        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)

        // 🔥 highlight temizle
        ayahAdapter.updatePlayingIndex(-1)
    }

    private fun showSpeedDialog() {

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_speed, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.containerSpeeds)

        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        speeds.forEach { speed ->

            val item = TextView(this)
            item.text = "${speed}x"
            item.textSize = 16f
            item.setPadding(0, 24, 0, 24)

            if (speed == currentPlaybackSpeed) {
                item.setTextColor(Color.parseColor("#1B5E20"))
                item.setTypeface(null, Typeface.BOLD)
            } else {
                item.setTextColor(Color.DKGRAY)
            }

            item.setOnClickListener {

                currentPlaybackSpeed = speed
                savePlaybackSpeed()
                updateSpeedUI()

                player?.playbackParameters =
                    PlaybackParameters(currentPlaybackSpeed)

                dialog.dismiss()
            }

            container.addView(item)
        }

        dialog.show()
    }

    private fun updateSpeedUI() {
        txtSpeed.text = "${currentPlaybackSpeed}x"
    }

    private fun savePlaybackSpeed() {
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE)
            .edit()
            .putFloat("speed", currentPlaybackSpeed)
            .apply()
    }

    private fun loadPlaybackSpeed() {
        currentPlaybackSpeed =
            getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE)
                .getFloat("speed", 1.0f)
    }

    // ================= OKUMA AYARLARI =================

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        textSize = prefs.getFloat("textSize", 26f)
        // Koyu temadayken açıldığında varsayılan koyu; kullanıcı isterse açık temaya alabilir
        val defaultNight = ThemeManager.isDarkMode(this)
        isNightMode = prefs.getBoolean("nightMode", defaultNight)
        currentMode = prefs.getInt("currentMode", 0)
        updateLanguageUI()
        applySettings()
    }

    private fun updateLanguageUI() {
        when (currentMode) {
            0 -> txtLangLabel.text = "ARAPÇA"
            1 -> txtLangLabel.text = "MEAL"
            2 -> txtLangLabel.text = "OKUNUŞ"
        }
    }

    private fun applySettings() {
        val oldTextSize = ayahAdapter.currentTextSize
        val oldNightMode = ayahAdapter.isNightMode
        val oldMode = ayahAdapter.currentMode
        
        ayahAdapter.currentTextSize = textSize
        ayahAdapter.isNightMode = isNightMode
        ayahAdapter.currentMode = currentMode
        
        // Sadece değişen öğeleri güncelle - performans için kritik
        if (oldTextSize != textSize || oldMode != currentMode) {
            // Text size veya mode değiştiyse tüm öğeleri güncelle (gerekli)
            ayahAdapter.notifyItemRangeChanged(0, ayahAdapter.itemCount, "SETTINGS_CHANGE")
        } else if (oldNightMode != isNightMode) {
            // Tema değişince tüm ayetlerin metin rengi güncellenmeli (açık/koyu okunabilir)
            ayahAdapter.notifyItemRangeChanged(0, ayahAdapter.itemCount)
        }

        if (isNightMode) {
            mainLayout.setBackgroundColor(Color.parseColor("#121212"))
            topBar.setBackgroundColor(Color.parseColor("#1F1F1F"))
            txtTitle.setTextColor(Color.LTGRAY)
            recyclerAyahs.setBackgroundColor(Color.parseColor("#121212"))
            findViewById<ImageView>(R.id.btnToggleTheme)
                .setImageResource(R.drawable.ic_read_sun)
        } else {
            mainLayout.setBackgroundResource(R.color.secondary_cream)
            // Diğer sayfalardaki gibi orijinal koyu yeşil (header_green)
            topBar.setBackgroundResource(R.color.header_green)
            txtTitle.setTextColor(Color.WHITE)
            recyclerAyahs.setBackgroundResource(R.drawable.bg_quran_page)
            findViewById<ImageView>(R.id.btnToggleTheme)
                .setImageResource(R.drawable.ic_read_moon)
        }

        saveSettings()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putFloat("textSize", textSize)
            .putBoolean("nightMode", isNightMode)
            .putInt("currentMode", currentMode)
            .apply()
    }

    private fun fetchCombinedData() {
        val retrofit = buildQuranRetrofit()
        val service = retrofit.create(QuranApiService::class.java)

        getApiCall(service, "quran-uthmani")
            .enqueue(object : Callback<QuranApiResponse> {

                override fun onResponse(
                    call: Call<QuranApiResponse>,
                    response: Response<QuranApiResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {

                        val arabicData = response.body()!!.data.ayahs

                        getApiCall(service, "tr.diyanet")
                            .enqueue(object : Callback<QuranApiResponse> {

                                override fun onResponse(
                                    call2: Call<QuranApiResponse>,
                                    response2: Response<QuranApiResponse>
                                ) {
                                    if (response2.isSuccessful && response2.body() != null) {

                                        val turkishData =
                                            response2.body()!!.data.ayahs

                                        getApiCall(service, "tr.transliteration")
                                            .enqueue(object :
                                                Callback<QuranApiResponse> {

                                                override fun onResponse(
                                                    call3: Call<QuranApiResponse>,
                                                    response3: Response<QuranApiResponse>
                                                ) {
                                                    if (response3.isSuccessful &&
                                                        response3.body() != null
                                                    ) {
                                                        val transData =
                                                            response3.body()!!.data.ayahs
                                                        lifecycleScope.launch {
                                                            val merged = withContext(Dispatchers.Default) {
                                                                val list = ArrayList<Ayah>()
                                                                for (i in arabicData.indices) {
                                                                    val ayah = arabicData[i]
                                                                    ayah.textTurkish = turkishData.getOrNull(i)?.text
                                                                    ayah.textTransliteration = transData.getOrNull(i)?.text
                                                                    list.add(ayah)
                                                                }
                                                                list
                                                            }
                                                            ayahList.clear()
                                                            ayahList.addAll(merged)
                                                            ayahAdapter.notifyItemRangeInserted(0, ayahList.size)
                                                            restorePosition()
                                                        }
                                                    }
                                                }

                                                override fun onFailure(
                                                    call3: Call<QuranApiResponse>,
                                                    t: Throwable
                                                ) { }
                                            })
                                    }
                                }

                                override fun onFailure(
                                    call2: Call<QuranApiResponse>,
                                    t: Throwable
                                ) { }
                            })
                    }
                }

                override fun onFailure(
                    call: Call<QuranApiResponse>,
                    t: Throwable
                ) { }
            })
    }

    private fun restorePosition() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val globalPrefs = getSharedPreferences("AppGlobalPrefs", MODE_PRIVATE)

        val key = if (currentJuzNumber > 0)
            "pos_juz_$currentJuzNumber"
        else
            "pos_surah_$currentSurahNumber"

        // Intent'ten gelen restoreAyahIndex öncelikli (Okumaya Devam Et butonu)
        val restoreAyahIndex = intent.getIntExtra("restoreAyahIndex", -1)
        val lastAyahIndex = if (restoreAyahIndex >= 0) {
            restoreAyahIndex
        } else {
            prefs.getInt("${key}_ayah", globalPrefs.getInt("lastReadAyahIndex", -1))
        }
        val lastPosition = prefs.getInt(key, 0)

        // Önce ayet pozisyonuna git, yoksa genel pozisyona git
        if (lastAyahIndex >= 0 && lastAyahIndex < ayahList.size) {
            recyclerAyahs.post {
                scrollToPlayingAyah(lastAyahIndex)
            }
        } else if (lastPosition > 0 && lastPosition < ayahList.size) {
            recyclerAyahs.post {
                val lm = recyclerAyahs.layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(lastPosition, (48 * resources.displayMetrics.density).toInt())
            }
        }
    }

    override fun onPause() {
        super.onPause()

        val layoutManager = recyclerAyahs.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()

        if (position != RecyclerView.NO_POSITION) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val globalPrefs = getSharedPreferences("AppGlobalPrefs", MODE_PRIVATE)

            val key = if (currentJuzNumber > 0)
                "pos_juz_$currentJuzNumber"
            else
                "pos_surah_$currentSurahNumber"

            // Lokal pozisyon kaydet
            prefs.edit().putInt(key, position).apply()
            
            // Global "Okumaya Devam Et" için son ayet indeksini kaydet
            val ayahIndex = if (ayahAdapter.currentPlayingIndex != -1) 
                ayahAdapter.currentPlayingIndex 
            else 
                position
            
            if (ayahIndex >= 0 && ayahIndex < ayahList.size) {
                prefs.edit().putInt("${key}_ayah", ayahIndex).apply()
                
                // Global prefs'e de kaydet (DashboardFragment için)
                globalPrefs.edit()
                    .putInt("lastReadAyahIndex", ayahIndex)
                    .apply()
                
                // Okuma kaydı - hafif, performans odaklı
                // Yaklaşık sayfa hesabı: her 20 ayet ~1 sayfa
                val pagesRead = (ayahIndex / 20) + 1
                ProgressManager.recordReading(this, pagesRead, 0)
                
                // Rozet kontrolü - hafif, arka planda
                BadgeManager.checkAndUnlockBadges(this)
                
                // Yeni rozet kazanıldı mı kontrol et ve göster
                checkNewBadges()
                if (ayahList.isNotEmpty() && ayahIndex >= ayahList.size - 1) {
                    completedReadingThisSession = true
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelBufferingTimeout()
        updateRunnable?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
    }

    /** Yeni kazanılan rozetleri kontrol et ve göster - performans odaklı */
    private fun checkNewBadges() {
        // Arka planda hafif kontrol - 300ms delay ile (performans için)
        recyclerAyahs.postDelayed({
            try {
                val unlockedBadges = BadgeManager.getUnlockedBadges(this)
                val allBadges = BadgeManager.allBadges
                
                // Yeni kazanılan rozetleri bul
                for (badge in allBadges) {
                    if (unlockedBadges.contains(badge.id) && 
                        !BadgeManager.isBadgeShown(this, badge.id)) {
                        // Yeni rozet kazanıldı - göster
                        BadgeUnlockDialog.showUnlockDialog(this, badge)
                        break // Sadece bir tane göster (kullanıcıyı rahatsız etme)
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }, 300) // 300ms delay - performans için
    }

    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // ================= EXOPLAYER BUFFERING TIMEOUT =================

    private fun startBufferingTimeout() {
        cancelBufferingTimeout()
        isBufferingTimeoutActive = true

        bufferingTimeoutRunnable = Runnable {
            // 8 saniye sonra hala buffering durumundaysa reset et
            player?.let { exoPlayer ->
                if (exoPlayer.playbackState == Player.STATE_BUFFERING) {
                    Log.w("ExoPlayer", "Buffering timeout - resetting player")
                    resetPlayer()
                    // Media'yı tekrar yükle
                    val url = audioUrls.getOrNull(currentAudioIndex)
                    if (url != null) {
                        playAudio(currentAudioIndex)
                    }
                }
            }
            isBufferingTimeoutActive = false
        }

        handler.postDelayed(bufferingTimeoutRunnable!!, 8000) // 8 saniye
    }

    private fun cancelBufferingTimeout() {
        bufferingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        bufferingTimeoutRunnable = null
        isBufferingTimeoutActive = false
    }

    private fun resetPlayer() {
        cancelBufferingTimeout()

        player?.let { exoPlayer ->
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.playWhenReady = false
            } catch (e: Exception) {
                Log.e("ExoPlayer", "Error resetting player", e)
            }
        }

        // Eğer player tamamen bozuksa release edip yeniden oluştur
        try {
            player?.release()
        } catch (e: Exception) {
            Log.e("ExoPlayer", "Error releasing player", e)
        }

        player = ExoPlayer.Builder(this).build()

        // Listener'ı tekrar ekle
        player?.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {

                when (state) {

                    Player.STATE_BUFFERING -> {
                        txtPlayerStatus.text = "Yükleniyor..."
                        // Buffering timeout başlat (8 saniye)
                        startBufferingTimeout()
                    }

                    Player.STATE_READY -> {
                        // Buffering timeout'u iptal et
                        cancelBufferingTimeout()

                        // Playback'i kesin başlat
                        player?.let { exoPlayer ->
                            if (!exoPlayer.isPlaying && isAudioPlaying) {
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        }

                        txtPlayerStatus.text = "Okunuyor"

                        val duration = player?.duration ?: 0
                        if (duration > 0) {
                            seekBar.max = duration.toInt()
                            txtTotalTime.text = formatTime(duration.toInt())
                        }

                        startSeekbarUpdate()
                    }

                    Player.STATE_ENDED -> {
                        cancelBufferingTimeout()
                        playAudio(currentAudioIndex + 1)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelBufferingTimeout()
                txtPlayerStatus.text = "Bağlantı hatası"
            }
        })
    }

}
