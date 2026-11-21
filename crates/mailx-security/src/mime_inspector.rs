use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct AttachmentRiskEntry {
    pub extension: &'static str,
    pub mime_type: &'static str,
    pub risk_level: &'static str,
    pub threat_category: &'static str,
    pub description: &'static str,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MimeStructureReport {
    pub content_type: String,
    pub boundary: Option<String>,
    pub is_pgp_mime_encrypted: bool,
    pub is_pgp_mime_signed: bool,
    pub has_inline_pgp_block: bool,
    pub detected_part_count: usize,
    pub attachment_filenames: Vec<String>,
    pub high_risk_attachments: Vec<String>,
}

pub const DANGEROUS_ATTACHMENT_CATALOG: &[AttachmentRiskEntry] = &[
    AttachmentRiskEntry {
        extension: "exe",
        mime_type: "application/x-msdownload",
        risk_level: "Critical",
        threat_category: "Windows Portable Executable",
        description: "Direct native binary execution on Windows endpoints.",
    },
    AttachmentRiskEntry {
        extension: "scr",
        mime_type: "application/x-msdownload",
        risk_level: "Critical",
        threat_category: "Windows Screensaver Trojan",
        description: "Executable binary masquerading as a screensaver file.",
    },
    AttachmentRiskEntry {
        extension: "pif",
        mime_type: "application/x-msdos-program",
        risk_level: "Critical",
        threat_category: "Program Information File",
        description: "Legacy DOS shortcut capable of executing arbitrary shell commands.",
    },
    AttachmentRiskEntry {
        extension: "bat",
        mime_type: "application/x-bat",
        risk_level: "Critical",
        threat_category: "Batch Command Script",
        description: "Executes Windows command interpreter instructions.",
    },
    AttachmentRiskEntry {
        extension: "cmd",
        mime_type: "application/cmd",
        risk_level: "Critical",
        threat_category: "Windows NT Command Script",
        description: "Runs native cmd.exe shell payloads.",
    },
    AttachmentRiskEntry {
        extension: "ps1",
        mime_type: "application/x-powershell",
        risk_level: "Critical",
        threat_category: "PowerShell Script",
        description: "Executes privileged .NET and PowerShell automation commands.",
    },
    AttachmentRiskEntry {
        extension: "vbs",
        mime_type: "text/vbscript",
        risk_level: "Critical",
        threat_category: "VBScript Dropper",
        description: "Windows Script Host payload commonly used by malware droppers.",
    },
    AttachmentRiskEntry {
        extension: "vbe",
        mime_type: "application/x-vbs",
        risk_level: "Critical",
        threat_category: "Encoded VBScript",
        description: "Obfuscated Windows Script Host payload.",
    },
    AttachmentRiskEntry {
        extension: "js",
        mime_type: "application/javascript",
        risk_level: "Critical",
        threat_category: "Standalone JScript Dropper",
        description: "Executes via Windows Script Host when opened outside a browser.",
    },
    AttachmentRiskEntry {
        extension: "jse",
        mime_type: "application/x-jse",
        risk_level: "Critical",
        threat_category: "Encoded JScript Payload",
        description: "Obfuscated JScript execution payload.",
    },
    AttachmentRiskEntry {
        extension: "wsf",
        mime_type: "text/xml",
        risk_level: "Critical",
        threat_category: "Windows Script File",
        description: "Multi-engine script container for JScript and VBScript.",
    },
    AttachmentRiskEntry {
        extension: "wsh",
        mime_type: "text/plain",
        risk_level: "Critical",
        threat_category: "Windows Script Host Control",
        description: "Configures and launches WSH script execution.",
    },
    AttachmentRiskEntry {
        extension: "hta",
        mime_type: "application/hta",
        risk_level: "Critical",
        threat_category: "HTML Application",
        description: "Executes HTML/JS with full local user privileges via mshta.exe.",
    },
    AttachmentRiskEntry {
        extension: "cpl",
        mime_type: "application/x-cpl",
        risk_level: "Critical",
        threat_category: "Control Panel Applet",
        description: "DLL exported applet executed by control.exe.",
    },
    AttachmentRiskEntry {
        extension: "msi",
        mime_type: "application/x-msi",
        risk_level: "Critical",
        threat_category: "Windows Installer Package",
        description: "Installs software and executes custom actions via msiexec.",
    },
    AttachmentRiskEntry {
        extension: "msp",
        mime_type: "application/Octet-stream",
        risk_level: "Critical",
        threat_category: "Windows Installer Patch",
        description: "Applies binary patches and custom installer scripts.",
    },
    AttachmentRiskEntry {
        extension: "com",
        mime_type: "application/x-msdos-program",
        risk_level: "Critical",
        threat_category: "DOS Command Binary",
        description: "Direct executable image.",
    },
    AttachmentRiskEntry {
        extension: "lnk",
        mime_type: "application/x-ms-shortcut",
        risk_level: "Critical",
        threat_category: "Windows Shell Shortcut",
        description: "Shortcut file frequently weaponized to invoke PowerShell or cmd.exe.",
    },
    AttachmentRiskEntry {
        extension: "iso",
        mime_type: "application/x-iso9660-image",
        risk_level: "High",
        threat_category: "Optical Disk Image Container",
        description: "Mounts as a virtual drive to bypass Mark-of-the-Web (MotW) checks.",
    },
    AttachmentRiskEntry {
        extension: "img",
        mime_type: "application/octet-stream",
        risk_level: "High",
        threat_category: "Raw Disk Image Container",
        description: "Bypasses browser download quarantine flags when mounted.",
    },
    AttachmentRiskEntry {
        extension: "vhd",
        mime_type: "application/x-vhd",
        risk_level: "High",
        threat_category: "Virtual Hard Disk",
        description: "Mounts a virtual file system containing hidden executables.",
    },
    AttachmentRiskEntry {
        extension: "vhdx",
        mime_type: "application/x-vhdx",
        risk_level: "High",
        threat_category: "Hyper-V Virtual Disk",
        description: "Mounts a virtual partition to evade attachment scanners.",
    },
    AttachmentRiskEntry {
        extension: "docm",
        mime_type: "application/vnd.ms-word.document.macroEnabled.12",
        risk_level: "High",
        threat_category: "Macro-Enabled Word Document",
        description: "Contains VBA macros capable of executing native code.",
    },
    AttachmentRiskEntry {
        extension: "xlsm",
        mime_type: "application/vnd.ms-excel.sheet.macroEnabled.12",
        risk_level: "High",
        threat_category: "Macro-Enabled Excel Workbook",
        description: "Contains VBA or Excel 4.0 XLM macros.",
    },
    AttachmentRiskEntry {
        extension: "pptm",
        mime_type: "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
        risk_level: "High",
        threat_category: "Macro-Enabled PowerPoint",
        description: "Executes embedded VBA macro payloads on presentation actions.",
    },
    AttachmentRiskEntry {
        extension: "xlam",
        mime_type: "application/vnd.ms-excel.addin.macroEnabled.12",
        risk_level: "High",
        threat_category: "Excel Macro Add-In",
        description: "Persistent Excel add-in containing executable VBA modules.",
    },
    AttachmentRiskEntry {
        extension: "xll",
        mime_type: "application/octet-stream",
        risk_level: "Critical",
        threat_category: "Excel Dynamic Link Library",
        description: "Native C/C++ DLL loaded directly into the Excel process.",
    },
    AttachmentRiskEntry {
        extension: "one",
        mime_type: "application/msonenote",
        risk_level: "High",
        threat_category: "OneNote Attachment Container",
        description: "Used to embed clickable script files without Mark-of-the-Web warnings.",
    },
    AttachmentRiskEntry {
        extension: "jar",
        mime_type: "application/java-archive",
        risk_level: "High",
        threat_category: "Java Archive Executable",
        description: "Executes cross-platform JVM bytecode.",
    },
    AttachmentRiskEntry {
        extension: "apk",
        mime_type: "application/vnd.android.package-archive",
        risk_level: "High",
        threat_category: "Android Application Package",
        description: "Sideloaded Android application binary.",
    },
    AttachmentRiskEntry {
        extension: "dex",
        mime_type: "application/octet-stream",
        risk_level: "High",
        threat_category: "Dalvik Executable Bytecode",
        description: "Executable Android runtime bytecode.",
    },
    AttachmentRiskEntry {
        extension: "sh",
        mime_type: "application/x-sh",
        risk_level: "High",
        threat_category: "POSIX Shell Script",
        description: "Executes shell commands on Linux and macOS systems.",
    },
    AttachmentRiskEntry {
        extension: "command",
        mime_type: "text/x-script.sh",
        risk_level: "High",
        threat_category: "macOS Terminal Script",
        description: "Launches Terminal.app on macOS when double-clicked.",
    },
    AttachmentRiskEntry {
        extension: "dmg",
        mime_type: "application/x-apple-diskimage",
        risk_level: "High",
        threat_category: "macOS Disk Image",
        description: "Mounts an Apple disk volume containing application bundles.",
    },
    AttachmentRiskEntry {
        extension: "pkg",
        mime_type: "application/x-newton-compatible-pkg",
        risk_level: "High",
        threat_category: "macOS Installer Package",
        description: "Runs preinstall and postinstall root/user scripts on macOS.",
    },
    AttachmentRiskEntry {
        extension: "svg",
        mime_type: "image/svg+xml",
        risk_level: "Medium",
        threat_category: "Scriptable Vector Image",
        description: "Can embed inline JavaScript and phishing redirects inside <script> tags.",
    },
    AttachmentRiskEntry {
        extension: "html",
        mime_type: "text/html",
        risk_level: "Medium",
        threat_category: "HTML Credential Harvester",
        description: "Frequently used as an offline credential phishing form attachment.",
    },
    AttachmentRiskEntry {
        extension: "htm",
        mime_type: "text/html",
        risk_level: "Medium",
        threat_category: "HTML Credential Harvester",
        description: "Local HTML attachment often used to mimic login portals.",
    },
    AttachmentRiskEntry {
        extension: "shtml",
        mime_type: "text/html",
        risk_level: "Medium",
        threat_category: "Server-Parsed HTML Attachment",
        description: "HTML attachment used to bypass basic extension filters.",
    },
    AttachmentRiskEntry {
        extension: "chm",
        mime_type: "application/vnd.ms-htmlhelp",
        risk_level: "Critical",
        threat_category: "Compiled HTML Help",
        description: "Executes ActiveX and command payloads via hh.exe.",
    },
];

pub fn inspect_attachment_filename(filename: &str) -> Option<&'static AttachmentRiskEntry> {
    let clean = filename.trim().trim_matches('"').trim_matches('\'');
    let ext = clean
        .rsplit_once('.')
        .map(|(_, e)| e.to_ascii_lowercase())?;
    DANGEROUS_ATTACHMENT_CATALOG
        .iter()
        .find(|entry| entry.extension == ext)
}

pub fn inspect_mime_message(raw_mime: &str) -> MimeStructureReport {
    let lower = raw_mime.to_ascii_lowercase();
    let is_pgp_mime_encrypted =
        lower.contains("multipart/encrypted") && lower.contains("application/pgp-encrypted");
    let is_pgp_mime_signed =
        lower.contains("multipart/signed") && lower.contains("application/pgp-signature");
    let has_inline_pgp_block = raw_mime.contains("-----BEGIN PGP MESSAGE-----")
        && raw_mime.contains("-----END PGP MESSAGE-----");

    let mut content_type = String::from("text/plain");
    let mut boundary: Option<String> = None;
    let mut attachment_filenames: Vec<String> = Vec::new();
    let mut high_risk_attachments: Vec<String> = Vec::new();

    for line in raw_mime.lines() {
        let trimmed = line.trim();
        let l_lower = trimmed.to_ascii_lowercase();
        if l_lower.starts_with("content-type:") {
            let val = trimmed[13..].trim();
            content_type = val.split(';').next().unwrap_or(val).trim().to_string();
        }
        if let Some(b_idx) = l_lower.find("boundary=") {
            let after = &trimmed[b_idx + 9..];
            let b_val = after
                .split(';')
                .next()
                .unwrap_or(after)
                .trim()
                .trim_matches('"')
                .trim_matches('\'');
            if !b_val.is_empty() {
                boundary = Some(b_val.to_string());
            }
        }
        if let Some(f_idx) = l_lower.find("filename=") {
            let after = &trimmed[f_idx + 9..];
            let fname = after
                .split(';')
                .next()
                .unwrap_or(after)
                .trim()
                .trim_matches('"')
                .trim_matches('\'')
                .to_string();
            if !fname.is_empty() && !attachment_filenames.contains(&fname) {
                if inspect_attachment_filename(&fname).is_some() {
                    high_risk_attachments.push(fname.clone());
                }
                attachment_filenames.push(fname);
            }
        }
    }

    let detected_part_count = if let Some(ref b) = boundary {
        let delim = format!("--{}", b);
        raw_mime.matches(&delim).count().saturating_sub(1)
    } else {
        1
    };

    MimeStructureReport {
        content_type,
        boundary,
        is_pgp_mime_encrypted,
        is_pgp_mime_signed,
        has_inline_pgp_block,
        detected_part_count,
        attachment_filenames,
        high_risk_attachments,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_inspect_pgp_mime_and_attachments() {
        let sample = r#"Content-Type: multipart/encrypted; protocol="application/pgp-encrypted"; boundary="pgp_bnd"
--pgp_bnd
Content-Disposition: attachment; filename="invoice.scr"
--pgp_bnd--
"#;
        let rep = inspect_mime_message(sample);
        assert!(rep.is_pgp_mime_encrypted);
        assert_eq!(rep.high_risk_attachments, vec!["invoice.scr".to_string()]);
    }
}
