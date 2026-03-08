# Hicri Takvim – Test ve Doğrulama Notları

## 1. Yıl değişiminde otomatik hesaplama
- `HicriTakvimRepository.getAllSpecialDaysForYear(year)` her çağrıda o yıl için hesaplama yapar; sabit tarih yok.
- Başlık: `HicriTakvimActivity` toolbar’da `Calendar.getInstance().year` kullanır, her yıl güncel kalır.

## 2. 29 / 30 Ramazan
- `IslamicCalendarHelper.getLengthOfHijriMonth(hy, 9)` Umm al-Qura’ya göre ay uzunluğunu döner (29 veya 30).
- Ramazan aralığı "1 - 29 Ramazan" veya "1 - 30 Ramazan" olarak doğru gösterilir.

## 3. Artık yıl
- Hesaplar `java.time.LocalDate` ve `HijrahDate` ile yapılır; miladi artık yıl otomatik işlenir.

## 4. Gece modu (dark theme)
- Kart: `primary_green`, `accent_gold`, `white` — `values-night/colors.xml` ile uyumlu.
- Liste: `white_card`, `text_dark`, `text_grey`, `primary_green` tema renklerine bağlı.

## 5. Cold start
- `HicriTakvimViewModel.init` içinde `loadCardData()` çağrılır; hesaplama `Dispatchers.Default` ile yapılır, ANR riski azaltılır.
- `loadYear(year)` Activity açıldığında çağrılır; ağır iş yine arka planda.

## 6. Configuration change / orientation
- ViewModel kullanıldığı için ekran dönüşünde state korunur.
- Liste `ListAdapter` + `DiffUtil` ile güncellenir.

## 7. Özel günler (Diyanet uyumlu)
- Mevlid, Regaib, Mirac, Berat, Ramazan başlangıcı, Ramazan Bayramı, Kurban Arefesi, Kurban Bayramı.
- Tarihler Umm al-Qura (Java `HijrahDate`) ile hesaplanır; Diyanet ile ±1 gün fark olabilir (ruyet farkı).
