use crate::models::AliasFilter;

/// Mailx Access Key uzunluk ve biçim doğrulaması (`req.go`: `validate:"required,min=85,max=85"`).
/// Format: `<36-char-uuid>.<48-char-token>` = toplam 85 karakter.
pub fn validate_access_key(access_key: &str) -> Result<(), String> {
    let trimmed = access_key.trim();
    if trimmed.len() != 85 {
        return Err(format!(
            "Geçersiz Access Key uzunluğu: {} karakter (tam 85 karakter olmalı).",
            trimmed.len()
        ));
    }
    let parts: Vec<&str> = trimmed.split('.').collect();
    if parts.len() != 2 || parts[0].len() != 36 || parts[1].len() != 48 {
        return Err(
            "Geçersiz Access Key formatı: '<36-karakter-UUID>.<48-karakter-token>' bekleniyor."
                .to_string(),
        );
    }
    Ok(())
}

/// Mailx Ters-Alias (Send from Alias) adres üreticisi (`app/src/components/AliasSend.vue` satır 91).
///
/// Formül: `alias.name.replace('@', '+' + toEmail.replace('@', '=') + '@')`
/// Örnek:
/// - Alias: `autumn.wind42@mailx.net`
/// - Alıcı: `destek@ornek.com`
/// - Üretilen Gönderim Adresi: `autumn.wind42+destek=ornek.com@mailx.net`
pub fn generate_reverse_alias(alias_email: &str, target_email: &str) -> Result<String, String> {
    let alias_trimmed = alias_email.trim();
    let target_trimmed = target_email.trim();

    let (alias_local, alias_domain) = alias_trimmed
        .split_once('@')
        .ok_or_else(|| "Geçersiz alias e-posta adresi".to_string())?;
    if alias_local.is_empty() || alias_domain.is_empty() || !alias_domain.contains('.') {
        return Err("Geçersiz alias e-posta adresi".to_string());
    }

    let (target_local, target_domain) = target_trimmed
        .split_once('@')
        .ok_or_else(|| "Geçersiz hedef e-posta adresi".to_string())?;
    if target_local.is_empty() || target_domain.is_empty() || !target_domain.contains('.') {
        return Err("Geçersiz hedef e-posta adresi".to_string());
    }

    Ok(format!(
        "{}+{}={}@{}",
        alias_local, target_local, target_domain, alias_domain
    ))
}

/// Wildcard alias (`*+suffix@domain` veya `*.suffix@domain`) üzerinden anında (on-the-fly)
/// yeni bir takma ad üretir (`api/internal/model/alias_name.go` & `Mailx FAQ`).
///
/// Örnek:
/// - Wildcard Alias: `*+shopping@mailx.net`
/// - Önek (Prefix): `amazon`
/// - Sonuç: `amazon+shopping@mailx.net`
pub fn generate_wildcard_on_the_fly(wildcard_alias: &str, prefix: &str) -> Result<String, String> {
    let prefix_trimmed = prefix.trim();
    if prefix_trimmed.is_empty()
        || !prefix_trimmed
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    {
        return Err("Önek yalnızca harf, rakam, '-' veya '_' içerebilir.".to_string());
    }

    if !wildcard_alias.starts_with("*+") && !wildcard_alias.starts_with("*.") {
        return Err(
            "Seçilen adres bir Wildcard alias ('*+' veya '*.' ile başlayan) değil.".to_string(),
        );
    }

    Ok(wildcard_alias.replacen('*', prefix_trimmed, 1))
}

/// URL sorgu parametresi kodlayıcı (harici crate gerektirmeden RFC 3986 yüzde kodlaması)
pub fn url_encode(input: &str) -> String {
    let mut encoded = String::with_capacity(input.len());
    for byte in input.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(byte as char);
            }
            _ => {
                encoded.push_str(&format!("%{:02X}", byte));
            }
        }
    }
    encoded
}

/// `GET /v1/api/aliases` için sorgu dizgesini oluşturur.
pub fn build_aliases_query_path(filter: &AliasFilter) -> String {
    let limit = filter.limit.clamp(1, 1000);
    let page = filter.page.max(1);
    let mut path = format!(
        "/v1/api/aliases?page={}&limit={}&sort_by={}&sort_order={}",
        page,
        limit,
        url_encode(&filter.sort_by),
        url_encode(&filter.sort_order)
    );
    if !filter.status.is_empty() {
        path.push_str(&format!("&status={}", url_encode(&filter.status)));
    }
    if !filter.wildcard.is_empty() {
        path.push_str(&format!("&wildcard={}", url_encode(&filter.wildcard)));
    }
    if !filter.search.trim().is_empty() {
        path.push_str(&format!("&search={}", url_encode(filter.search.trim())));
    }
    path
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_access_key_valid_and_invalid() {
        let uuid = "550e8400-e29b-41d4-a716-446655440000";
        let token = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
        let valid_key = format!("{uuid}.{token}");
        assert_eq!(valid_key.len(), 85);
        assert!(validate_access_key(&valid_key).is_ok());
        assert!(validate_access_key("short_key").is_err());
    }

    #[test]
    fn test_generate_reverse_alias_exact_mailx_formula() {
        let send_addr =
            generate_reverse_alias("autumn.wind42@mailx.net", "support@github.com").unwrap();
        assert_eq!(send_addr, "autumn.wind42+support=github.com@mailx.net");
    }

    #[test]
    fn test_generate_wildcard_on_the_fly() {
        let plus_alias = generate_wildcard_on_the_fly("*+rehber@mailx.net", "signal").unwrap();
        assert_eq!(plus_alias, "signal+rehber@mailx.net");

        let dot_alias = generate_wildcard_on_the_fly("*.rehber@mailx.net", "dentist").unwrap();
        assert_eq!(dot_alias, "dentist.rehber@mailx.net");
    }

    #[test]
    fn test_build_aliases_query_path() {
        let filter = AliasFilter {
            page: 1,
            limit: 50,
            sort_by: "created_at".to_string(),
            sort_order: "DESC".to_string(),
            status: "active".to_string(),
            wildcard: "false".to_string(),
            search: "github.com".to_string(),
        };
        let path = build_aliases_query_path(&filter);
        assert_eq!(
            path,
            "/v1/api/aliases?page=1&limit=50&sort_by=created_at&sort_order=DESC&status=active&wildcard=false&search=github.com"
        );
    }
}
