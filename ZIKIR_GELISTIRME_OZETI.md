# Gelişmiş Zikirmatik – Özet

## Eklenen / Değiştirilen Dosyalar

### Model / Veri
- **ZikirItem.kt** – Tek zikir kartı: `id`, `name`, `target`, `currentCount`, `order`. DiffUtil için `equals`/`hashCode` data class ile.
- **ZikirHistoryRecord.kt** – Geçmiş kaydı: `dateYmd`, `zikirId`, `zikirName`, `count`.
- **ZikirStorage.kt** – SharedPreferences ile liste + geçmiş; ilk açılışta default set / eski sayaç migration; geçmişe O(1) append.

### Layout
- **item_zikir_card.xml** – Kart: sürükle tutamacı, ad, mevcut/hedef, +/-, Sıfırla, Kaydet, menü.
- **item_zikir_history.xml** – Geçmiş satırı: tarih, zikir adı, tamamlanan sayı.
- **dialog_dhikr_edit.xml** – Yeni zikir / düzenle: başlık, zikir adı, hedef sayı, İptal/Kaydet.
- **fragment_dhikr.xml** – Header, “Zikirlerim | Geçmiş” sekmeleri, iki RecyclerView, FAB.

### Mantık / Adapter
- **ZikirAdapter.kt** – DiffUtil + payload (sadece sayı değişince `PAYLOAD_COUNT`); artır/azalt, sıfırla, kaydet, menü callback’leri; `notifyDataSetChanged` yok.
- **ZikirHistoryAdapter.kt** – Geçmiş listesi (küçük liste, basit adapter).
- **DhikrFragment.kt** – Liste yükleme, sekme geçişi, ItemTouchHelper ile sıralama, FAB ile yeni zikir, dialoglar (ekle/düzenle/sil/sıfırla), titreşim.

---

## Zikir Veri Yapısı

```kotlin
data class ZikirItem(
    val id: String,        // UUID veya "legacy" / "def_0" vb.
    var name: String,
    var target: Int,
    var currentCount: Int,
    var order: Int
)
```

- **SharedPreferences** anahtarı: `ZikirPrefs` / `zikir_list` (JSON array).
- Eski tek sayaç: `savedCount` varsa tek kart “Zikir” olarak en üstte, hedef 33; default 5 zikir altına eklenir.
- Yeni kullanıcı: sadece 5 default (Subhanallah 33, Elhamdülillah 33, Allahu Ekber 33, La ilahe illallah 100, Estağfirullah 100).

---

## Geçmiş Veri Yapısı

```kotlin
data class ZikirHistoryRecord(
    val dateYmd: String,   // yyyyMMdd
    val zikirId: String,
    val zikirName: String,
    val count: Int
)
```

- **SharedPreferences** anahtarı: `zikir_history` (JSON array).
- **Kaydet**e basılınca: mevcut sayı tek kayıt olarak eklenir (O(1) append), sonra sayaç sıfırlanır.
- En fazla 300 kayıt tutulur (cap).
- Gösterim: En yeniden eskiye (`reversed()`).

---

## Performans Neden Güvenli

1. **RecyclerView**  
   - Zikir listesi: `ZikirAdapter` + `DiffUtil.calculateDiff`; güncellemeler `dispatchUpdatesTo` ile `notifyItem*` / payload.  
   - `notifyDataSetChanged` kullanılmıyor.

2. **Payload**  
   - Sadece `currentCount` değişince `PAYLOAD_COUNT`; `onBindViewHolder(holder, position, payloads)` yalnızca sayı TextView’larını güncelliyor, layout hesabı yok.

3. **Stable ID**  
   - `getItemId(position) = items[position].id.hashCode().toLong()` ile sıralama/drag’da görsel kayma azalıyor.

4. **Veri**  
   - Tüm liste tek JSON; öğe sayısı onlarla sınırlı. Büyük in-memory liste yok.  
   - Geçmiş: append + cap; her “Kaydet”te tek kayıt ekleniyor, tüm geçmiş döngüyle yeniden hesaplanmıyor.

5. **Main thread**  
   - SharedPreferences `apply()` kullanılıyor (asenkron yazma). Okuma kısa; ağır iş yok.

6. **Geçmiş listesi**  
   - Küçük (≤300); sadece sekme açıldığında bir kez yükleniyor. Bu ekran için `notifyDataSetChanged` kabul edilebilir.

---

## Mevcut Kullanıcıların Verisini Koruma

- `ZikirPrefs` / `savedCount` varsa: bu değer “Zikir” adlı tek kartın `currentCount` değeri yapılır, en üstte (order 0) tutulur.  
- Default 5 zikir, sadece isim çakışması yoksa eklenir (`existingNames` kontrolü).  
- `zikr_initialized` ile default/migration yalnızca bir kez çalışır; sonra mevcut liste olduğu gibi kullanılır.

---

## Kısa UX Özeti

- Sade, huzurlu kartlar (yeşil vurgu, krem arka plan).  
- Zikirlerim / Geçmiş sekmeleri; FAB ile yeni zikir.  
- Kart menüsü: Düzenle (ad + hedef), Sil.  
- Sürükle-bırak ile sıra değişimi.  
- Sıfırla: sadece sayaç; geçmiş silinmez.  
- Kaydet: o anki sayı geçmişe eklenir, sayaç sıfırlanır.
