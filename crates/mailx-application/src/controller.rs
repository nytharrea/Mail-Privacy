#[cfg(feature = "network")]
use mailx_domain::csv_engine::parse_aliases_csv_import;
#[cfg(feature = "network")]
use mailx_domain::models::CustomDomain;
use mailx_domain::telemetry::{civil_from_days, current_utc_epoch_days};
#[cfg(feature = "network")]
use mailx_network::client::{MailxClient, MailxError};
use mailx_utils::encode_path_segment;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ControllerActionResult {
    pub action: String,
    pub status_message: String,
    pub payload: Value,
}

pub fn build_expiry_iso_from_days(days: i32) -> Option<String> {
    if days <= 0 {
        return None;
    }
    let target_days = current_utc_epoch_days() + (days as i64);
    let (y, m, d) = civil_from_days(target_days);
    Some(format!("{:04}-{:02}-{:02}T23:59:59Z", y, m, d))
}

pub fn build_action_endpoint(
    action: &str,
    params: &Value,
) -> Result<(String, String, Option<String>), String> {
    let str_param = |key: &str| -> String {
        params
            .get(key)
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .trim()
            .to_string()
    };
    let bool_param = |key: &str, default: bool| -> bool {
        params.get(key).and_then(|v| v.as_bool()).unwrap_or(default)
    };
    let int_param = |key: &str, default: i64| -> i64 {
        params.get(key).and_then(|v| v.as_i64()).unwrap_or(default)
    };

    match action {
        "set_default_domain" => {
            let domain = str_param("domain");
            let body = json!({ "domain": domain }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/domain".to_string(),
                Some(body),
            ))
        }
        "set_default_recipient" => {
            let recipient = str_param("recipient");
            let body = json!({ "recipient": recipient }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/recipient".to_string(),
                Some(body),
            ))
        }
        "set_alias_format" => {
            let alias_format = str_param("alias_format");
            let body = json!({ "alias_format": alias_format }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/alias-format".to_string(),
                Some(body),
            ))
        }
        "set_from_name" => {
            let from_name = str_param("from_name");
            let body = json!({ "from_name": from_name }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/from-name".to_string(),
                Some(body),
            ))
        }
        "set_remove_header" => {
            let remove_header = bool_param("remove_header", false);
            let body = json!({ "remove_header": remove_header }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/remove-header".to_string(),
                Some(body),
            ))
        }
        "set_block_trackers" => {
            let block_trackers = bool_param("block_trackers", true);
            let body = json!({ "block_trackers": block_trackers }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/block-trackers".to_string(),
                Some(body),
            ))
        }
        "set_unsubscribe_behaviour" => {
            let behaviour = int_param("list_unsubscribe_behaviour", 0);
            let body = json!({ "list_unsubscribe_behaviour": behaviour }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/list-unsubscribe-behaviour".to_string(),
                Some(body),
            ))
        }
        "set_log_issues" => {
            let log_issues = bool_param("log_issues", true);
            let body = json!({ "log_issues": log_issues }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/settings/log-issues".to_string(),
                Some(body),
            ))
        }
        "pause_alias" => {
            let alias_id = str_param("alias_id");
            let value = int_param("value", 1).clamp(1, 10);
            let unit = str_param("unit");
            let body = json!({ "value": value, "unit": unit }).to_string();
            Ok((
                "PUT".to_string(),
                format!("/v1/alias/{}/pause", encode_path_segment(&alias_id)),
                Some(body),
            ))
        }
        "unpause_alias" => {
            let alias_id = str_param("alias_id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/alias/{}/pause", encode_path_segment(&alias_id)),
                None,
            ))
        }
        "restore_alias" => {
            let alias_id = str_param("alias_id");
            Ok((
                "POST".to_string(),
                format!("/v1/alias/restore/{}", encode_path_segment(&alias_id)),
                None,
            ))
        }
        "forget_alias" => {
            let alias_id = str_param("alias_id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/alias/forget/{}", encode_path_segment(&alias_id)),
                None,
            ))
        }
        "bulk_enable" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let enabled = bool_param("enabled", true);
            let body = json!({ "ids": ids, "enabled": enabled }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/aliases/bulk/enable".to_string(),
                Some(body),
            ))
        }
        "bulk_pin" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let pinned = bool_param("pinned", true);
            let body = json!({ "ids": ids, "pinned": pinned }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/aliases/bulk/pin".to_string(),
                Some(body),
            ))
        }
        "bulk_delete" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let body = json!({ "ids": ids }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/aliases/bulk/delete".to_string(),
                Some(body),
            ))
        }
        "bulk_restore" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let body = json!({ "ids": ids }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/aliases/bulk/restore".to_string(),
                Some(body),
            ))
        }
        "bulk_forget" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let body = json!({ "ids": ids }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/aliases/bulk/forget".to_string(),
                Some(body),
            ))
        }
        "add_recipient" => {
            let email = str_param("email");
            let body = json!({ "email": email }).to_string();
            Ok(("POST".to_string(), "/v1/recipient".to_string(), Some(body)))
        }
        "send_recipient_otp" => {
            let recipient_id = str_param("recipient_id");
            Ok((
                "POST".to_string(),
                format!(
                    "/v1/recipient/sendotp/{}",
                    encode_path_segment(&recipient_id)
                ),
                None,
            ))
        }
        "verify_recipient_otp" => {
            let recipient_id = str_param("recipient_id");
            let otp = str_param("otp");
            let body = json!({ "otp": otp }).to_string();
            Ok((
                "POST".to_string(),
                format!(
                    "/v1/recipient/activate/{}",
                    encode_path_segment(&recipient_id)
                ),
                Some(body),
            ))
        }
        "update_recipient_pgp" => {
            let id = str_param("id");
            let pgp_key = str_param("pgp_key");
            let pgp_enabled = bool_param("pgp_enabled", false);
            let pgp_inline = bool_param("pgp_inline", false);
            let body = json!({
                "id": id,
                "pgp_key": pgp_key,
                "pgp_enabled": pgp_enabled,
                "pgp_inline": pgp_inline
            })
            .to_string();
            Ok(("PUT".to_string(), "/v1/recipient".to_string(), Some(body)))
        }
        "delete_recipient" => {
            let recipient_id = str_param("recipient_id");
            let recipients = str_param("recipients");
            let body = json!({ "recipients": recipients }).to_string();
            Ok((
                "PUT".to_string(),
                format!(
                    "/v1/recipient/delete/{}",
                    encode_path_segment(&recipient_id)
                ),
                Some(body),
            ))
        }
        "add_custom_domain" => {
            let name = str_param("name").to_ascii_lowercase();
            let body = json!({ "name": name }).to_string();
            Ok(("POST".to_string(), "/v1/domain".to_string(), Some(body)))
        }
        "verify_domain_dns" => {
            let domain_id = str_param("domain_id");
            Ok((
                "POST".to_string(),
                format!("/v1/domain/{}/verify-dns", encode_path_segment(&domain_id)),
                None,
            ))
        }
        "update_custom_domain" => {
            let id = str_param("id");
            let body = json!({
                "id": id,
                "description": str_param("description"),
                "recipient": str_param("recipient"),
                "from_name": str_param("from_name"),
                "enabled": bool_param("enabled", true),
                "catch_all": bool_param("catch_all", false),
                "create_alias": bool_param("create_alias", false)
            })
            .to_string();
            Ok((
                "PUT".to_string(),
                format!("/v1/domain/{}", encode_path_segment(&id)),
                Some(body),
            ))
        }
        "delete_custom_domain" => {
            let domain_id = str_param("domain_id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/domain/{}", encode_path_segment(&domain_id)),
                None,
            ))
        }
        "add_blocklist_item" => {
            let item_type = str_param("type");
            let value = str_param("value");
            let body = json!({ "type": item_type, "value": value }).to_string();
            Ok(("POST".to_string(), "/v1/blocklist".to_string(), Some(body)))
        }
        "delete_blocklist_item" => {
            let id = str_param("id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/blocklist/{}", encode_path_segment(&id)),
                None,
            ))
        }
        "toggle_rule_active" => {
            let rule_id = str_param("rule_id");
            let active = bool_param("active", true);
            let body = json!({ "active": active }).to_string();
            Ok((
                "PUT".to_string(),
                format!("/v1/rule/{}/active", encode_path_segment(&rule_id)),
                Some(body),
            ))
        }
        "reorder_rules" => {
            let ids = params.get("ids").cloned().unwrap_or_else(|| json!([]));
            let body = json!({ "ids": ids }).to_string();
            Ok(("PUT".to_string(), "/v1/rules/order".to_string(), Some(body)))
        }
        "delete_rule" => {
            let rule_id = str_param("rule_id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/rule/{}", encode_path_segment(&rule_id)),
                None,
            ))
        }
        "send_account_otp" => Ok(("POST".to_string(), "/v1/user/sendotp".to_string(), None)),
        "verify_account_otp" => {
            let otp = str_param("otp");
            let body = json!({ "otp": otp }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/user/activate".to_string(),
                Some(body),
            ))
        }
        "delete_passkey" => {
            let credential_id = str_param("credential_id");
            Ok((
                "DELETE".to_string(),
                format!(
                    "/v1/user/credential/{}",
                    encode_path_segment(&credential_id)
                ),
                None,
            ))
        }
        "change_password" => {
            let old_password = params
                .get("old_password")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let password = params
                .get("password")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let body = json!({
                "old_password": old_password,
                "password": password
            })
            .to_string();
            Ok((
                "PUT".to_string(),
                "/v1/user/changepassword".to_string(),
                Some(body),
            ))
        }
        "initiate_totp" => Ok(("PUT".to_string(), "/v1/user/totp/enable".to_string(), None)),
        "confirm_totp" => {
            let otp = str_param("otp");
            let body = json!({ "otp": otp }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/user/totp/enable/confirm".to_string(),
                Some(body),
            ))
        }
        "disable_totp" => {
            let otp = str_param("otp");
            let body = json!({ "otp": otp }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/user/totp/disable".to_string(),
                Some(body),
            ))
        }
        "create_access_key" => {
            let raw_name = str_param("name");
            let name = if raw_name.is_empty() {
                "Mailx Android".to_string()
            } else {
                raw_name
            };
            let days = int_param("expires_days", 0) as i32;
            let mut obj = serde_json::Map::new();
            obj.insert("name".to_string(), Value::String(name));
            if let Some(exp_iso) = build_expiry_iso_from_days(days) {
                obj.insert("expires_at".to_string(), Value::String(exp_iso));
            }
            let body = Value::Object(obj).to_string();
            Ok(("POST".to_string(), "/v1/accesskeys".to_string(), Some(body)))
        }
        "delete_access_key" => {
            let id = str_param("id");
            Ok((
                "DELETE".to_string(),
                format!("/v1/accesskeys/{}", encode_path_segment(&id)),
                None,
            ))
        }
        "confirm_delete_account" => {
            let otp = str_param("otp");
            let body = json!({ "otp": otp }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/user/delete".to_string(),
                Some(body),
            ))
        }
        "delete_all_logs" => Ok(("DELETE".to_string(), "/v1/logs".to_string(), None)),
        "initiate_password_reset" => {
            let email = str_param("email");
            let body = json!({ "email": email }).to_string();
            Ok((
                "POST".to_string(),
                "/v1/initiatepasswordreset".to_string(),
                Some(body),
            ))
        }
        "confirm_password_reset" => {
            let otp = str_param("otp");
            let password = params
                .get("password")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let body = json!({ "otp": otp, "password": password }).to_string();
            Ok((
                "PUT".to_string(),
                "/v1/resetpassword".to_string(),
                Some(body),
            ))
        }
        _ => Err(format!("Unsupported controller action: {}", action)),
    }
}

#[cfg(feature = "network")]
pub fn execute_controller_action(
    client: &MailxClient,
    action: &str,
    params_json: &str,
) -> Result<Value, MailxError> {
    let params: Value = if params_json.trim().is_empty() {
        json!({})
    } else {
        serde_json::from_str(params_json)
            .map_err(|e| MailxError::SerializationError(e.to_string()))?
    };

    match action {
        "change_email_with_stepup" => {
            let current_password = params
                .get("current_password")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            if !current_password.is_empty() {
                let step_body = json!({ "password": current_password }).to_string();
                let _ =
                    client.execute_raw_json("POST", "/v1/user/stepup/password", Some(&step_body));
            }
            let email = params
                .get("email")
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .trim();
            let body = json!({ "email": email }).to_string();
            client.execute_raw_json("PUT", "/v1/user/changeemail", Some(&body))
        }
        "request_delete_account_with_stepup" => {
            let current_password = params
                .get("current_password")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            if !current_password.is_empty() {
                let step_body = json!({ "password": current_password }).to_string();
                let _ =
                    client.execute_raw_json("POST", "/v1/user/stepup/password", Some(&step_body));
            }
            client.execute_raw_json("POST", "/v1/user/delete/request", None)
        }
        "import_aliases_csv" => {
            let csv_text = params
                .get("csv_text")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let default_recipient = params
                .get("default_recipient")
                .and_then(|v| v.as_str())
                .unwrap_or_default();
            let custom_domains: Vec<CustomDomain> = params
                .get("custom_domains")
                .cloned()
                .and_then(|v| serde_json::from_value(v).ok())
                .unwrap_or_default();

            let plan = parse_aliases_csv_import(csv_text, default_recipient, &custom_domains, 500);
            let mut imported_count = 0usize;
            let mut skipped_count = plan.skipped_count;

            for req in &plan.requests {
                match client.create_alias(req) {
                    Ok(_) => imported_count += 1,
                    Err(_) => skipped_count += 1,
                }
            }

            Ok(json!({
                "imported_count": imported_count,
                "skipped_count": skipped_count
            }))
        }
        _ => {
            let (method, path, body_opt) =
                build_action_endpoint(action, &params).map_err(MailxError::SerializationError)?;
            client.execute_raw_json(&method, &path, body_opt.as_deref())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_action_endpoint_settings_and_bulk() {
        let params = json!({ "domain": "mailx.net" });
        let (method, path, body) = build_action_endpoint("set_default_domain", &params).unwrap();
        assert_eq!(method, "PUT");
        assert_eq!(path, "/v1/settings/domain");
        assert_eq!(body.unwrap(), r#"{"domain":"mailx.net"}"#);

        let bulk_params = json!({ "ids": ["a1", "a2"], "pinned": true });
        let (m2, p2, b2) = build_action_endpoint("bulk_pin", &bulk_params).unwrap();
        assert_eq!(m2, "POST");
        assert_eq!(p2, "/v1/aliases/bulk/pin");
        assert!(b2.unwrap().contains("pinned"));
    }

    #[test]
    fn test_ids_are_encoded_into_one_path_segment() {
        let (_, path, _) =
            build_action_endpoint("delete_access_key", &json!({ "id": "../x" })).unwrap();
        assert_eq!(path, "/v1/accesskeys/..%2Fx");

        let (_, path, _) = build_action_endpoint(
            "pause_alias",
            &json!({ "alias_id": "a?b#c", "value": 1, "unit": "hours" }),
        )
        .unwrap();
        assert_eq!(path, "/v1/alias/a%3Fb%23c/pause");

        let (_, path, _) =
            build_action_endpoint("delete_blocklist_item", &json!({ "id": "plain-id-42" }))
                .unwrap();
        assert_eq!(path, "/v1/blocklist/plain-id-42");
    }

    #[test]
    fn test_build_action_endpoint_domains_recipients_and_rules() {
        let (m, path, body) = build_action_endpoint(
            "add_custom_domain",
            &json!({ "name": "  EXAMPLE.ORG  " }),
        )
        .unwrap();
        assert_eq!(m, "POST");
        assert_eq!(path, "/v1/domain");
        assert_eq!(body.unwrap(), r#"{"name":"example.org"}"#);

        let (m, path, _) =
            build_action_endpoint("verify_domain_dns", &json!({ "domain_id": "d1" })).unwrap();
        assert_eq!(m, "POST");
        assert_eq!(path, "/v1/domain/d1/verify-dns");

        let (m, path, _) = build_action_endpoint(
            "delete_recipient",
            &json!({ "recipient_id": "rec1", "recipients": "a@b.com" }),
        )
        .unwrap();
        assert_eq!(m, "PUT");
        assert_eq!(path, "/v1/recipient/delete/rec1");

        let (m, path, _) = build_action_endpoint(
            "toggle_rule_active",
            &json!({ "rule_id": "r9", "active": false }),
        )
        .unwrap();
        assert_eq!(m, "PUT");
        assert_eq!(path, "/v1/rule/r9/active");
    }

    #[test]
    fn test_build_action_endpoint_totp_access_key_and_unknown() {
        let (m, path, body) = build_action_endpoint("initiate_totp", &json!({})).unwrap();
        assert_eq!(m, "PUT");
        assert_eq!(path, "/v1/user/totp/enable");
        assert!(body.is_none());

        let (m, path, body) =
            build_action_endpoint("create_access_key", &json!({ "expires_days": 30 })).unwrap();
        assert_eq!(m, "POST");
        assert_eq!(path, "/v1/accesskeys");
        let parsed: Value = serde_json::from_str(&body.unwrap()).unwrap();
        assert_eq!(parsed["name"], "Mailx Android");
        assert!(parsed["expires_at"].as_str().is_some());

        let err = build_action_endpoint("unknown_action", &json!({})).unwrap_err();
        assert!(!err.is_empty());
    }

    #[test]
    fn test_build_expiry_iso_from_days_bounds() {
        assert!(build_expiry_iso_from_days(0).is_none());
        assert!(build_expiry_iso_from_days(-5).is_none());
        let iso = build_expiry_iso_from_days(7).unwrap();
        assert!(iso.ends_with("T23:59:59Z"));
        assert_eq!(iso.len(), 20);
    }
}
