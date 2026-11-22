use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuleConditionSpec {
    #[serde(default)]
    pub r#type: String,
    #[serde(default)]
    pub r#match: String,
    #[serde(default)]
    pub values: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuleActionSpec {
    #[serde(default)]
    pub r#type: String,
    #[serde(default)]
    pub value: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MailxRuleSpec {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub order: i32,
    #[serde(default)]
    pub operator: String,
    #[serde(default)]
    pub conditions: Vec<RuleConditionSpec>,
    #[serde(default)]
    pub actions: Vec<RuleActionSpec>,
    #[serde(default)]
    pub forwards: bool,
    #[serde(default)]
    pub replies: bool,
    #[serde(default)]
    pub sends: bool,
    #[serde(default)]
    pub active: bool,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct SimulatedEmailContext {
    #[serde(default, alias = "sender")]
    pub sender_email: String,
    #[serde(default)]
    pub sender_name: String,
    #[serde(default)]
    pub subject: String,
    #[serde(default, alias = "alias")]
    pub alias_email: String,
    #[serde(default)]
    pub alias_description: String,
    #[serde(default)]
    pub is_mailing_list: bool,
    #[serde(default)]
    pub has_attachments: bool,
    #[serde(default)]
    pub alias_is_wildcard: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuleEvaluationOutcome {
    pub matched: bool,
    pub matched_conditions: usize,
    pub total_conditions: usize,
    pub should_block: bool,
    pub should_deactivate_alias: bool,
    pub should_remove_attachments: bool,
    pub should_disable_encryption: bool,
    pub banner_override: Option<bool>,
    pub subject_replacement: Option<String>,
    pub display_name_override: Option<String>,
    pub forward_to_override: Vec<String>,
    pub stop_processing: bool,
}

pub fn is_boolean_condition_type(cond_type: &str) -> bool {
    matches!(
        cond_type.trim(),
        "is_mailing_list" | "has_attachments" | "alias_is_wildcard"
    )
}

pub fn is_text_value_action_type(action_type: &str) -> bool {
    matches!(
        action_type.trim(),
        "replace_subject" | "set_display_name" | "set_alias_description" | "forward_to"
    )
}

#[derive(Debug, Clone, Copy)]
pub struct RulePayloadInput<'a> {
    pub rule_id: Option<&'a str>,
    pub name: &'a str,
    pub operator: &'a str,
    pub forwards: bool,
    pub replies: bool,
    pub sends: bool,
    pub active: bool,
    pub conditions: &'a [RuleConditionSpec],
    pub actions: &'a [RuleActionSpec],
}

pub fn normalize_rule_payload_json(input: RulePayloadInput<'_>) -> Result<String, String> {
    let RulePayloadInput {
        rule_id,
        name,
        operator,
        forwards,
        replies,
        sends,
        active,
        conditions,
        actions,
    } = input;
    let clean_name = name.trim();
    if clean_name.is_empty() {
        return Err("Rule name cannot be empty.".to_string());
    }
    if conditions.is_empty() {
        return Err("At least one condition is required.".to_string());
    }
    if conditions.len() > 5 {
        return Err("A rule may contain at most 5 conditions.".to_string());
    }
    if actions.is_empty() {
        return Err("At least one action is required.".to_string());
    }
    if actions.len() > 5 {
        return Err("A rule may contain at most 5 actions.".to_string());
    }

    let norm_op = if operator.trim().eq_ignore_ascii_case("OR") {
        "OR"
    } else {
        "AND"
    };

    let cond_values: Vec<Value> = conditions
        .iter()
        .map(|c| {
            let is_bool = is_boolean_condition_type(&c.r#type);
            let m = if is_bool { "" } else { c.r#match.trim() };
            let vals: Vec<String> = if is_bool {
                Vec::new()
            } else {
                c.values
                    .iter()
                    .map(|v| v.trim().to_string())
                    .filter(|v| !v.is_empty())
                    .collect()
            };
            json!({
                "type": c.r#type.trim(),
                "match": m,
                "values": vals
            })
        })
        .collect();

    let act_values: Vec<Value> = actions
        .iter()
        .map(|a| {
            let val = if is_text_value_action_type(&a.r#type) {
                a.value.trim()
            } else {
                ""
            };
            json!({
                "type": a.r#type.trim(),
                "value": val
            })
        })
        .collect();

    let mut root = serde_json::Map::new();
    if let Some(id) = rule_id {
        if !id.trim().is_empty() {
            root.insert("id".to_string(), Value::String(id.trim().to_string()));
        }
    }
    root.insert("name".to_string(), Value::String(clean_name.to_string()));
    root.insert("operator".to_string(), Value::String(norm_op.to_string()));
    root.insert("forwards".to_string(), Value::Bool(forwards));
    root.insert("replies".to_string(), Value::Bool(replies));
    root.insert("sends".to_string(), Value::Bool(sends));
    root.insert("active".to_string(), Value::Bool(active));
    root.insert("conditions".to_string(), Value::Array(cond_values));
    root.insert("actions".to_string(), Value::Array(act_values));

    Ok(Value::Object(root).to_string())
}

fn match_single_text(candidate: &str, match_op: &str, pattern: &str) -> bool {
    let cand_lower = candidate.trim().to_ascii_lowercase();
    let pat_lower = pattern.trim().to_ascii_lowercase();
    match match_op.trim() {
        "is" => cand_lower == pat_lower,
        "is_not" => cand_lower != pat_lower,
        "contains" => cand_lower.contains(&pat_lower),
        "not_contains" => !cand_lower.contains(&pat_lower),
        "starts_with" => cand_lower.starts_with(&pat_lower),
        "not_starts_with" => !cand_lower.starts_with(&pat_lower),
        "ends_with" => cand_lower.ends_with(&pat_lower),
        "not_ends_with" => !cand_lower.ends_with(&pat_lower),
        "regex" => wildcard_like_match(&cand_lower, &pat_lower),
        "not_regex" => !wildcard_like_match(&cand_lower, &pat_lower),
        _ => cand_lower.contains(&pat_lower),
    }
}

fn wildcard_like_match(text: &str, pattern: &str) -> bool {
    if pattern.is_empty() || pattern == ".*" || pattern == "*" {
        return true;
    }
    let clean = pattern.trim_start_matches('^').trim_end_matches('$');
    if pattern.starts_with('^') && pattern.ends_with('$') {
        text == clean
    } else if pattern.starts_with('^') {
        text.starts_with(clean)
    } else if pattern.ends_with('$') {
        text.ends_with(clean)
    } else {
        text.contains(clean)
    }
}

pub fn evaluate_condition(cond: &RuleConditionSpec, ctx: &SimulatedEmailContext) -> bool {
    match cond.r#type.trim() {
        "is_mailing_list" => return ctx.is_mailing_list,
        "has_attachments" => return ctx.has_attachments,
        "alias_is_wildcard" => return ctx.alias_is_wildcard,
        _ => {}
    }

    let target_field = match cond.r#type.trim() {
        "sender" => ctx.sender_email.as_str(),
        "sender_domain" => ctx
            .sender_email
            .split_once('@')
            .map(|(_, d)| d)
            .unwrap_or(ctx.sender_email.as_str()),
        "sender_name" => ctx.sender_name.as_str(),
        "subject" => ctx.subject.as_str(),
        "alias" => ctx.alias_email.as_str(),
        "alias_description" => ctx.alias_description.as_str(),
        _ => "",
    };

    if cond.values.is_empty() {
        return false;
    }

    let is_negative_op = matches!(
        cond.r#match.trim(),
        "is_not" | "not_contains" | "not_starts_with" | "not_ends_with" | "not_regex"
    );

    if is_negative_op {
        cond.values
            .iter()
            .all(|val| match_single_text(target_field, &cond.r#match, val))
    } else {
        cond.values
            .iter()
            .any(|val| match_single_text(target_field, &cond.r#match, val))
    }
}

pub fn evaluate_rule(rule: &MailxRuleSpec, ctx: &SimulatedEmailContext) -> RuleEvaluationOutcome {
    let mut matched_conditions = 0usize;
    for cond in &rule.conditions {
        if evaluate_condition(cond, ctx) {
            matched_conditions += 1;
        }
    }

    let total_conditions = rule.conditions.len();
    let is_or = rule.operator.trim().eq_ignore_ascii_case("OR");
    let matched = if total_conditions == 0 {
        false
    } else if is_or {
        matched_conditions > 0
    } else {
        matched_conditions == total_conditions
    };

    let mut outcome = RuleEvaluationOutcome {
        matched,
        matched_conditions,
        total_conditions,
        should_block: false,
        should_deactivate_alias: false,
        should_remove_attachments: false,
        should_disable_encryption: false,
        banner_override: None,
        subject_replacement: None,
        display_name_override: None,
        forward_to_override: Vec::new(),
        stop_processing: false,
    };

    if !matched {
        return outcome;
    }

    for action in &rule.actions {
        match action.r#type.trim() {
            "block" | "blocklist_sender" | "blocklist_domain" => outcome.should_block = true,
            "deactivate_alias" => outcome.should_deactivate_alias = true,
            "remove_attachments" => outcome.should_remove_attachments = true,
            "disable_encryption" => outcome.should_disable_encryption = true,
            "hide_banner" => outcome.banner_override = Some(false),
            "show_banner" => outcome.banner_override = Some(true),
            "replace_subject" => outcome.subject_replacement = Some(action.value.clone()),
            "set_display_name" => outcome.display_name_override = Some(action.value.clone()),
            "forward_to" => {
                outcome.forward_to_override = action
                    .value
                    .split(',')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect();
            }
            "stop" => outcome.stop_processing = true,
            _ => {}
        }
    }

    outcome
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_evaluate_rule_and_actions() {
        let rule = MailxRuleSpec {
            id: "r1".to_string(),
            name: "Block Newsletter Promo".to_string(),
            order: 1,
            operator: "AND".to_string(),
            conditions: vec![
                RuleConditionSpec {
                    r#type: "sender_domain".to_string(),
                    r#match: "is".to_string(),
                    values: vec!["promo.example.com".to_string()],
                },
                RuleConditionSpec {
                    r#type: "is_mailing_list".to_string(),
                    r#match: String::new(),
                    values: Vec::new(),
                },
            ],
            actions: vec![
                RuleActionSpec {
                    r#type: "remove_attachments".to_string(),
                    value: String::new(),
                },
                RuleActionSpec {
                    r#type: "replace_subject".to_string(),
                    value: "[Filtered Promo]".to_string(),
                },
            ],
            forwards: true,
            replies: false,
            sends: false,
            active: true,
        };

        let ctx = SimulatedEmailContext {
            sender_email: "deals@promo.example.com".to_string(),
            sender_name: "Deals".to_string(),
            subject: "50% Off Today".to_string(),
            alias_email: "shopping.fox12@mailx.net".to_string(),
            alias_description: "Shopping".to_string(),
            is_mailing_list: true,
            has_attachments: true,
            alias_is_wildcard: false,
        };

        let out = evaluate_rule(&rule, &ctx);
        assert!(out.matched);
        assert_eq!(out.matched_conditions, 2);
        assert!(out.should_remove_attachments);
        assert_eq!(out.subject_replacement.as_deref(), Some("[Filtered Promo]"));
    }

    #[test]
    fn test_normalize_rule_payload_validation_bounds() {
        let cond = RuleConditionSpec {
            r#type: "is_mailing_list".to_string(),
            r#match: "is".to_string(),
            values: vec!["ignored".to_string()],
        };
        let act = RuleActionSpec {
            r#type: "block".to_string(),
            value: "ignored".to_string(),
        };
        let empty_name = normalize_rule_payload_json(RulePayloadInput {
            rule_id: None,
            name: "   ",
            operator: "OR",
            forwards: true,
            replies: false,
            sends: false,
            active: true,
            conditions: std::slice::from_ref(&cond),
            actions: std::slice::from_ref(&act),
        });
        assert!(empty_name.is_err());

        let too_many_conds = vec![cond.clone(); 6];
        let over_limit = normalize_rule_payload_json(RulePayloadInput {
            rule_id: None,
            name: "Too Many",
            operator: "AND",
            forwards: true,
            replies: false,
            sends: false,
            active: true,
            conditions: &too_many_conds,
            actions: std::slice::from_ref(&act),
        });
        assert!(over_limit.is_err());

        let valid = normalize_rule_payload_json(RulePayloadInput {
            rule_id: Some("r42"),
            name: "  List Guard  ",
            operator: "or",
            forwards: true,
            replies: true,
            sends: false,
            active: true,
            conditions: &[cond],
            actions: &[act],
        })
        .unwrap();
        let parsed: Value = serde_json::from_str(&valid).unwrap();
        assert_eq!(parsed["id"], "r42");
        assert_eq!(parsed["operator"], "OR");
        assert_eq!(parsed["conditions"][0]["match"], "");
        assert_eq!(parsed["actions"][0]["value"], "");
    }

    #[test]
    fn test_evaluate_or_operator_and_negative_matchers() {
        let rule = MailxRuleSpec {
            id: "r2".to_string(),
            name: "OR Rule".to_string(),
            order: 1,
            operator: "OR".to_string(),
            conditions: vec![
                RuleConditionSpec {
                    r#type: "subject".to_string(),
                    r#match: "starts_with".to_string(),
                    values: vec!["urgent".to_string()],
                },
                RuleConditionSpec {
                    r#type: "sender".to_string(),
                    r#match: "not_contains".to_string(),
                    values: vec!["trusted.org".to_string()],
                },
            ],
            actions: vec![RuleActionSpec {
                r#type: "block".to_string(),
                value: String::new(),
            }],
            forwards: true,
            replies: false,
            sends: false,
            active: true,
        };
        let ctx = SimulatedEmailContext {
            sender_email: "spam@unknown.net".to_string(),
            subject: "Regular update".to_string(),
            ..Default::default()
        };
        let out = evaluate_rule(&rule, &ctx);
        assert!(out.matched);
        assert_eq!(out.matched_conditions, 1);
        assert!(out.should_block);
    }

    #[test]
    fn test_evaluate_regex_anchors_and_all_actions() {
        let rule = MailxRuleSpec {
            id: "r3".to_string(),
            name: "Full Actions".to_string(),
            order: 2,
            operator: "AND".to_string(),
            conditions: vec![RuleConditionSpec {
                r#type: "subject".to_string(),
                r#match: "regex".to_string(),
                values: vec!["^invoice$".to_string()],
            }],
            actions: vec![
                RuleActionSpec {
                    r#type: "deactivate_alias".to_string(),
                    value: String::new(),
                },
                RuleActionSpec {
                    r#type: "disable_encryption".to_string(),
                    value: String::new(),
                },
                RuleActionSpec {
                    r#type: "hide_banner".to_string(),
                    value: String::new(),
                },
                RuleActionSpec {
                    r#type: "set_display_name".to_string(),
                    value: "Billing".to_string(),
                },
                RuleActionSpec {
                    r#type: "forward_to".to_string(),
                    value: "a@example.com, b@example.com".to_string(),
                },
                RuleActionSpec {
                    r#type: "stop".to_string(),
                    value: String::new(),
                },
            ],
            forwards: true,
            replies: false,
            sends: false,
            active: true,
        };
        let ctx = SimulatedEmailContext {
            subject: "INVOICE".to_string(),
            ..Default::default()
        };
        let out = evaluate_rule(&rule, &ctx);
        assert!(out.matched);
        assert!(out.should_deactivate_alias);
        assert!(out.should_disable_encryption);
        assert_eq!(out.banner_override, Some(false));
        assert_eq!(out.display_name_override.as_deref(), Some("Billing"));
        assert_eq!(out.forward_to_override.len(), 2);
        assert!(out.stop_processing);
    }

    #[test]
    fn test_unmatched_rule_triggers_no_actions() {
        let rule = MailxRuleSpec {
            id: "r4".to_string(),
            name: "No Match".to_string(),
            order: 3,
            operator: "AND".to_string(),
            conditions: vec![RuleConditionSpec {
                r#type: "subject".to_string(),
                r#match: "ends_with".to_string(),
                values: vec!["receipt".to_string()],
            }],
            actions: vec![RuleActionSpec {
                r#type: "block".to_string(),
                value: String::new(),
            }],
            forwards: true,
            replies: false,
            sends: false,
            active: true,
        };
        let ctx = SimulatedEmailContext {
            subject: "Hello friend".to_string(),
            ..Default::default()
        };
        let out = evaluate_rule(&rule, &ctx);
        assert!(!out.matched);
        assert!(!out.should_block);
    }
}
