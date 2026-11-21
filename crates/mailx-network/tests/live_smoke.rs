use mailx_domain::models::{AliasFilter, AliasRequest};
use mailx_network::client::MailxClient;

fn env_value(name: &str) -> Option<String> {
    std::env::var(name)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

#[test]
#[ignore = "talks to a live Mailx account; set MAILX_LIVE_ACCESS_KEY and run with --ignored"]
fn live_account_round_trip() {
    let base_url =
        env_value("MAILX_LIVE_BASE_URL").unwrap_or_else(|| "https://api.mailx.net".to_string());
    let access_key = env_value("MAILX_LIVE_ACCESS_KEY").expect("MAILX_LIVE_ACCESS_KEY must be set");
    let client = MailxClient::new(&base_url, false).expect("base url is accepted");

    assert!(client.check_livez().expect("livez answers"));

    let auth = client
        .authenticate(&access_key)
        .expect("access key authenticates");
    assert!(!auth.token.is_empty());
    assert!(!auth.domain.is_empty());

    let page = client
        .fetch_aliases(&AliasFilter::default())
        .expect("alias list loads");
    println!(
        "alias page ok: {} listed, {} total",
        page.aliases.len(),
        page.total
    );

    let defaults = client.fetch_defaults().expect("defaults load");
    assert!(!defaults.domains.is_empty());

    if env_value("MAILX_LIVE_ALLOW_MUTATION").as_deref() != Some("1") {
        println!("mutation steps skipped (set MAILX_LIVE_ALLOW_MUTATION=1 to enable)");
        return;
    }

    let request = AliasRequest {
        description: "mailx-android live smoke test".to_string(),
        enabled: true,
        recipients: auth.recipient.clone(),
        from_name: String::new(),
        format: "random".to_string(),
        domain: auth.domain.clone(),
        wildcard_local_part: None,
        wildcard_delimiter: None,
        local_part: None,
    };
    let created = client.create_alias(&request).expect("alias is created");
    let alias_id = created.alias.id.clone();
    assert!(!alias_id.is_empty());

    let pin_result = client.pin_alias(&alias_id, true);
    let delete_result = client.delete_alias(&alias_id);
    pin_result.expect("alias can be pinned");
    delete_result.expect("alias is deleted again");
}
