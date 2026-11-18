# Mailx Privacy Android — Unified Rust Core & Skeleton + Premium Material 3 Expressive UI

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) altyapısı için geliştirilmiş; **çekirdeği ve tüm uygulama iskeleti %100 saf Rust (`mailx-core` v0.5.0)** üzerinde çalışan, arayüz katmanında ise baştan yazılmış **Material 3 Expressive (Electric Indigo + Cyber Cyan/Teal + Obsidian/Pearl Tonal Hiyerarşi)** tasarım sistemini kullanan modern, premium ve gizlilik odaklı Android istemcisidir.

---

## 1. Mimari: 24 Modüllü Rust Çekirdek ve Uygulama İskeleti (`rust-core` / `libmailx_core.so`)

Uygulamanın yalnızca HTTP/TLS ağ katmanı değil; **durum makinesi, önbellek senkronizasyonu, filtreleme/sıralama motoru, kural simülatörü, RFC-4880 OpenPGP paket/CRC24 denetçisi, RFC-5322/MIME başlık analizi, oltalama (phishing) & IDN homograf kalkanı, RFC-3463 MTA geri dönen (bounce) tanı motoru, RFC-6238 TOTP üreticisi ve 200+ parametrelik URL izleyici temizleyicisi** tamamen C/C++ bağımlılığı olmadan saf **Rust** üzerinde çalışır.

| Modül / Dosya | Görev ve Sorumluluklar |
| :--- | :--- |
| `src/skeleton.rs` | **Birleşik Rust Uygulama İskeleti (`MailxSkeletonRuntime`)**: Takma ad filtreleme/sıralama, 7 günlük Howard Hinnant takvim telemetrisi, CSV içe/dışa aktarma, 200+ parametrelik URL izleyici temizleyici (`sanitize_tracking_urls`), birleşik RFC-5322 + MIME + tehlikeli ek analizi (`analyze_email_headers`), bağlamsal takma ad + parola stüdyosu (`generate_contextual_aliases`), portföy hijyen denetimi (`audit_alias_hygiene`), satıcı istihbaratlı izleyici raporu çözücü (`decode_and_audit_trackers`), OpenPGP RFC-4880 zırh/paket denetçisi (`inspect_openpgp_key_armor`), oltalama & alan adı risk değerlendiricisi (`evaluate_phishing_and_domain_risk`), yerel kural simülatörü (`simulate_rule_evaluation`), RFC-3463 MTA bounce tanılayıcısı (`analyze_mta_bounce_log`) ve RFC-6238 TOTP üreticisi (`generate_totp_token`). |
| `src/controller.rs` | **Rust Durum Makinesi (`MailxSkeletonController`)**: Bellek içi önbellek (`Snapshot`), iyimser (optimistic) UI güncellemeleri, çevrimdışı arama ve özet istatistikler. |
| `src/client.rs` | `ureq` + `rustls` TLS istemcisi; `POST /v1/login` (E-posta, Parola, 2FA TOTP) ve `POST /v1/api/authenticate` (85 karakterlik Access Key) oturum yönetimi, otomatik Bearer token yenileme ve tüm IVPN Mailx REST API uç noktaları. |
| `src/pgp_inspector.rs` | **RFC-4880 OpenPGP Denetçisi**: ASCII Armor ayrıştırma, CRC24 sağlama toplamı doğrulaması, paket etiketleri (Public-Key, User ID, Signature, Subkey) ve algoritma/eğri tespiti (Ed25519/Cv25519, RSA, ECDSA). |
| `src/phishing_guard.rs` | **Oltalama & Spoofing Kalkanı**: From / Reply-To / Return-Path uyuşmazlığı, punycode (`xn--`) IDN homograf tespiti, marka taklidi ve şüpheli TLD analizi. |
| `src/mime_inspector.rs` & `src/rfc_email.rs` | **RFC-5322 & MIME Güvenlik Analizi**: `Authentication-Results` (SPF/DKIM/DMARC), `Received` MTA zinciri, TLS şifreleme oranı, gizli izleme pikseli tespiti ve çalıştırılabilir/makro ek (`exe`, `scr`, `docm`, `ps1`, çift uzantı) taraması. |
| `src/tracker_db.rs` | **İzleyici Satıcı İstihbaratı**: Bilinen e-posta takip ağlarını (HubSpot, Mailchimp, SendGrid, Customer.io, Marketo, Braze, Klaviyo, Amazon SES vb.) satıcı adı, kategori ve önem derecesiyle sınıflandırır. |
| `src/rules_engine.rs` | **Yerel Kural Değerlendirme Motoru**: Sunucuya kaydetmeden önce gelen e-posta zarfını (`From`, `To`, `Subject`) kurallara karşı yerel olarak simüle eder. |
| `src/mta_diagnostics.rs` | **RFC-3463 Gelişmiş SMTP Bounce Tanılayıcısı**: Teslimat hatalarını (`5.1.1`, `5.7.1`, `4.2.2` vb.) sınıflandırır, kalıcı/geçici ayrımı yapar ve çözüm önerisi üretir. |
| `src/dns_security.rs` & `src/disposable_domains.rs` | **Alan Adı Güvenlik & Tek Kullanımlık Sağlayıcı Denetimi**: Özel alan adı DNS (SPF/DKIM/DMARC/MX) hazırlık puanlaması ve tek kullanımlık e-posta sağlayıcı tespiti. |
| `src/alias_engine.rs` & `src/wordlist.rs` | **Kriptografik Takma Ad & Parola Motoru**: Xorshift64* ve kelime listesi tabanlı yerel takma ad adayları, bit cinsinden entropi hesabı ve 4 kelimelik yüksek entropili parola üretimi. |
| `src/totp_engine.rs` | **RFC-6238 HMAC-SHA1 TOTP Motoru**: `otpauth://` URI ayrıştırma ve 2FA kurulumunda canlı 6 haneli kod + kalan süre üretimi. |
| `src/offline_audit.rs` & `src/telemetry.rs` | **Çevrimdışı Portföy Hijyen Denetimi & Telemetri**: Spam mıknatısı / sızdırılmış takma ad tespiti, alıcı konsantrasyon analizi ve 7 günlük aktivite serisi. |
| `src/secret.rs` | `SecretString` ile hassas anahtarları `Drop` anında `std::ptr::write_volatile` kullanarak RAM'den sıfırlama (zeroize). |
| `src/jni_bridge.rs` | `catch_unwind` ile panik-korumalı JNI köprüsü (`JniEnvelope<T>`), tüm yerel Rust işlevlerini Android Kotlin katmanına (`MailxRustBridge.kt`) JSON zarfı ile aktarır. |

---

## 2. Premium Material 3 Expressive Arayüz & Yeni Özellikler

- **Material 3 Expressive Renk & Tonal Yüzey Sistemi (`Color.kt`, `Theme.kt`)**:
  - **Electric Indigo (`#6366F1` / `#4F46E5`)**, **Cyber Cyan/Teal (`#06B6D4` / `#14B8A6`)** ve **Royal Violet (`#8B5CF6`)** vurgu paleti.
  - Koyu temada derin **Obsidian (`#090D16`)** ve açık temada **Crisp Alabaster (`#F8FAFC`)** yüzey hiyerarşisi; ayrıca **Dynamic Color (Material You)** ve **AMOLED Pitch-Black (`#000000`)** modu.
- **Yenilenen Ana Ekran (`HomeDashboardTab.kt`)**:
  - **Hero Privacy Shield Card**: Canlı Rust güvenlik derecesi (`A+`..`D`), `RUST CORE` rozeti, aktif koruma çipleri (`OpenPGP`, `Tracker Shield`, `Active Rules`, `High-Spam Alias`), 3 sütunlu metrik şeridi, `MailxActivityRing` ve hızlı eylem düğmeleri (`New Alias`, `Send Mail`, `Privacy Lab`).
  - **Instant Alias & Passphrase Studio**: Ana ekrandan tek dokunuşla Rust üzerinde yerel `Words`, `Random` veya `UUID` takma ad adayı, entropi bit değeri ve 4 kelimelik kriptografik parola üretimi.
- **Yenilenen Takma Ad Kartları (`AliasCard.kt`)**:
  - Ayrıştırılmış `localPart` + `@domainPart` rozeti, **High Spam** maruziyet rozeti (`blocks >= 3 && blocks >= forwards`), mikro-telemetri ilerleme çubuğu ve animasyonlu kopyalama onayı.
- **5 Araçlı Çevrimdışı Rust Privacy & Security Lab (`PrivacyLabBottomSheet.kt`)**:
  1. **URL Sanitizer**: 200+ takip parametresini (`utm_*`, `fbclid`, `gclid`, `mc_eid`, `mkt_tok`, `_hsenc` vb.) çevrimdışı temizler.
  2. **Phishing & Spoofing Guard**: Gönderici, yanıt adresi, dönüş yolu ve konu satırını IDN homograf ve marka taklidine karşı denetler.
  3. **Header & MIME Inspector**: Ham e-posta kaynağını SPF/DKIM/DMARC, MTA TLS zinciri ve tehlikeli eklere karşı analiz eder.
  4. **Contextual Alias & Passphrase Studio**: Hizmet adına göre kategori tespiti, entropi analizi, tavsiye edilen biçim ve 4 kelimelik parola üretir.
  5. **Exposure & Hygiene Radar**: Tüm takma ad portföyünü yerel olarak denetler, sızdırılmış/yüksek spam alan adresleri raporlar.
- **Entegre Rust Güvenlik Denetçileri**:
  - **Alıcılar (`RecipientsTab.kt`)**: Her alıcı kartında **Rust Alan Adı Güven Değerlendirmesi** ve OpenPGP anahtarlarında **Canlı RFC-4880 Paket + CRC24 Denetçisi**.
  - **Kurallar & Engel Listesi (`RulesAndBlocklistTab.kt`)**: Her kural kartında **Canlı Rust Kural Simülatörü** ve engel listesi ekleme penceresinde **Canlı Rust Alan Adı Risk Denetimi**.
  - **Tanılama, İzleyici Raporu & 2FA (`AccountAndDiagnosticsSheets.kt`)**: Teslimat günlüklerinde **RFC-3463 Bounce Sınıflandırması**, izleyici raporlarında **Satıcı İstihbaratı** ve 2FA kurulumunda **Canlı RFC-6238 TOTP Önizlemesi**.

---

## 3. Derleme ve Test

```bash
cd rust-core
cargo test --workspace --all-features
cargo clippy --workspace --all-targets --all-features -- -D warnings
```
