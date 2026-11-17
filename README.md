# Mailx Android (Rust Core + Jetpack Compose Material 3)

[IVPN Mailx](https://github.com/ivpn/mailx) ([mailx.net](https://mailx.net/)) açık kaynaklı e-posta takma ad (alias) servisi için geliştirilmiş, **Rust çekirdekli (`mailx-core`)**, **Android 16 KB sayfa boyutu uyumlu**, **çoklu ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`)** destekli ve [anonaddy/addy-android](https://github.com/anonaddy/addy-android) sistem entegrasyonlarını barındıran modern Android istemcisidir.

## Öne Çıkan Özellikler

1. **Rust Çekirdek (`rust-core` / `libmailx_core.so`)**:
   - `POST /v1/api/authenticate`, `GET /v1/api/aliases`, `POST /v1/api/alias`, `PUT /v1/api/alias/:id`, `PUT /v1/api/alias/:id/pin`, `DELETE /v1/api/alias/:id`, `GET /v1/api/defaults` ve `POST /v1/api/logout` uç noktalarını `ureq` + `rustls` ile çalıştırır.
   - `401 Unauthorized` durumunda saklı 85 karakterlik Access Key ile 5 deneme / 10 dk sınırına takılmadan **yalnızca 1 kez** sessizce token yeniler.
   - `SecretString` ile Access Key ve Bearer Token değerlerini bellekten düşerken (`Drop`) sıfırlar.
   - Ters-alias (`Send from alias`: `alias+alici=domain.com@mailx.net`) ve Wildcard on-the-fly (`prefix+wildcard@domain`) adreslerini yerel olarak Rust'ta üretir.
2. **Android 16 KB Sayfa Boyutu (Page Size) Desteği**:
   - `.cargo/config.toml` içinde tüm Android hedefleri için `-Wl,-z,max-page-size=16384` ve `-Wl,-z,common-page-size=16384` tanımlıdır.
   - `android-app/app/build.gradle.kts` içinde **NDK r30 LTS (`30.0.16248370`)** ve `useLegacyPackaging = false` kullanılır.
   - GitHub Actions iş akışında hem `llvm-objdump -p` ile ELF `2**14` segment hizalaması hem de `zipalign -c -v -P 16 4` doğrulaması otomatik yapılır.
3. **Çoklu ABI Desteği**:
   - `arm64-v8a` (`aarch64-linux-android`)
   - `armeabi-v7a` (`armv7-linux-androideabi`)
   - `x86_64` (`x86_64-linux-android`)
   - `x86` (`i686-linux-android`)
   - Hem her mimari için ayrı küçük boyutlu Split APK hem de tek parça Universal APK üretilir.
4. **`addy-android`'den Uyarlanan Android Sistem Entegrasyonları**:
   - **Quick Settings Tile (`QuickAliasTileService.kt`)**: Bildirim panelinden uygulamayı açmadan tek dokunuşla yeni alias oluşturup panoya kopyalar.
   - **Ters-Alias Gönderim Yakalayıcı (`ReverseAliasSendActivity.kt`)**: Herhangi bir uygulamada e-posta adresini seçip sağ tık menüsünden veya `mailto:` bağlantısından doğrudan Mailx ters-alias adresi oluşturur.
   - **AndroidX Security Crypto (`EncryptedPrefsManager.kt`)**: Access Key ve önbelleği AES256-GCM ile Android Keystore üzerinde şifreli saklar.

## GitHub Actions ile Derleme ve Kendi Anahtarınızla İmzalama

Depoyu GitHub'a yükledikten sonra **Settings → Secrets and variables → Actions → New repository secret** bölümüne şu 4 gizli değişkeni ekleyin:

| Secret Adı | Açıklama |
| :--- | :--- |
| `KEYSTORE_BASE64` | `.jks` veya `.keystore` dosyanızın `base64 -w 0 imzam.jks` çıktısı |
| `KEYSTORE_PASSWORD` | Keystore parolası |
| `KEY_ALIAS` | Anahtar takma adı (alias) |
| `KEY_PASSWORD` | Anahtar parolası |

### Termux Üzerinde Keystore Oluşturma ve Base64 Çıktısı Alma

```bash
keytool -genkeypair -v -keystore mailx-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mailx
base64 -w 0 mailx-release.jks > keystore.b64
```

Her `v*` etiketinde (ör. `git tag v0.1.0 && git push origin v0.1.0`) veya **Actions → Android Multi-ABI 16KB Build & Sign → Run workflow** tetiklemesinde APK'lar derlenir, 16 KB hizalaması doğrulanır, anahtarınızla imzalanır ve GitHub Releases altına yüklenir.
