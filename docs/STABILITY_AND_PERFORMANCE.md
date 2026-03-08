# Stabilite ve Performans – Root Cause & Düzeltmeler

## 1) Global root cause analizi

| Risk | Durum | Açıklama |
|------|--------|----------|
| Main thread blocking | Azaltıldı | Firebase callback'ler main'de ama kısa; ağır işler IO'da (FeatureGate, WorkManager post). |
| Firebase UI thread | Hayır | signIn, sendPasswordResetEmail callback'leri main'de ama sadece UI güncellemesi; ağ işi arka planda. |
| Application.onCreate ağır | Azaltıldı | WorkManager enqueue Handler.post ile ertelendi; StrictMode ve AuthStateListener sadece DEBUG. |
| Splash ANR riski | Azaltıldı | Handler callback'leri onDestroy'da temizleniyor; minDuration 1.2s; route() blocking yok. |
| WorkManager çakışma | Hayır | KEEP + UNIQUE work; periyodik ve one-time ayrı. |
| Dialog lifecycle leak | Giderildi | LoginActivity: AlertDialog + WeakReference; isSafeForUi() ile gösterim. |
| Handler memory leak | Giderildi | SplashActivity: pendingRunnables listesi, onDestroy'da removeCallbacks. |
| Bitmap / Lottie spike | Azaltıldı | Glide override, BitmapFactory options, Lottie HARDWARE. |
| RecyclerView adapter leak | Düşük | setHasFixedSize(true); ViewBinding null reset (fragment’lerde onDestroyView). |
| Coroutine cancellation | Mevcut | lifecycleScope kullanılan yerlerde activity destroy’da iptal. |

---

## 2) LoginActivity – BadTokenException

- **Yapılanlar:** `android.app.Dialog` kaldırıldı; `AlertDialog` + `WeakReference<LoginActivity>` kullanılıyor. `runOnUiThread` kaldırıldı (Firebase zaten main’de callback ediyor). Tüm dialog/Toast öncesi `isSafeForUi()` veya `activityRef.get()?.isSafeForUi()`. `addOnCompleteListener(this)` yerine parametresiz listener + WeakReference ile activity alınıyor.
- **Hedef:** BadTokenException’ın tekrar etmemesi.

---

## 3) Out of memory

- Glide: override(1024/400). BitmapFactory: inSampleSize, RGB_565. RecyclerView: setHasFixedSize(true). Lottie: setRenderMode(HARDWARE). ViewBinding: fragment’lerde onDestroyView’da null atanması önerilir. LeakCanary debug’da açık; HomeActivity onDestroy’da IMM/focus temizliği, SpiritualityFragment onDestroyView’da clearFocus.

---

## 4) SplashActivity

- Min süre 1.2s. Animasyon süreleri kısaltıldı (600/500ms). Handler postDelayed callback’leri `pendingRunnables` ile tutulup onDestroy’da `removeCallbacks` ile temizleniyor. route() sadece prefs + auth.currentUser (senkron, hafif); signInAnonymously async, callback’te isFinishing/isDestroyed kontrolü var.

---

## 5) MyApplication

- FirebaseApp + AppCheck init main’de (kısa). AuthStateListener sadece DEBUG. RemoteConfig preload zaten appScope.launch(Dispatchers.IO). WorkManager enqueue’leri Handler(Looper.getMainLooper()).post { } ile ertelendi.

---

## 6) ANR stratejisi

- Ağır iş: Dispatchers.Default/IO (GoalsFragment, ProfileFragment, HicriTakvimViewModel, FeatureGate). Firebase çağrıları senkron değil. StrictMode sadece DEBUG. WorkManager tekrarları UNIQUE ile.

---

## 7) Performans

- R8: release’te minifyEnabled + shrinkResources. ProGuard: Firebase, Glide, Lottie keep. LeakCanary debugImplementation. WebP/baseline profile: ihtiyaca göre eklenebilir.

---

## 8) Test planı

- 1GB RAM emülatör: düşük bellek senaryosu.
- Network: throttling (slow 3G) ile login/splash/RemoteConfig.
- Orientation: Login, Splash, ana ekranlarda döndürme; crash/ANR yok.
- Background → foreground: Splash veya Login açıkken home’a alıp geri dön; dialog/klavye crash yok.
- 50 cold start: uygulamayı sonlandırıp 50 kez aç; ANR/crash sayısı 0 hedeflenir.
- 30 dk açık: ana akışlarda gezinip LeakCanary ile leak kontrolü.

---

## Özet

- Login: Lifecycle-safe dialog + WeakReference, runOnUiThread kaldırıldı.
- Splash: 1.2s min, Handler leak önlendi, route() non-blocking.
- Application: WorkManager post ile ertelendi, AuthStateListener sadece DEBUG.
- OOM: Glide/Bitmap/Lottie/RecyclerView ve leak düzeltmeleri uygulandı.
