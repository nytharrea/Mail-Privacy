# Mailx Privacy Android (Rust Native Core + Clean Material 3 UI)

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) altyapısı için geliştirilmiş, çekirdeği ve uygulama iskeleti tamamen **Rust (`mailx-core` v0.5.0)** tabanlı olan, arayüz katmanında ise **Jetpack Compose Material 3** ve her temada renklendirilebilir (**tintable**) vektör ikon sistemini kullanan modern, sade ve gizlilik odaklı Android e-posta gizlilik istemcisidir.

## Mimari: Rust Tabanlı Uygulama İskeleti (`rust-core` / `libmailx_core.so`)

Uygulamanın yalnızca ağ katmanı değil, **tüm uygulama iskeleti, durum filtreleme/sıralama motoru, 7 günlük telemetri hesaplayıcısı, CSV ayrıştırıcısı ve kriptografik bellek yönetimi** C/C++ bağımlılığı olmadan saf **Rust** (`rust-core`) üzerinde çalışır:

| Modül / Dosya | Görev ve Sorumluluklar |
| :--- | :--- |
| `rust-core/src/skeleton.rs` | **Rust Uygulama İskeleti (`MailxSkeletonRuntime`)**: Takma adların aranması, çip filtrelemesi (`active_inactive`, `pinned_only`, `wildcard_only`, `active`, `inactive`, `deleted`), sabitlenenlerin önceliklendirilmesi ve çoklu kritere göre sıralanması (`filter_and_sort_aliases`); Howard Hinnant takvim algoritmasıyla 7 günlük mesaj telemetrisinin hesaplanması (`compute_seven_day_telemetry`); CSV dışa aktarma (`format_aliases_csv_export`) ve özel alan adı eşlemeli CSV içe aktarma planlayıcısı (`parse_aliases_csv_import`). |
| `rust-core/src/client.rs` | `ureq` + `rustls` tabanlı TLS istemcisi; `POST /v1/login` (E-posta, Parola, 2FA TOTP) ve `POST /v1/api/authenticate` (85 karakterlik Access Key) oturum yönetimi, otomatik Bearer token yenileme ve tüm REST API uç noktaları. |
| `rust-core/src/secret.rs` | `SecretString` yapısı ile hassas oturum anahtarlarını ve parolaları `Drop` anında `std::ptr::write_volatile` kullanarak RAM'den sıfırlama (zeroize). |
| `rust-core/src/utils.rs` | Ters takma ad (`alias+hedef=alanadi.com@mailx.net`) üretimi, Wildcard (`*+` ve `*.`) anlık adres üretimi, çevrimdışı Base64URL `#fragment` **İzleyici Raporu Çözücü**, **NATO Fonetik Alfabe Kodlayıcı (`encode_nato_phonetic`)**, xorshift64 tabanlı **Rastgele Sonek Üretici (`generate_random_suffix`)** ve **Gizlilik Duruşu Hesaplayıcı (`compute_privacy_posture`)**. |
| `rust-core/src/jni_bridge.rs` | `catch_unwind` ile panik-korumalı JNI köprüsü (`JniEnvelope<T>`), tüm yerel Rust iskelet ve ağ işlevlerini JSON zarfı ile Android Kotlin katmanına (`MailxRustBridge.kt`) güvenli şekilde aktarır. |

## Arayüz: Sakin, Güven Veren ve Modern Material 3 Tasarımı

- **Renk Paleti & Tonal Hiyerarşi (`Color.kt`, `Theme.kt`)**:
  - Koyu temada temiz nötr arduvaz arka plan (`#0B0E11`) ve sofistike teal primary (`#14B8A6`); açık temada yüksek kontrastlı `#0D9488` primary.
  - Desatüre edilmiş durum renkleri (`ForwardEmerald`, `ReplySky`, `SentIndigo`); yalnızca kritik uyarı durumları (`BlockedRose`, `PausedAmber`) canlı vurgulanır.
  - Net tonal yüzey hiyerarşisi (`surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`), ince `BorderStroke` kullanımı ve sıfır gereksiz gölge/elevation.
  - **Material You (Dynamic Color)** varsayılan olarak açıktır; **Saf Siyah (AMOLED)** modunda WCAG AA/AAA metin kontrastı korunur.
- **Sakin Hareket & Azaltılmış Hareket Desteği (`Theme.kt`, `MainActivity.kt`, `AliasCard.kt`)**:
  - Varsayılan hareket şeması **`MotionScheme.standard()`** olarak ayarlanmıştır (isteğe bağlı Expressive Motion ayarlardan açılabilir).
  - Sistem ve uygulama içi **Reduced Motion (`LocalReducedMotion`)** desteği mevcuttur.
  - Köşe yarıçapları `10.dp`–`20.dp` (`medium = 14.dp`, `large = 20.dp`) aralığında dengelenmiş, tipografide `SemiBold` / `Medium` ağırlıkları tercih edilmiştir.
- **Optik Dengeli, Tint Edilebilir İkon Sistemi (`MailxIcons.kt`)**:
  - Hardcoded beyaz stroke kaldırılarak açık ve koyu temada `%100` uyumlu, `24.dp` / `1.75f` tutarlı ağırlıklı vektör ikon sistemi kullanılmıştır.

## Derleme ve Test

```bash
cargo test --workspace --all-features
```
