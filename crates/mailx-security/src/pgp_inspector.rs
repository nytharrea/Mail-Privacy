use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct OpenPgpPacketSummary {
    pub tag_id: u8,
    pub tag_name: String,
    pub body_length: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct OpenPgpKeyringReport {
    pub valid_armor: bool,
    pub crc24_verified: bool,
    pub expected_crc24: u32,
    pub computed_crc24: u32,
    pub primary_algorithm_id: u8,
    pub primary_algorithm_name: String,
    pub creation_timestamp_unix: u32,
    pub user_ids: Vec<String>,
    pub subkey_count: usize,
    pub signature_count: usize,
    pub packets: Vec<OpenPgpPacketSummary>,
}

const CRC24_INIT: u32 = 0x00B7_04CE;
const CRC24_POLY: u32 = 0x0186_4CFB;

pub fn compute_openpgp_crc24(data: &[u8]) -> u32 {
    let mut crc = CRC24_INIT;
    for &b in data {
        crc ^= (b as u32) << 16;
        for _ in 0..8 {
            crc <<= 1;
            if (crc & 0x0100_0000) != 0 {
                crc ^= CRC24_POLY;
            }
        }
    }
    crc & 0x00FF_FFFF
}

fn decode_standard_base64(input: &str) -> Result<Vec<u8>, String> {
    fn val(b: u8) -> Result<u8, String> {
        match b {
            b'A'..=b'Z' => Ok(b - b'A'),
            b'a'..=b'z' => Ok(b - b'a' + 26),
            b'0'..=b'9' => Ok(b - b'0' + 52),
            b'+' => Ok(62),
            b'/' => Ok(63),
            _ => Err("Invalid base64 character".to_string()),
        }
    }

    let clean: Vec<u8> = input
        .bytes()
        .filter(|b| !b.is_ascii_whitespace())
        .collect();
    if clean.is_empty() || clean.len() % 4 != 0 {
        return Err("Invalid base64 length".to_string());
    }

    let mut out = Vec::with_capacity((clean.len() / 4) * 3);
    for chunk in clean.chunks(4) {
        let v0 = val(chunk[0])?;
        let v1 = val(chunk[1])?;
        out.push((v0 << 2) | (v1 >> 4));
        if chunk[2] != b'=' {
            let v2 = val(chunk[2])?;
            out.push((v1 << 4) | (v2 >> 2));
            if chunk[3] != b'=' {
                let v3 = val(chunk[3])?;
                out.push((v2 << 6) | v3);
            }
        }
    }
    Ok(out)
}

pub fn pgp_packet_tag_name(tag: u8) -> &'static str {
    match tag {
        1 => "Public-Key Encrypted Session Key",
        2 => "Signature Packet",
        5 => "Secret-Key Packet",
        6 => "Public-Key Packet",
        7 => "Secret-Subkey Packet",
        8 => "Compressed Data Packet",
        11 => "Literal Data Packet",
        12 => "Trust Packet",
        13 => "User ID Packet",
        14 => "Public-Subkey Packet",
        17 => "User Attribute Packet",
        18 => "Sym. Encrypted and Integrity Protected Data",
        19 => "Modification Detection Code",
        _ => "OpenPGP Packet",
    }
}

pub fn pgp_public_key_algorithm_name(alg: u8) -> &'static str {
    match alg {
        1..=3 => "RSA (Encrypt or Sign)",
        16 => "Elgamal (Encrypt-Only)",
        17 => "DSA (Digital Signature Algorithm)",
        18 => "ECDH (Elliptic Curve Diffie-Hellman)",
        19 => "ECDSA (Elliptic Curve DSA)",
        22 => "EdDSA (Curve25519 / Ed25519 Legacy)",
        25 => "X25519 (RFC 9580 ECDH)",
        26 => "X448 (RFC 9580 ECDH)",
        27 => "Ed25519 (RFC 9580 EdDSA)",
        28 => "Ed448 (RFC 9580 EdDSA)",
        _ => "Unknown Public-Key Algorithm",
    }
}

pub fn parse_openpgp_armored_public_key(armored: &str) -> Result<OpenPgpKeyringReport, String> {
    let trimmed = armored.trim();
    let begin = "-----BEGIN PGP PUBLIC KEY BLOCK-----";
    let end = "-----END PGP PUBLIC KEY BLOCK-----";

    if !trimmed.starts_with(begin) || !trimmed.ends_with(end) {
        return Err("Missing BEGIN/END PGP PUBLIC KEY BLOCK armor boundaries.".to_string());
    }

    let inner = trimmed
        .strip_prefix(begin)
        .unwrap_or("")
        .strip_suffix(end)
        .unwrap_or("")
        .trim();

    let mut body_b64 = String::new();
    let mut crc_b64 = String::new();
    let mut past_headers = false;

    for line in inner.lines() {
        let l = line.trim();
        if l.is_empty() {
            past_headers = true;
            continue;
        }
        if !past_headers && l.contains(':') {
            continue;
        }
        past_headers = true;
        if let Some(crc_str) = l.strip_prefix('=') {
            if crc_str.len() == 4 {
                crc_b64 = crc_str.to_string();
                continue;
            }
        }
        body_b64.push_str(l);
    }

    let raw_bytes = decode_standard_base64(&body_b64)?;
    let computed_crc24 = compute_openpgp_crc24(&raw_bytes);
    let expected_crc24 = if crc_b64.len() == 4 {
        let crc_bytes = decode_standard_base64(&crc_b64).unwrap_or_default();
        if crc_bytes.len() == 3 {
            ((crc_bytes[0] as u32) << 16) | ((crc_bytes[1] as u32) << 8) | (crc_bytes[2] as u32)
        } else {
            computed_crc24
        }
    } else {
        computed_crc24
    };

    let mut packets = Vec::new();
    let mut primary_algorithm_id = 0u8;
    let mut creation_timestamp_unix = 0u32;
    let mut user_ids = Vec::new();
    let mut subkey_count = 0usize;
    let mut signature_count = 0usize;

    let mut offset = 0usize;
    while offset < raw_bytes.len() {
        let ctb = raw_bytes[offset];
        if (ctb & 0x80) == 0 {
            break;
        }
        offset += 1;

        let (tag, body_len, hdr_advance) = if (ctb & 0x40) != 0 {
            let tag = ctb & 0x3F;
            if offset >= raw_bytes.len() {
                break;
            }
            let first_len = raw_bytes[offset] as usize;
            if first_len < 192 {
                (tag, first_len, 1usize)
            } else if first_len < 224 && offset + 1 < raw_bytes.len() {
                let second = raw_bytes[offset + 1] as usize;
                let len = ((first_len - 192) << 8) + second + 192;
                (tag, len, 2usize)
            } else if first_len == 255 && offset + 4 < raw_bytes.len() {
                let len = ((raw_bytes[offset + 1] as usize) << 24)
                    | ((raw_bytes[offset + 2] as usize) << 16)
                    | ((raw_bytes[offset + 3] as usize) << 8)
                    | (raw_bytes[offset + 4] as usize);
                (tag, len, 5usize)
            } else {
                break;
            }
        } else {
            let tag = (ctb >> 2) & 0x0F;
            let len_type = ctb & 0x03;
            match len_type {
                0 if offset < raw_bytes.len() => (tag, raw_bytes[offset] as usize, 1usize),
                1 if offset + 1 < raw_bytes.len() => {
                    let len = ((raw_bytes[offset] as usize) << 8) | (raw_bytes[offset + 1] as usize);
                    (tag, len, 2usize)
                }
                2 if offset + 3 < raw_bytes.len() => {
                    let len = ((raw_bytes[offset] as usize) << 24)
                        | ((raw_bytes[offset + 1] as usize) << 16)
                        | ((raw_bytes[offset + 2] as usize) << 8)
                        | (raw_bytes[offset + 3] as usize);
                    (tag, len, 4usize)
                }
                _ => break,
            }
        };

        let Some(body_start) = offset.checked_add(hdr_advance) else {
            break;
        };
        let Some(body_end) = body_start.checked_add(body_len) else {
            break;
        };
        if body_end > raw_bytes.len() {
            break;
        }
        let body = &raw_bytes[body_start..body_end];
        offset = body_end;

        match tag {
            6 => {
                if body.len() >= 6 && (body[0] == 4 || body[0] == 6) {
                    creation_timestamp_unix = ((body[1] as u32) << 24)
                        | ((body[2] as u32) << 16)
                        | ((body[3] as u32) << 8)
                        | (body[4] as u32);
                    primary_algorithm_id = body[5];
                }
            }
            13 => {
                if let Ok(uid) = std::str::from_utf8(body) {
                    user_ids.push(uid.trim().to_string());
                }
            }
            14 => subkey_count += 1,
            2 => signature_count += 1,
            _ => {}
        }

        packets.push(OpenPgpPacketSummary {
            tag_id: tag,
            tag_name: pgp_packet_tag_name(tag).to_string(),
            body_length: body_len,
        });
    }

    Ok(OpenPgpKeyringReport {
        valid_armor: true,
        crc24_verified: expected_crc24 == computed_crc24,
        expected_crc24,
        computed_crc24,
        primary_algorithm_id,
        primary_algorithm_name: pgp_public_key_algorithm_name(primary_algorithm_id).to_string(),
        creation_timestamp_unix,
        user_ids,
        subkey_count,
        signature_count,
        packets,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_crc24_computation() {
        let crc = compute_openpgp_crc24(b"Mailx OpenPGP Test Payload");
        assert_ne!(crc, 0);
        assert!(crc <= 0x00FF_FFFF);
    }

    #[test]
    fn oversized_packet_length_is_rejected_without_panicking() {
        let armored = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nxv//////\n-----END PGP PUBLIC KEY BLOCK-----";
        let report = parse_openpgp_armored_public_key(armored).unwrap();
        assert!(report.packets.is_empty());
    }
}
