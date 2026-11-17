# Mailx Privacy (Pure Rust Core + `al1h3n/liquid-rust` iOS 26/27 Liquid Glass UI)

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) altyapısı için geliştirilmiş, [al1h3n/liquid-rust](https://github.com/al1h3n/liquid-rust) optik ve fizik motorunu temel alan **Saf Rust (`mailx-core`)** çekirdekli e-posta gizlilik ve güvenlik istemcisidir.

## `al1h3n/liquid-rust` iOS 26/27 Liquid Glass Mimarisi

Klasik bulanıklaştırma (glassmorphism) yerine Apple'ın **iOS 26/27 Figma kitine** piksel-piksel kalibre edilmiş optik kırılma, süperelips geometrisi ve analitik yay fiziği Rust çekirdeğinde (`rust-core/src/liquid_glass.rs`) hesaplanır ve GPU shader (`WGSL` + Android 13+ `AGSL RuntimeShader`) katmanında gerçek zamanlı çizilir:

- **Squircle Mercek Kırılması (Snell Yasası, $\eta = 1.5$) ve Renk Sapması (Dispersion)**: Yüzey kenarlarındaki %60 Figma köşe yumuşatmalı süperelips (`Shape::Rounded`) eğimi üzerinden Snell kırılması ve RGB kanal bazlı prizmatik renk sapması üretilir.
- **Sıvı Boyun Birleşmesi (`Smooth-Union SDF` & `Liquid Neck`)**: Aynı `ContainerId` içerisindeki cam bileşenler (`LiquidGlassBottomBar`, `LiquidGlassTopBar`) birbirine yaklaştığında polinomik `smin` mesafe alanı ile aralarında sıvı bir boyun (`liquid neck`) oluşturarak kaynaşır ve ayrılır (`expand_from`, `collapse_into`, `morph_from`, `shrink_into`).
- **Analitik Apple Yay Fiziği (`Spring::EMERGE` & `Spring::RECEDE`)**: Kare hızından bağımsız (60/120 Hz), hareket ortasında hızını koruyarak yeniden hedeflenebilen sönümlü harmonik osilatör ve hareketsizken sıfır CPU/GPU maliyeti (`Scene::update(dt) -> false`).
- **Apple Materyal Ön Ayarları ve iOS 27 Saydamlık Kaydırıcısı (`0.0..=1.0`)**: `Material::regular()`, `Material::clear()`, `Material::clear_bar()`, `Material::tinted(Color)` ve `Material::identity()` ön ayarları; orta griye kaymadan koyu temada `#1C1C1E` (%70–85), açık temada parlak beyaz (%70–88) yüzeye geçiş yapan canlı saydamlık kaydırıcısı ve WCAG kontrast doğrulaması.

## Mimari ve Saf Rust Çekirdeği (`rust-core` / `libmailx_core.so`)

Uygulamanın ağ iletişimi, sıfır-bilgi bellek temizliği (`SecretString`), Liquid Glass sahne/optik fiziği ve çevrimdışı gizlilik laboratuvarı C/C++ bağımlılığı olmadan saf **Rust** ile yazılmış `mailx-core` kütüphanesinde çalışır:

| Modül / Dosya | Görev ve Sorumluluklar |
| :--- | :--- |
| `rust-core/src/liquid_glass.rs` | [al1h3n/liquid-rust](https://github.com/al1h3n/liquid-rust) optik ve fizik motoru: `Scene`, `Glass`, `Material`, `Shape`, `Spring`, `Appearance`, `smin_polynomial` sıvı boyun birleşmesi, `evaluate_nav_bar_liquid_state`, `WGSL` ve `AGSL` shader üreticileri. |
| `rust-core/src/utils.rs` | **Çevrimdışı URL İzleyici Temizleyici (`sanitize_tracking_urls`)**, **RFC 5322 E-posta Başlığı ve SPF/DKIM/DMARC Denetçisi (`analyze_email_headers`)**, **Takma Ad Hijyen ve Sızıntı Denetçisi (`audit_alias_hygiene`)**, **Bağlamsal Yüksek Entropili Takma Ad Üretici (`generate_contextual_aliases`)**, Ters Takma Ad (`Reverse-Alias`), Wildcard (`*+` / `*.`), Base64URL `#fragment` İzleyici Raporu Çözücü ve NATO Fonetik Kodlayıcı. |
| `rust-core/src/client.rs` | `ureq` + `rustls` tabanlı TLS istemcisi; `POST /v1/login` (E-posta, Parola, 2FA TOTP) ve `POST /v1/api/authenticate` (85 karakterlik Access Key) oturum yönetimi ve otomatik Bearer token yenileme. |
| `rust-core/src/secret.rs` | `SecretString` yapısı ile hassas oturum anahtarlarını ve parolaları `Drop` anında `std::ptr::write_volatile` kullanarak RAM'den sıfırlama. |
| `rust-core/src/jni_bridge.rs` | `catch_unwind` ile panik-korumalı JNI köprüsü (`JniEnvelope<T>`). |

## Ekstra Gizlilik ve Güvenlik Özellikleri (v0.5.0)

1. **Çevrimdışı Bağlantı İzleyici Temizleyici (`sanitize_tracking_urls`)**:
   - Metin veya e-posta içindeki bağlantılardan `utm_*`, `fbclid`, `gclid`, `msclkid`, `mc_eid`, `mkt_tok`, `_hsenc`, `igshid`, `yclid` dahil 45+ takip parametresini sunucuya ihtiyaç duymadan Rust'ta kazır.
2. **RFC 5322 E-posta Başlığı ve Kimlik Doğrulama Denetçisi (`analyze_email_headers`)**:
   - Ham e-posta başlıklarını ayrıştırarak `SPF`, `DKIM` ve `DMARC` imzalarını, `From` ile `Return-Path` / `Reply-To` alan adı uyuşmazlıklarını (oltalama/spoofing), istemci IP sızıntılarını ve MTA sunucu atlama (`Received` hop) zincirini puanlar (`0..=100`).
3. **Takma Ad Hijyen ve Korelasyon Denetçisi (`audit_alias_hygiene`)**:
   - Yüksek engelleme oranına sahip sızdırılmış ("burned") takma adları, etiketsiz aktif adresleri, aynı hizmet için mükerrer açılmış takma adları ve alıcı korelasyon riskini denetler.
4. **Hizmete Özel Yüksek Entropili Takma Ad Üretici (`generate_contextual_aliases`)**:
   - Girilen web sitesi veya uygulama adına göre `SplitMix64` tabanlı 5 farklı stratejide (`Compartment Tag`, `Stealth Word Pair`, `Zero-Link High Entropy`, `Hybrid Service + Word`, `Plus-Subaddressed`) takma ad önerir ve Shannon bit entropisini hesaplar.
5. **iOS 27 Liquid Glass Optik Stüdyosu (`SettingsTab`)**:
   - Saydamlık (`Ultra Clear` / `Apple Kit Default` / `Deep Tinted`), Snell kırılma gücü ($\eta = 1.5$), RGB prizma renk sapması, sıvı boyun birleşmesi (`Liquid Neck Merging`), `Reduce Transparency`, `Increase Specular Contrast` ve saf siyah (`AMOLED #000000`) kontrollerini canlı olarak sunar.
