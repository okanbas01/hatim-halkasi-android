# 🕌 Mushaf Font Kurulumu

Bu proje gerçek Kur'an-ı Kerim mushaf hissi vermek için **KFGQPC Uthmanic Script HAFS** fontunu kullanmaktadır.

## 📥 Font Dosyasını İndirme

Font dosyasını şu kaynaklardan indirebilirsiniz:

1. **GitHub**: https://github.com/mustafa0x/qpc-fonts
2. **OnlineWebFonts**: https://www.onlinewebfonts.com/fonts/KFGQPC_HAFS_Uthmanic
3. **Arabic Fonts**: https://arabicfonts.net/fonts/kfgqpc-uthmanic-script-hafs-regular

**İndirilecek dosya**: `KFGQPC Uthmanic Script HAFS Regular.ttf` veya `kfgqpc_uthmanic_hafs.ttf`

## 📁 Font Dosyasını Ekleme

Font dosyasını projeye eklemek için iki yöntem var:

### Yöntem 1: res/font Klasörüne Ekleme (Önerilen)

1. `app/src/main/res/font/` klasörünü oluşturun (eğer yoksa)
2. Font dosyasını `kfgqpc_uthmanic_hafs.ttf` olarak adlandırın
3. Dosyayı `app/src/main/res/font/` klasörüne kopyalayın

### Yöntem 2: assets Klasörüne Ekleme

1. `app/src/main/assets/` klasörünü oluşturun (eğer yoksa)
2. `assets` klasörü içinde `fonts` klasörü oluşturun
3. Font dosyasını `kfgqpc_uthmanic_hafs.ttf` olarak adlandırın
4. Dosyayı `app/src/main/assets/fonts/` klasörüne kopyalayın

## ✅ Kontrol

Font dosyası eklendikten sonra:

1. Projeyi temizleyin: `Build > Clean Project`
2. Projeyi yeniden derleyin: `Build > Rebuild Project`
3. Uygulamayı çalıştırın

Font yüklenemezse, sistem otomatik olarak serif font kullanacaktır.

## 🎨 Özellikler

- ✅ Gerçek mushaf hissi
- ✅ Net ve doğru harekeler (ötre/cezm ayrımı)
- ✅ Besmele ayrı blok olarak gösterilir
- ✅ Ayet numaraları mushaf stili
- ✅ Sürekli akış (kart stil yok)

## 📝 Notlar

- Font dosyası yaklaşık 240-250 KB boyutundadır
- Font yüklenemezse uygulama serif font ile çalışmaya devam eder
- Harekelerin net görünmesi için `includeFontPadding="false"` ayarı kullanılmıştır
