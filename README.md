# Mailx Privacy Android (Rust Native Core + Compact Material Design 3)

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) altyapısı için geliştirilmiş, [addy.io](https://addy.io/features/) ve [addy-android](https://github.com/anonaddy/addy-android) ekosisteminin işlevsel yeteneklerinden ilham alınarak sıfırdan **özgün, modern ve kompakt** bir [Jetpack Compose Material Design 3](https://developer.android.com/develop/ui/compose/designsystems/material3) ([m3.material.io](https://m3.material.io/)) mimarisiyle tasarlanmış **Rust tabanlı (`mailx-core`)** Android e-posta gizlilik istemcisidir.

## Tasarım Felsefesi: Neden Kompakt Material Design 3?

Önceki arayüzde bulunan büyük kart yığınları, tam ekran mavi karşılama ekranı ve doğrudan `addy-android` renk/bileşen kopyaları tamamen kaldırılarak yerine yüksek bilgi yoğunluklu, tek elle kullanıma uygun **Emerald-Teal & Cyber-Slate Material 3** tasarım sistemi getirilmiştir:

- **Kompakt Üst Çubuk (`MailxCompactTopBar`)**: Geniş başlık alanları yerine aktif sekme, canlı `@domain` rozeti, tek dokunuşla **Ters Takma Ad (Reverse-Alias)** oluşturucu, **İzleyici Raporu Çözücü (Tracker Decoder)**, yenileme butonu ve **Komut Merkezi (`MailxCommandBottomSheet`)** kısayolunu tek satırda sunar.
- **Birleşik Gizlilik Kalkanı ve Telemetri Paneli (`HomeDashboardTab`)**: 14 ayrı büyük kart yerine; Rust çekirdeğinde gerçek zamanlı hesaplanan **Gizlilik Skoru (`Shield A+ / 0-100`)**, yatay telemetri dağılım çubuğu (`MailxTelemetryBar`), 4'lü metrik şeridi (`Forwarded`, `Blocked`, `Replied`, `Sent`), 7 günlük trafik grafiği ve **3x2 Kompakt Takma Ad Matrisi (`MailxStatTile`)** tek ekranda kaydırma gerektirmeden görüntülenir.
- **Yüksek Yoğunluklu Takma Ad Kartları (`AliasCard`)**: Her kartta sol durum şeridi (`Aktif: Zümrüt Yeşili`, `Duraklatılmış: Kehribar`, `Silinmiş: Mercan Kırmızısı`, `Pasif: Arduvaz Gri`), 36dp kompakt aktivite halkası (`MailxActivityRing`), ayrıştırılmış `yerel_kısım@alan_adı` tipografisi, satır içi mini telemetri rozetleri (`↑Fwd`, `⊘Blk`, `⇄Rep/Sent`) ve tek dokunuşla kopyalama / Wildcard üretme butonu yer alır.
- **Tek Ekranlı Kompakt Kurulum (`SetupScreen`)**: İkinci bir alt pencere açmaya gerek kalmadan doğrudan tek ekranda **E-posta & Parola (+ 2FA TOTP)** veya **85 Karakterlik Erişim Anahtarı (Access Key)** ile resmi (`api.mailx.net`) ya da kendi barındırdığınız (`Self-Hosted`) sunucuya bağlanma imkanı sağlar.
- **Dinamik Renk (Material You) ve Saf Siyah (AMOLED `#000000`)**: Android 12+ duvar kağıdı renk uyumu ve OLED ekranlar için gerçek `#000000` yüzey hiyerarşisi içerir.

## Mimari ve Rust Çekirdeği (`rust-core` / `libmailx_core.so`)

Uygulamanın tüm ağ iletişimi, kriptografik bellek temizliği, veri doğrulama ve gizlilik analizleri C/C++ bağımlılığı olmadan saf **Rust** ile yazılmış `mailx-core` kütüphanesinde çalışır:

| Modül / Dosya | Görev ve Sorumluluklar |
| :--- | :--- |
| `rust-core/src/client.rs` | `ureq` + `rustls` tabanlı TLS istemcisi; `POST /v1/login` (E-posta, Parola, 2FA TOTP) ve `POST /v1/api/authenticate` (85 karakterlik Access Key) oturum yönetimi, otomatik Bearer token yenileme ve tüm REST API uç noktaları. |
| `rust-core/src/secret.rs` | `SecretString` yapısı ile hassas oturum anahtarlarını ve parolaları `Drop` anında `std::ptr::write_volatile` kullanarak RAM'den sıfırlama (zeroize). |
| `rust-core/src/utils.rs` | Ters takma ad (`alias+hedef=alanadi.com@mailx.net`) üretimi, Wildcard (`*+` ve `*.`) anlık adres üretimi, çevrimdışı Base64URL `#fragment` **İzleyici Raporu Çözücü**, **NATO Fonetik Alfabe Kodlayıcı (`encode_nato_phonetic`)**, xorshift64 tabanlı **Rastgele Sonek Üretici (`generate_random_suffix`)** ve **Gizlilik Duruşu Hesaplayıcı (`compute_privacy_posture`)**. |
| `rust-core/src/jni_bridge.rs` | `catch_unwind` ile panik-korumalı JNI köprüsü (`JniEnvelope<T>`), tüm yerel Rust işlevlerini JSON zarfı ile Android Kotlin katmanına (`MailxRustBridge.kt`) güvenli şekilde aktarır. |

## Temel Özellikler

1. **Gelişmiş Takma Ad (Alias) ve Toplu İşlem Yönetimi**:
   - `Words`, `Random`, `UUID`, `Wildcard (*)` ve özel alan adlarında `Custom` formatlarında takma ad oluşturma.
   - Takma adları sabitleme (`Pin`), geçici olarak duraklatma (`1-10 saat/gün/hafta Snooze/Pause`), açıklama ve gönderici adı (`From Name`) düzenleme, çöp kutusuna taşıma, geri yükleme (`Restore`) veya kalıcı olarak unutma (`Forget`).
   - Çoklu seçim modu ile toplu etkinleştirme, devre dışı bırakma, sabitleme, silme ve geri yükleme (`/v1/aliases/bulk/*`).
2. **Rust Tabanlı NATO Fonetik Denetçi ve Ters Takma Ad (Reverse-Alias)**:
   - Takma ad detay sayfasında adresi telefonla veya sözlü olarak kodlarken hata yapmamak için Rust tarafından üretilen yatay **NATO Fonetik Alfabe** şeridi.
   - Gerçek e-posta adresinizi ifşa etmeden herhangi bir alıcıya takma adınız üzerinden e-posta göndermek için `alias+alici=domain.com@mailx.net` formatında **Reverse-Alias** üretici, sistem geneli `mailto:` bağlantısı yakalayıcı ve metin seçimi (`PROCESS_TEXT`) entegrasyonu (`ReverseAliasSendActivity.kt`).
3. **Çevrimdışı İzleyici Raporu Çözücü (Offline Tracker Inspector)**:
   - Yönlendirilen e-postalardaki `#fragment` izleyici raporlarını hiçbir sunucuya istek göndermeden cihaz üzerinde Rust ile (`nativeDecodeTrackersReport`) декоde ederek engellenen takip piksellerini ve casus bağlantıları listeler.
4. **Alıcılar (Recipients) ve PGP Şifreleme**:
   - Çoklu alıcı ekleme, 6 haneli OTP doğrulama, **PGP Açık Anahtarı (Public Key)** yükleme, PGP şifrelemeyi açıp kapatma ve `PGP/MIME` ile `PGP/Inline` modları arasında geçiş yapma.
5. **Özel Alan Adları (Custom Domains) ve Canlı DNS Doğrulama**:
   - Kendi alan adınızı ekleme, `TXT`, `MX`, `SPF`, `DKIM` ve `DMARC` kayıtlarını tek dokunuşla kopyalama, canlı DNS doğrulaması (`/v1/domain/:id/verify-dns`) ve `Catch-All` yapılandırması.
6. **Otomasyon Kuralları (Rules) ve Engelleme Listesi (Blocklist)**:
   - `AND` / `OR` mantıksal operatörleriyle en fazla 5 koşul (`sender`, `sender_domain`, `subject`, `alias`, `is_mailing_list`, `has_attachments`, `alias_is_wildcard` vb.) ve 5 eylem (`block`, `replace_subject`, `forward_to`, `remove_attachments`, `hide_banner`, `deactivate_alias` vb.) içeren gelişmiş e-posta kuralları oluşturma, kopyalama ve sıralama.
   - İstenmeyen gönderici adreslerini veya tüm alan adlarını engelleme listesine ekleme.
7. **Hesap Güvenliği, Tanılama ve Sistem Entegrasyonu**:
   - 2FA TOTP kurulumu ve yedek kurtarma kodları, Passkey yönetimi, 85 karakterlik API Erişim Anahtarı oluşturma/iptal etme, CSV olarak takma adları dışa/içe aktarma.
   - `/livez` ve `/readyz` sunucu sağlık kontrolü, teslimat/geri dönen (bounce) MTA günlüklerini inceleme.
   - Android Hızlı Ayarlar Karesi (`QuickAliasTileService.kt`) ile uygulamayı açmadan bildirim panelinden tek dokunuşla yeni takma ad oluşturup panoya kopyalama.
8. **Android 16 KB Bellek Sayfası Hizalaması ve Çoklu ABI Desteği**:
   - `.cargo/config.toml` içerisinde `arm64-v8a`, `armeabi-v7a`, `x86_64` ve `x86` hedefleri için `-Wl,-z,max-page-size=16384` ve `-Wl,-z,common-page-size=16384` bayraklarıyla Android 15/16+ 16 KB sayfa boyutu uyumluluğu.

## Proje Dizin Yapısı

```text
Mail-Privacy/
├── Cargo.toml
├── .cargo/config.toml
├── .github/workflows/
│   ├── android-release.yml
│   └── ci.yml
├── rust-core/
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs
│       ├── client.rs
│       ├── jni_bridge.rs
│       ├── models.rs
│       ├── secret.rs
│       └── utils.rs
└── android-app/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── app/
        ├── build.gradle.kts
        └── src/main/java/net/mailx/android/
            ├── core/
            │   ├── EncryptedPrefsManager.kt
            │   └── MailxRustBridge.kt
            ├── service/
            │   └── QuickAliasTileService.kt
            └── ui/
                ├── MainActivity.kt
                ├── components/
                │   ├── AliasCard.kt
                │   ├── DonutChart.kt
                │   └── StatCards.kt
                ├── intent/
                │   └── ReverseAliasSendActivity.kt
                ├── model/
                │   └── UiAlias.kt
                ├── screens/
                │   ├── AliasesTab.kt
                │   ├── HomeDashboardTab.kt
                │   ├── RecipientsTab.kt
                │   ├── RulesAndBlocklistTab.kt
                │   ├── SettingsTab.kt
                │   └── SetupScreen.kt
                ├── sheets/
                │   ├── AccountAndDiagnosticsSheets.kt
                │   ├── AddAliasBottomSheet.kt
                │   ├── FilterOptionsBottomSheet.kt
                │   ├── ManageAliasBottomSheet.kt
                │   ├── ReverseAliasSendBottomSheet.kt
                │   └── WildcardGeneratorBottomSheet.kt
                ├── theme/
                │   ├── Color.kt
                │   ├── Theme.kt
                │   └── Type.kt
                └── viewmodel/
                    └── MailxViewModel.kt
```

## Derleme ve GitHub Actions ile İmzalı APK Üretimi

### Yerel Rust Testleri
```bash
cargo test --workspace --all-features
```

### GitHub Actions Otomatik Derleme ve İmzalama
Depoda yer alan `.github/workflows/android-release.yml` iş akışı; `arm64-v8a`, `armeabi-v7a`, `x86_64` ve `x86` mimarileri için `libmailx_core.so` kütüphanesini NDK r30 LTS ile 16 KB hizalı olarak derler, imzalar ve GitHub Releases üzerinden yayınlar. Otomatik imzalama için GitHub deponuzun **Settings → Secrets and variables → Actions** bölümüne şu gizli değişkenleri ekleyin:

| Secret Adı | Açıklama |
| :--- | :--- |
| `KEYSTORE_BASE64` | `.jks` veya `.keystore` dosyasının Base64 çıktısı (`base64 -w 0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore parolası |
| `KEY_ALIAS` | Anahtar takma adı (alias) |
| `KEY_PASSWORD` | Anahtar parolası |
