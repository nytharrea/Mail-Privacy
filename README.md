# Mailx Privacy Android (Rust Native Skeleton + Compose Material 3 Expressive)

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) altyapısı için geliştirilmiş, çekirdeği ve uygulama iskeleti tamamen **Rust (`mailx-core` v0.5.0)** tabanlı olan, arayüz katmanında ise **[Jetpack Compose Material 3 Expressive (`1.5.0-rc01`)](https://developer.android.com/jetpack/androidx/releases/compose-material3)** ([Material 3 Expressive Motion Theming](https://m3.material.io/blog/m3-expressive-motion-theming)) ve **[Lucide Icons](https://lucide.dev/) + Material Symbols Rounded** vektör ikon sistemini kullanan modern Android e-posta gizlilik istemcisidir.

## Mimari: Rust Tabanlı Uygulama İskeleti (`rust-core` / `libmailx_core.so`)

Uygulamanın yalnızca ağ katmanı değil, **tüm uygulama iskeleti, durum filtreleme/sıralama motoru, 7 günlük telemetri hesaplayıcısı, CSV ayrıştırıcısı ve kriptografik bellek yönetimi** C/C++ bağımlılığı olmadan saf **Rust** (`rust-core`) üzerinde çalışır:

| Modül / Dosya | Görev ve Sorumluluklar |
| :--- | :--- |
| `rust-core/src/skeleton.rs` | **Rust Uygulama İskeleti (`MailxSkeletonRuntime`)**: Takma adların aranması, çip filtrelemesi (`active_inactive`, `pinned_only`, `wildcard_only`, `active`, `inactive`, `deleted`), sabitlenenlerin önceliklendirilmesi ve çoklu kritere göre sıralanması (`filter_and_sort_aliases`); Howard Hinnant takvim algoritmasıyla 7 günlük mesaj telemetrisinin hesaplanması (`compute_seven_day_telemetry`); CSV dışa aktarma (`format_aliases_csv_export`) ve özel alan adı eşlemeli CSV içe aktarma planlayıcısı (`parse_aliases_csv_import`). |
| `rust-core/src/client.rs` | `ureq` + `rustls` tabanlı TLS istemcisi; `POST /v1/login` (E-posta, Parola, 2FA TOTP) ve `POST /v1/api/authenticate` (85 karakterlik Access Key) oturum yönetimi, otomatik Bearer token yenileme ve tüm REST API uç noktaları. |
| `rust-core/src/secret.rs` | `SecretString` yapısı ile hassas oturum anahtarlarını ve parolaları `Drop` anında `std::ptr::write_volatile` kullanarak RAM'den sıfırlama (zeroize). |
| `rust-core/src/utils.rs` | Ters takma ad (`alias+hedef=alanadi.com@mailx.net`) üretimi, Wildcard (`*+` ve `*.`) anlık adres üretimi, çevrimdışı Base64URL `#fragment` **İzleyici Raporu Çözücü**, **NATO Fonetik Alfabe Kodlayıcı (`encode_nato_phonetic`)**, xorshift64 tabanlı **Rastgele Sonek Üretici (`generate_random_suffix`)** ve **Gizlilik Duruşu Hesaplayıcı (`compute_privacy_posture`)**. |
| `rust-core/src/jni_bridge.rs` | `catch_unwind` ile panik-korumalı JNI köprüsü (`JniEnvelope<T>`), tüm yerel Rust iskelet ve ağ işlevlerini JSON zarfı ile Android Kotlin katmanına (`MailxRustBridge.kt`) güvenli şekilde aktarır. |

## Arayüz: Compose Material 3 Expressive & Lucide Icons

- **Material 3 Expressive (`androidx.compose.material3:material3:1.5.0-rc01` & Compose BOM `2026.09.00`)**:
  - `MaterialExpressiveTheme` ve `MotionScheme.expressive()` ile yay fiziği (spring physics) tabanlı uzamsal (`defaultSpatialSpec`, `fastSpatialSpec`) ve efekt (`defaultEffectsSpec`) animasyonları.
  - Seçim ve kopyalama durumlarında dinamik köşe yarıçapı dönüşümü (**Shape Morphing**: `18.dp` ↔ `24.dp`) ve vurgulu tipografi (`MailxTypography` — `ExtraBold` / `Bold` başlık hiyerarşisi).
  - Ayarlar ekranından çalışma zamanında **Material 3 Expressive Motion Physics (`MotionScheme.expressive()`)** ile `MotionScheme.standard()` arasında geçiş imkanı.
- **Lucide Icons (`com.composables:icons-lucide-android:2.2.1`) & Material Symbols Rounded (`MailxIcons.kt`)**:
  - Tüm alt navigasyon sekmeleri, üst çubuk eylemleri, durum rozetleri, arama/filtre çubukları ve hızlı araçlarda 24x24 `2dp` yuvarlatılmış konturlu **Lucide Icons v1.70** ve **Material Symbols** vektör ikonları.

## Derleme ve Test

```bash
cargo test --workspace --all-features
```
