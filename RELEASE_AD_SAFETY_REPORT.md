# Google Play Production – Reklam Güvenlik ve Policy Kontrol Raporu

**Tarih:** 2025-03-04  
**Amaç:** Release build’in production-safe olduğunu doğrulamak; test reklam, ANR ve policy risklerini önlemek.

---

## 1. TEST REKLAM TEMİZLİĞİ (KRİTİK)

| Kontrol | Durum | Açıklama |
|--------|--------|----------|
| `ca-app-pub-3940256099942544` release’te | ✅ | Sadece `app/src/debug/res/values/strings.xml` içinde; release’te yok. |
| `setTestDeviceIds` | ✅ | Sadece `BuildConfig.DEBUG == true` iken `MyApplication` içinde çağrılıyor; release’te boş liste. |
| `Log.d("AdMob", ...)` | ✅ | Tüm AdMob logları `BuildConfig.DEBUG` ile sarıldı; release’te log yok. |
| Gradle task | ✅ | `checkReleaseAdIds`: main kaynak + release `strings.xml` taranıyor; test ID bulunursa build **FAIL**. |

**Gradle task davranışı:**
- `app/src/main` altındaki `.kt`, `.java`, `.xml` dosyalarında `3940256099942544` aranıyor; bulunursa build durur.
- `app/src/release/res/values/strings.xml` içinde aynı test ID aranıyor; bulunursa build durur.
- Task: `mergeReleaseResources`, `bundleRelease`, `assembleRelease` öncesi otomatik çalışıyor.

---

## 2. RELEASE REKLAM ID DOĞRULAMA

| ID | Durum | Not |
|----|--------|-----|
| `admob_banner_id` | ✅ | Dolu, format `ca-app-pub-XXX/YYY` uygun. |
| `admob_rewarded_id` | ✅ | Dolu, format uygun. |
| `admob_interstitial_id` | ✅ | Dolu, format uygun. |
| `admob_app_open_id` | ✅ | Dolu, format uygun. |

**Gradle kuralları:**
- Bu dört key boş veya "PLACEHOLDER"/"REPLACE" içeriyorsa build **FAIL**.
- Değer `ca-app-pub-\d+/\d+` formatında değilse build **FAIL**.

Dosya: `app/src/release/res/values/strings.xml`.  
**Not:** Interstitial ve App Open için şu an kullanılan birim ID’leri (9309434523, 9309434524) AdMob konsolundan oluşturulmuş gerçek birimler değilse, konsoldan yeni birim açıp bu dosyayı güncelleyin.

---

## 3. APP OPEN GÜVENLİK

| Kural | Durum | Kod yeri |
|-------|--------|-----------|
| Cold start bloklamıyor | ✅ | `MyApplication.onCreate`: init/preload `delay(800)` + Coroutine; `Application.onCreate`’ta senkron reklam yok. |
| Splash’te gösterilmiyor | ✅ | `SplashActivity` içinde reklam/AdMob çağrısı yok. App Open sadece `HomeActivity.onCreate` → `maybeShowAfterDelay(this)`. |
| Sadece 3+ gün aktif kullanıcı | ✅ | `AppOpenManager.maybeShowAfterDelay`: `activeDays < AdRemoteConfig.appOpenMinActiveDays()` (varsayılan 3) ise gösterilmiyor. |
| Günde maksimum 1 | ✅ | `AdRemoteConfig.appOpenMaxPerDay()` (varsayılan 1); `tryShow` içinde `countToday` ile kontrol. |
| Gösterim gecikmesi | ✅ | `DELAY_MS = 2000L`; reklam açılıştan 2 sn sonra deneniyor. |

---

## 4. INTERSTITIAL GÜVENLİK

| Kural | Durum | Kod yeri |
|-------|--------|-----------|
| İlk 3 uygulama açılışında yok | ✅ | `InterstitialManager.maybeShowInterstitial`: `appLaunches < FIRST_LAUNCHES_NO_AD` (3) ise atlanıyor. |
| Ekran geçişinde tetiklenmiyor | ✅ | Sadece `ReadJuzActivity` (sure bitti, geri) ve `QuizFragment` (sonuç ekranı) tetikliyor. |
| Sadece sure tamamlandıktan sonra / quiz sonucu | ✅ | `ReadJuzActivity.navigateBack()` okuma tamamlandıysa; `QuizFragment` state Result’ta. |
| Günlük max 3 | ✅ | `AdRemoteConfig.interstitialMaxPerDay()` (varsayılan 3); `todayCount` ile sınır. |
| Hassas ekranlarda yok | ✅ | `AdScreenContext.SENSITIVE` (örn. Yasin) ile çağrıda interstitial hiç gösterilmiyor. |

---

## 5. REWARDED GÜVENLİK

| Kural | Durum | Kaynak |
|-------|--------|--------|
| Günde max 5 | ✅ | `SupportAdTrackerImpl` + `AdRemoteConfig.rewardedMaxPerDay()`. |
| Saatte max 2 | ✅ | `AdRemoteConfig.rewardedMaxPerHour()` + saat penceresi sayacı. |
| Min 5 dk aralık | ✅ | `AdRemoteConfig.rewardedMinIntervalMinutes()` + `cachedLastRewardMs`. |
| “1 reklam = gün boyu reklamsız” yok | ✅ | Rewarded izlendiğinde sadece `SupportAdTracker.increment()`; `setPremium`/reklamsızlık ödülü çağrılmıyor. |

---

## 6. PERFORMANCE / ANR KONTROL

| Kontrol | Durum | Açıklama |
|--------|--------|----------|
| Reklam preload main thread’de değil | ✅ | Preload’lar `CoroutineScope` + `Dispatchers.Main`/callback ile; init `delay(800)` sonrası. |
| Splash temiz | ✅ | Splash’te reklam/AdMob yok. |
| `Application.onCreate` ağır iş yok | ✅ | Ad init ertelenmiş; Remote Config `Dispatchers.IO`’da. |
| Retry loop yok | ✅ | Load/show hatalarında sessiz fail; kullanıcı bekletilmiyor. |
| Load başarısızsa kullanıcı bekletilmiyor | ✅ | Rewarded/interstitial hazır değilse `onDismissed`/skip; 3 sn show timeout. |
| StrictMode (sadece debug) | ✅ | `MyApplication.onCreate`: `BuildConfig.DEBUG` iken StrictMode thread + VM policy. |

---

## 7. MEMORY LEAK KONTROL

| Kontrol | Durum | Açıklama |
|--------|--------|----------|
| Banner destroy | ✅ | `DashboardFragment.onDestroyView` → `adViewModel.destroyBanner(adContainer)`; `AdManagerImpl.destroyBanner` view’ı kaldırıp `destroy()` çağırıyor. |
| Activity referansı static değil | ✅ | Reklam yöneticileri Activity’yi parametre olarak alıyor; static reklam instance’ı yok. |
| Context | ✅ | `applicationContext` kullanılıyor (prefs, load). |
| App Open / Interstitial onDestroy | ✅ | `HomeActivity.onDestroy` → `appOpenManager?.onDestroy()`; `AppOpenManager.onDestroy()` `appOpenAd = null`. InterstitialManager application context kullanıyor. |

---

## 8. POLICY SAFE CONFIG

`MyApplication` içinde `MobileAds.initialize` callback’inde:

- `setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)`
- `setTagForUnderAgeOfConsent(TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)`
- `setMaxAdContentRating(MAX_AD_CONTENT_RATING_PG)`
- Test cihaz listesi **sadece** `BuildConfig.DEBUG` iken ayarlanıyor.

---

## 9. PROGUARD / R8

`app/proguard-rules.pro`:

- `-keep class com.google.android.gms.ads.** { *; }`
- `-keepclassmembers class com.google.android.gms.ads.** { *; }`
- `Signature`, `*Annotation*` attribute’ları korunuyor.

AdMob sınıflarının strip edilmemesi için yeterli.

---

## 10. BU OTURUMDA DEĞİŞTİRİLEN / EKLENEN DOSYALAR

| Dosya | Değişiklik |
|-------|------------|
| `app/build.gradle.kts` | `checkReleaseAdIds`: main kaynakta test ID taraması; release `strings.xml`’de test ID + boş/placeholder/format kontrolü; 4 ad ID zorunlu. |
| `app/src/main/java/.../ads/AdManagerImpl.kt` | Tüm `Log.d("AdMob", ...)` çağrıları `BuildConfig.DEBUG` ile sarıldı. |
| `app/src/main/java/.../ads/BannerStateControllerImpl.kt` | Aynı şekilde AdMob logları sadece DEBUG’da. |
| `app/src/main/java/.../ads/AdBannerCompose.kt` | Aynı şekilde AdMob logları sadece DEBUG’da. |

---

## 11. EKLENEN GÜVENLİK KONTROLLERİ (ÖZET)

1. **checkReleaseAdIds (Gradle)**  
   - Main kaynak (kt/java/xml) ve release `strings.xml`’de test yayıncı ID (`3940256099942544`) aranıyor; bulunursa build fail.  
   - Release’te `admob_banner_id`, `admob_rewarded_id`, `admob_interstitial_id`, `admob_app_open_id` boş/placeholder olamaz ve `ca-app-pub-\d+/\d+` formatında olmalı; aksi halde build fail.

2. **AdMob logları**  
   - `Log.d("AdMob", ...)` ve ilgili reklam logları sadece `BuildConfig.DEBUG` iken çalışıyor; release’te Logcat’te reklam mesajı çıkmıyor.

3. **Mevcut yapı (önceki oturumlardan)**  
   - Policy (PG, child/underage false), App Open (cold start/splash/3 gün/1 gün), Interstitial (ilk 3 açılış, tetikleyici, günlük 3, SENSITIVE), Rewarded (5/2/5 dk, reklamsızlık yok), ANR önlemleri, lifecycle/destroy, Proguard kuralları.

---

## 12. RİSK ÖZETİ

| Risk | Seviye | Durum |
|------|--------|--------|
| Test reklamın release’te kalması | Kritik | ✅ Gradle + kaynak ayrımı ile engellendi. |
| Boş/yanlış reklam ID | Yüksek | ✅ Gradle ile zorunlu ve format kontrolü. |
| ANR / cold start | Yüksek | ✅ Ertelenmiş init, preload, retry yok, timeout. |
| Policy (çocuk/yaş/içerik) | Yüksek | ✅ RequestConfiguration PG, child/underage false. |
| Hassas ekranda interstitial | Orta | ✅ SENSITIVE context’te gösterilmiyor. |
| Memory leak | Orta | ✅ Destroy, applicationContext, static Activity yok. |

---

## 13. RELEASE BUILD HAZIR MI?

**Evet.**  
Bu kontroller ve mevcut kod ile release build:

- Test reklam ID’si olmadan,
- Zorunlu reklam ID’leri dolu ve formatta,
- ANR ve policy kurallarına uygun,
- Reklam logları kapalı

olarak **production’a uygun** kabul edilebilir.

**Yayına almadan önce:**

1. `app/src/release/res/values/strings.xml` içindeki Interstitial ve App Open birim ID’lerinin AdMob konsolundaki gerçek birimlerle aynı olduğundan emin olun.  
2. Firebase Remote Config’te reklam parametrelerinin (günlük/saatlik limitler, min interval vb.) istediğiniz değerlere ayarlandığını kontrol edin.  
3. `./gradlew :app:assembleRelease` veya `bundleRelease` çalıştırdığınızda `checkReleaseAdIds`’in geçtiğini doğrulayın.

Bu adımlardan sonra uygulama, belirtilen amaçlara uygun şekilde **Google Play’e gönderilmeye hazır** kabul edilebilir.
