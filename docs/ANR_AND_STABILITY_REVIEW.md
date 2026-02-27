# ANR ve Kilitlenme Oranı — Geliştirme Özeti

Bu belge, yapılan geliştirmelerin ANR (Application Not Responding) ve kilitlenme oranını **artırmadığını** ve gereken yerlerde **azalttığını** özetler.

---

## 1. Splash ekranı (SplashActivity)

| Konu | Durum |
|------|--------|
| Ana thread | Sadece setContentView, findViewById, alpha/translationY ataması, post, OnPreDrawListener, ObjectAnimator başlatma. Ağ/disk yok. |
| Animasyon | ObjectAnimator/AnimatorSet — UI thread'de çalışır, kısa süreli (≈2 sn), bloklamaz. |
| route() | Sadece subtitle animasyonunun withEndAction içinde ve ensureMinDurationThenRoute → postDelayed ile. onResume/onStart'ta çağrılmıyor. |
| **ANR riski** | **Yok** |

---

## 2. Namaz bildirimleri (alarm ve receiver)

| Konu | Durum |
|------|--------|
| **PrayerAlarmReceiver** | onReceive içinde sadece: intent okuma, cache/prefs okuma, shouldFireNotification (hafif), NotificationHelper.sendNow, **rescheduleOnBackground** (executor.execute). **rescheduleAll ana thread'de çağrılmıyor.** |
| **PrayerRescheduleReceiver** | Tüm iş (DailyAlarmScheduler + PrayerReminderScheduler) **rescheduleExecutor.execute** ile arka planda. Receiver hemen dönüyor. |
| **ViewModel / BottomSheet** | rescheduleAll **prayerSchedulerExecutor** / **prayerScheduleExecutor** ile arka planda. |
| **PrayerTimesRefreshWorker** | rescheduleAll zaten Worker thread'de (doWork). |
| **ANR riski** | **Azaltıldı** — alarm kurulumu ana thread'den kaldırıldı. |

---

## 3. NotificationHelper (debounce)

| Konu | Durum |
|------|--------|
| Ek iş | Sadece `System.currentTimeMillis()` ve iki long karşılaştırması. |
| **ANR riski** | **Yok** |

---

## 4. Statik executor'lar

| Konu | Durum |
|------|--------|
| Kullanım | 4 adet `Executors.newSingleThreadExecutor()`: PrayerAlarmReceiver, ViewModel, PrayerRescheduleReceiver, BottomSheet. |
| Deadlock | Her biri sadece rescheduleAll (AlarmManager) çalıştırıyor; birbirini beklemiyor. |
| **Kilitlenme riski** | **Yok** |

---

## 5. Son düzeltme (PrayerRescheduleReceiver)

- **Önce:** DailyAlarmScheduler.rescheduleAll(context) **ana thread'de** (onReceive içinde) çağrılıyordu.
- **Şimdi:** Hem Daily hem Prayer reschedule **rescheduleExecutor.execute** içinde; receiver anında bitiyor.
- **Sonuç:** BroadcastReceiver ANR penceresi (≈10 sn) zorlanmıyor.

---

## Özet

- Splash: Ağ/disk yok, kısa animasyon; ANR riski yok.
- Namaz/Daily alarm kurulumu: Tamamı arka plan thread'lerinde; ANR riski azaltıldı.
- Debounce ve diğer ekler: Hafif, ana thread’i bloklamıyor.
- Bu geliştirmeler **ANR veya kilitlenme oranını yükseltmez**; receiver ve ViewModel/BottomSheet tarafında **oranı düşürür**.
