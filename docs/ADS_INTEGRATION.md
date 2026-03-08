# Reklam Entegrasyonu (Hatim Halkası)

## Kurallar (Uygulandı)

- **Reklam YOK:** Kur'an okuma, sure içeriği, Namaz Hocası adım, Kıble bul, Zikir aktif sayaç, Dua metni ekranları. Bu ekranlara AdMob preload bile yapılmıyor.
- **Banner:** Sadece Ana Sayfa (Hicri kartı altı), Maneviyat grid en altı, Hatimlerim listesi altı, Sure listesi en altı. Adaptive banner, bottom-anchored.
- **Rewarded:** Sadece (1) Uygulamayı Destekle – 24h reklamsız, (2) Bilgi Yarışması bitiminde ekstra soru paketi, (3) Cuma/Kandil kartında kilitli tema açma. Zorunlu değil, iptal edilebilir.

## Mimari

- **AdManager** (interface): `shouldShowAds`, `loadBanner`, `showRewarded`, `preloadRewarded`, `destroyBanner`. UI/ViewModel doğrudan AdMob kullanmıyor.
- **AdManagerImpl**: AdMob implementasyonu. Banner: adaptive boyut, lazy yükleme. Rewarded: lifecycle-safe, `FullScreenContentCallback` ile kapatma/ödül.
- **AdPreferences**: 24 saat reklamsız (`grantAdFree24h`), premium flag. `shouldShowAds()` = reklam gösterilebilir mi kontrolü.

## Performans

- **Application:** AdMob `MobileAds.initialize()` Splash’ta değil, `Handler(Looper.getMainLooper()).post { ... }` ile bir sonraki frame’de; cold start bloklanmıyor.
- **Banner:** Sadece izin verilen ekranlarda, `shouldShowAds()` true ise yüklenir; premium/24h reklamsız ise container `GONE`.
- **Rewarded:** Okuma/namaz/dua/zikir ekranlarında preload yapılmıyor. Preload ihtiyacı varsa sadece Destek/Quiz/Cuma gibi ekranlara girildiğinde yapılabilir (şu an lazy: gösterirken yükle).
- **Lifecycle:** Her banner ekranında `onDestroyView` içinde `destroyBanner(container)` çağrılıyor; leak riski azaltıldı.

## Dosyalar

| Dosya | Açıklama |
|-------|----------|
| `ads/AdManager.kt` | Interface |
| `ads/AdManagerImpl.kt` | AdMob implementasyonu |
| `ads/AdPreferences.kt` | 24h reklamsız + premium flag |
| `res/layout/ad_banner_container.xml` | Banner container (include) |
| `MyApplication.kt` | AdPreferences.init, MobileAds.initialize (post), adManager |
| `strings.xml` | `admob_banner_id`, `admob_rewarded_id` (test ID’ler; production’da değiştir) |
| `AndroidManifest.xml` | `com.google.android.gms.ads.APPLICATION_ID` (test; production’da kendi ID) |

## Production

1. **Manifest:** `APPLICATION_ID` değerini kendi AdMob uygulama ID’nizle değiştirin.
2. **strings.xml:** `admob_banner_id` ve `admob_rewarded_id` değerlerini kendi birim ID’lerinizle değiştirin.
3. Test birim ID’leri: [AdMob test ads](https://developers.google.com/admob/android/test-ads).

## LeakCanary / Bellek Sızıntısı Düzeltmeleri

- **AdManagerImpl.loadBanner:** `AdListener` içinde container artık **WeakReference** ile tutuluyor; böylece listener view hiyerarşisini (ScrollView, ConstraintLayout, includeAdBanner) strong referansla tutmuyor.
- **AdManagerImpl.destroyBanner:** Destroy öncesi `adView.adListener = null` yapılıyor; SDK’nın tuttuğu listener → container zinciri kırılıyor. Sonra `removeAllViews()` ve `adView.destroy()` çağrılıyor.
- **Fragment onDestroyView:** Banner container referansı `view?.findViewById` ile alınıp hemen `destroyBanner(adContainer)` ile temizleniyor; sıra `super.onDestroyView()` öncesi.

**Not:** OMID / `ContentObserver` / `internal.webview` kaynaklı sızıntılar AdMob SDK içinde; uygulama tarafında tamamen kesmek mümkün değil. Banner’ı doğru destroy etmek ve listener’ı null’lamak, AdView/FrameLayout/ScrollView sızıntılarını büyük ölçüde ortadan kaldırır.

## ANR / Risk Özeti

- Main thread: AdMob init ertelendi; banner load coroutine ile (Main dispatcher), ağır iş yok.
- Reklam SDK’sı Kur’an/dua/namaz/zikir ekranlarında hiç initialize/preload edilmiyor.
- Premium veya 24h reklamsız kullanıcıda banner hiç yüklenmez (erken `shouldShowAds()` kontrolü).
