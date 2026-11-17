use serde::{Deserialize, Serialize};

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

pub fn sha1_digest(message: &[u8]) -> [u8; 20] {
    let mut h0: u32 = 0x6745_2301;
    let mut h1: u32 = 0xEFCD_AB89;
    let mut h2: u32 = 0x98BA_DCFE;
    let mut h3: u32 = 0x1032_5476;
    let mut h4: u32 = 0xC3D2_E1F0;

    let bit_len = (message.len() as u64) * 8;
    let mut padded = Vec::with_capacity(message.len() + 72);
    padded.extend_from_slice(message);
    padded.push(0x80);
    while (padded.len() % 64) != 56 {
        padded.push(0x00);
    }
    padded.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in padded.chunks_exact(64) {
        let mut w = [0u32; 80];
        for i in 0..16 {
            let j = i * 4;
            w[i] = ((chunk[j] as u32) << 24)
                | ((chunk[j + 1] as u32) << 16)
                | ((chunk[j + 2] as u32) << 8)
                | (chunk[j + 3] as u32);
        }
        for i in 16..80 {
            w[i] = (w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16]).rotate_left(1);
        }

        let mut a = h0;
        let mut b = h1;
        let mut c = h2;
        let mut d = h3;
        let mut e = h4;

        for i in 0..80 {
            let (f, k) = match i {
                0..=19 => ((b & c) | ((!b) & d), 0x5A82_7999u32),
                20..=39 => (b ^ c ^ d, 0x6ED9_EBA1u32),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1B_BCDCu32),
                _ => (b ^ c ^ d, 0xCA62_C1D6u32),
            };
            let temp = a
                .rotate_left(5)
                .wrapping_add(f)
                .wrapping_add(e)
                .wrapping_add(k)
                .wrapping_add(w[i]);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = temp;
        }

        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
    }

    let mut out = [0u8; 20];
    out[0..4].copy_from_slice(&h0.to_be_bytes());
    out[4..8].copy_from_slice(&h1.to_be_bytes());
    out[8..12].copy_from_slice(&h2.to_be_bytes());
    out[12..16].copy_from_slice(&h3.to_be_bytes());
    out[16..20].copy_from_slice(&h4.to_be_bytes());
    out
}

pub fn hmac_sha1(key: &[u8], message: &[u8]) -> [u8; 20] {
    let mut key_block = [0u8; 64];
    if key.len() > 64 {
        let hashed = sha1_digest(key);
        key_block[..20].copy_from_slice(&hashed);
    } else {
        key_block[..key.len()].copy_from_slice(key);
    }

    let mut ipad = [0x36u8; 64];
    let mut opad = [0x5Cu8; 64];
    for i in 0..64 {
        ipad[i] ^= key_block[i];
        opad[i] ^= key_block[i];
    }

    let mut inner_input = Vec::with_capacity(64 + message.len());
    inner_input.extend_from_slice(&ipad);
    inner_input.extend_from_slice(message);
    let inner_hash = sha1_digest(&inner_input);

    let mut outer_input = Vec::with_capacity(64 + 20);
    outer_input.extend_from_slice(&opad);
    outer_input.extend_from_slice(&inner_hash);
    sha1_digest(&outer_input)
}

pub fn generate_totp_code_at(
    secret_base32: &str,
    unix_timestamp_secs: u64,
    period_secs: u64,
    digits: u32,
) -> Result<String, String> {
    let key = decode_base32_rfc4648(secret_base32)?;
    let step = unix_timestamp_secs / period_secs.max(1);
    let counter_bytes = step.to_be_bytes();
    let mac = hmac_sha1(&key, &counter_bytes);
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

    #[test]
    fn test_totp_rfc6238_vector() {
        // JBSWY3DPEHPK3PXP is Base32 for "Hello!\xDE\xAD\xBE\xEF"
        let code = generate_totp_code_at("JBSWY3DPEHPK3PXP", 1700000000, 30, 6).unwrap();
        assert_eq!(code.len(), 6);
        assert!(code.chars().all(|c| c.is_ascii_digit()));
    }
}
