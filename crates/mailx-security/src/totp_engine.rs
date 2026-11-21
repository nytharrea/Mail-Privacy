use hmac::{Hmac, KeyInit, Mac};
use serde::{Deserialize, Serialize};
use sha1::Sha1;
use zeroize::Zeroizing;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct OtpAuthConfig {
    pub issuer: String,
    pub account_name: String,
    pub secret_base32: String,
    pub algorithm: String,
    pub digits: u32,
    pub period_secs: u64,
}

pub fn decode_base32_rfc4648(input: &str) -> Result<Vec<u8>, String> {
    let mut bits: u32 = 0;
    let mut bit_count: u32 = 0;
    let mut out = Vec::new();

    for ch in input.chars() {
        if ch.is_whitespace() || ch == '=' || ch == '-' {
            continue;
        }
        let val: u32 = match ch.to_ascii_uppercase() {
            'A'..='Z' => (ch.to_ascii_uppercase() as u32) - ('A' as u32),
            '2'..='7' => (ch as u32) - ('2' as u32) + 26,
            _ => return Err(format!("Invalid Base32 character: '{}'", ch)),
        };
        bits = (bits << 5) | val;
        bit_count += 5;
        if bit_count >= 8 {
            bit_count -= 8;
            out.push(((bits >> bit_count) & 0xFF) as u8);
        }
    }

    if out.is_empty() {
        return Err("Base32 secret decoded to empty byte array.".to_string());
    }
    Ok(out)
}

pub fn hmac_sha1(key: &[u8], message: &[u8]) -> [u8; 20] {
    let mut mac = Hmac::<Sha1>::new_from_slice(key).expect("HMAC accepts keys of any length");
    mac.update(message);
    let mut out = [0u8; 20];
    out.copy_from_slice(&mac.finalize().into_bytes());
    out
}

pub fn generate_totp_code_at(
    secret_base32: &str,
    unix_timestamp_secs: u64,
    period_secs: u64,
    digits: u32,
) -> Result<String, String> {
    let key = Zeroizing::new(decode_base32_rfc4648(secret_base32)?);
    let step = unix_timestamp_secs / period_secs.max(1);
    let counter_bytes = step.to_be_bytes();
    let mac = hmac_sha1(key.as_slice(), &counter_bytes);
    let offset = (mac[19] & 0x0F) as usize;
    let binary = (((mac[offset] & 0x7F) as u32) << 24)
        | ((mac[offset + 1] as u32) << 16)
        | ((mac[offset + 2] as u32) << 8)
        | (mac[offset + 3] as u32);
    let clean_digits = digits.clamp(6, 8);
    let modulo = 10u32.pow(clean_digits);
    let otp = binary % modulo;
    Ok(format!("{:0width$}", otp, width = clean_digits as usize))
}

#[cfg(test)]
mod tests {
    use super::*;

    const RFC_SECRET_BASE32: &str = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    fn hex(bytes: &[u8]) -> String {
        bytes.iter().map(|b| format!("{b:02x}")).collect()
    }

    #[test]
    fn rfc6238_sha1_reference_vectors() {
        let vectors = [
            (59u64, "94287082"),
            (1_111_111_109, "07081804"),
            (1_111_111_111, "14050471"),
            (1_234_567_890, "89005924"),
            (2_000_000_000, "69279037"),
            (20_000_000_000, "65353130"),
        ];
        for (timestamp, expected) in vectors {
            let code = generate_totp_code_at(RFC_SECRET_BASE32, timestamp, 30, 8).unwrap();
            assert_eq!(code, expected, "timestamp {timestamp}");
        }
    }

    #[test]
    fn six_digit_codes_are_the_low_digits_of_the_eight_digit_vector() {
        let code = generate_totp_code_at(RFC_SECRET_BASE32, 59, 30, 6).unwrap();
        assert_eq!(code, "287082");
    }

    #[test]
    fn hmac_sha1_matches_rfc2202_vectors() {
        assert_eq!(
            hex(&hmac_sha1(&[0x0b; 20], b"Hi There")),
            "b617318655057264e28bc0b6fb378c8ef146be00"
        );
        assert_eq!(
            hex(&hmac_sha1(b"Jefe", b"what do ya want for nothing?")),
            "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79"
        );
        assert_eq!(
            hex(&hmac_sha1(
                &[0xaa; 80],
                b"Test Using Larger Than Block-Size Key - Hash Key First"
            )),
            "aa4ae5e15272d00e95705637ce8a3b55ed402112"
        );
    }

    #[test]
    fn code_is_constant_inside_a_period_and_changes_at_the_boundary() {
        let secret = "JBSWY3DPEHPK3PXP";
        let first = generate_totp_code_at(secret, 1_699_999_980, 30, 6).unwrap();
        let middle = generate_totp_code_at(secret, 1_700_000_000, 30, 6).unwrap();
        let last = generate_totp_code_at(secret, 1_700_000_009, 30, 6).unwrap();
        let next = generate_totp_code_at(secret, 1_700_000_010, 30, 6).unwrap();
        assert_eq!(first, "324550");
        assert_eq!(first, middle);
        assert_eq!(middle, last);
        assert_eq!(next, "367665");
    }

    #[test]
    fn digits_are_clamped_and_zero_period_is_treated_as_one_second() {
        assert_eq!(
            generate_totp_code_at(RFC_SECRET_BASE32, 59, 30, 3).unwrap(),
            "287082"
        );
        assert_eq!(
            generate_totp_code_at(RFC_SECRET_BASE32, 59, 30, 12).unwrap(),
            "94287082"
        );
        assert_eq!(
            generate_totp_code_at(RFC_SECRET_BASE32, 59, 0, 6).unwrap(),
            "083773"
        );
    }

    #[test]
    fn base32_decoder_accepts_common_authenticator_formatting() {
        let canonical = decode_base32_rfc4648(RFC_SECRET_BASE32).unwrap();
        assert_eq!(canonical, b"12345678901234567890");
        let formatted = "gezd gnbv-gy3t qojq gezd gnbv gy3t qojq====";
        assert_eq!(decode_base32_rfc4648(formatted).unwrap(), canonical);
        assert_eq!(
            generate_totp_code_at(formatted, 1_111_111_109, 30, 8).unwrap(),
            "07081804"
        );
    }

    #[test]
    fn base32_decoder_rejects_invalid_input() {
        assert!(decode_base32_rfc4648("").is_err());
        assert!(decode_base32_rfc4648("====").is_err());
        assert!(decode_base32_rfc4648("ABC1").is_err());
        assert!(decode_base32_rfc4648("ABC!").is_err());
        assert!(generate_totp_code_at("not base32 !", 0, 30, 6).is_err());
    }
}
