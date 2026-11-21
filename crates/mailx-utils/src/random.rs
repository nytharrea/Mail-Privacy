const HEX: &[u8; 16] = b"0123456789abcdef";

pub fn try_fill_random(buf: &mut [u8]) -> Result<(), String> {
    getrandom::fill(buf).map_err(|error| format!("OS random number generator failed: {error}"))
}

pub fn fill_random(buf: &mut [u8]) {
    if let Err(message) = try_fill_random(buf) {
        panic!("{message}");
    }
}

pub fn random_u64() -> u64 {
    let mut bytes = [0u8; 8];
    fill_random(&mut bytes);
    u64::from_le_bytes(bytes)
}

fn sample_below(bound: u64, mut next: impl FnMut() -> u64) -> u64 {
    if bound <= 1 {
        return 0;
    }
    let threshold = bound.wrapping_neg() % bound;
    loop {
        let candidate = next();
        if candidate >= threshold {
            return candidate % bound;
        }
    }
}

pub fn random_below(bound: u64) -> u64 {
    sample_below(bound, random_u64)
}

pub fn random_index(len: usize) -> usize {
    random_below(len as u64) as usize
}

pub fn random_hex(byte_len: usize) -> String {
    let mut bytes = vec![0u8; byte_len];
    fill_random(&mut bytes);
    let mut out = String::with_capacity(byte_len * 2);
    for byte in bytes {
        out.push(HEX[(byte >> 4) as usize] as char);
        out.push(HEX[(byte & 0x0F) as usize] as char);
    }
    out
}

pub fn random_uuid_v4() -> String {
    let mut bytes = [0u8; 16];
    fill_random(&mut bytes);
    bytes[6] = (bytes[6] & 0x0F) | 0x40;
    bytes[8] = (bytes[8] & 0x3F) | 0x80;
    let mut out = String::with_capacity(36);
    for (index, byte) in bytes.iter().enumerate() {
        if matches!(index, 4 | 6 | 8 | 10) {
            out.push('-');
        }
        out.push(HEX[(byte >> 4) as usize] as char);
        out.push(HEX[(byte & 0x0F) as usize] as char);
    }
    out
}

pub fn entropy_bits_floor(choice_counts: impl IntoIterator<Item = usize>) -> u32 {
    let total: f64 = choice_counts
        .into_iter()
        .filter(|count| *count > 1)
        .map(|count| (count as f64).log2())
        .sum();
    total.floor() as u32
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scripted(values: &[u64]) -> impl FnMut() -> u64 + '_ {
        let mut index = 0;
        move || {
            let value = values[index];
            index += 1;
            value
        }
    }

    #[test]
    fn sampler_rejects_values_that_would_bias_the_result() {
        assert_eq!(sample_below(10, scripted(&[0, 5, 6])), 6);
        assert_eq!(sample_below(10, scripted(&[3, 99])), 9);
        assert_eq!(sample_below(10, scripted(&[7])), 7);
        assert_eq!(sample_below(3, scripted(&[0, 1])), 1);
        assert_eq!(sample_below(3, scripted(&[u64::MAX])), u64::MAX % 3);
    }

    #[test]
    fn sampler_handles_degenerate_bounds() {
        assert_eq!(sample_below(0, scripted(&[])), 0);
        assert_eq!(sample_below(1, scripted(&[])), 0);
        assert_eq!(random_below(0), 0);
        assert_eq!(random_below(1), 0);
        assert_eq!(random_index(0), 0);
    }

    #[test]
    fn power_of_two_bound_never_rejects() {
        assert_eq!(sample_below(256, scripted(&[0])), 0);
        assert_eq!(sample_below(256, scripted(&[u64::MAX])), 255);
    }

    #[test]
    fn random_below_stays_in_range_and_covers_every_bucket() {
        let bound = 36u64;
        let draws = 36_000usize;
        let mut buckets = [0usize; 36];
        for _ in 0..draws {
            let value = random_below(bound);
            assert!(value < bound);
            buckets[value as usize] += 1;
        }
        let expected = draws / buckets.len();
        for count in buckets {
            assert!(
                count > expected / 2 && count < expected * 3 / 2,
                "bucket count {count} is far from {expected}"
            );
        }
    }

    #[test]
    fn random_hex_has_requested_length_and_alphabet() {
        let a = random_hex(16);
        let b = random_hex(16);
        assert_eq!(a.len(), 32);
        assert!(a
            .bytes()
            .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c)));
        assert_ne!(a, b);
        assert!(random_hex(0).is_empty());
    }

    #[test]
    fn uuid_v4_has_valid_layout() {
        for _ in 0..64 {
            let uuid = random_uuid_v4();
            assert_eq!(uuid.len(), 36);
            let bytes = uuid.as_bytes();
            assert_eq!(bytes[8], b'-');
            assert_eq!(bytes[13], b'-');
            assert_eq!(bytes[18], b'-');
            assert_eq!(bytes[23], b'-');
            assert_eq!(bytes[14], b'4');
            assert!(matches!(bytes[19], b'8' | b'9' | b'a' | b'b'));
        }
        assert_ne!(random_uuid_v4(), random_uuid_v4());
    }

    #[test]
    fn entropy_is_the_floor_of_the_summed_log2() {
        assert_eq!(entropy_bits_floor(std::iter::repeat_n(36usize, 10)), 51);
        assert_eq!(entropy_bits_floor(std::iter::repeat_n(36usize, 8)), 41);
        assert_eq!(entropy_bits_floor(std::iter::repeat_n(2usize, 8)), 8);
        assert_eq!(entropy_bits_floor([1usize, 1]), 0);
        assert_eq!(entropy_bits_floor(Vec::<usize>::new()), 0);
    }
}
