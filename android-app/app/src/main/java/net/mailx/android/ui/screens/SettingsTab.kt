package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.MailxRuleCatalog
import net.mailx.android.ui.model.ThemeUiState
import net.mailx.android.ui.model.formatAliasTypeLabel
import net.mailx.android.ui.theme.AddyPalette

/**
 * `anonaddy/addy-android` içindeki `res/layout/activity_app_settings.xml` (`AppSettingsActivity.kt`)
 * ve `res/layout/bottomsheet_uiux_interface.xml` (`UIUXInterfaceBottomDialogFragment.kt`) +
 * Mailx `Settings` (`43678.jpg`..`43690.jpg`: Default Domain, Default Recipient, Default Alias Format,
 * From Name, Mailx Header, Email Trackers, Unsubscribe Behaviour [5 seçenek], Diagnostics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    baseUrl: String,
    defaultsState: DefaultsUiState,
    themeState: ThemeUiState,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultRecipient: (String) -> Unit = {},
    onUpdateDefaultFormat: (String) -> Unit,
    onUpdateDefaultFromName: (String) -> Unit = {},
    onUpdateIncludeMailxHeader: (Boolean) -> Unit = {},
    onUpdateBlockTrackers: (Boolean) -> Unit = {},
    onUpdateUnsubscribeBehaviour: (Int) -> Unit = {},
    onUpdateLogIssues: (Boolean) -> Unit = {},
    onUpdateDarkModeMode: (Int) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
    onSyncDefaults: () -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onOpenDiagnosticsSheet: () -> Unit = {},
    onOpenAccountSheet: () -> Unit = {},
    onOpenAnnouncementsSheet: () -> Unit = {},
    onRequestLogout: () -> Unit
) {
    var showInterfaceSheet by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showRecipientDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showFromNameDialog by remember { mutableStateOf(false) }
    var showUnsubscribeDialog by remember { mutableStateOf(false) }

    val darkModeLabel = when (themeState.darkModeMode) {
        1 -> "Karanlık Mod: Açık"
        0 -> "Karanlık Mod: Kapalı"
        else -> "Karanlık Mod: Sistem Varsayılanı"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Genel & Görünüm (addy-android AppSettingsActivity)
        item(key = "header_general") {
            AddySectionHeader(title = "Genel ve Görünüm")
        }

        item(key = "section_uiux_interface") {
            AddySectionView(
                title = "Görünüm ve Arayüz",
                description = "$darkModeLabel • Tema ve renk seçeneklerini özelleştirin",
                startIcon = Icons.Default.Brightness6,
                onClick = { showInterfaceSheet = true }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "section_dynamic_colors") {
                AddySectionView(
                    title = "Dinamik Renkler",
                    description = "Uygulama renklerini cihaz duvar kağıdına göre uyarla",
                    startIcon = Icons.Default.Palette,
                    showSwitch = true,
                    switchChecked = themeState.dynamicColorEnabled,
                    onSwitchChange = onToggleDynamicColor
                )
            }
        }

        item(key = "section_amoled_dark") {
            AddySectionView(
                title = "Saf Siyah Tema",
                description = "Karanlık modda tam siyah arka plan kullan",
                startIcon = Icons.Default.Contrast,
                showSwitch = true,
                switchChecked = themeState.amoledDarkEnabled,
                onSwitchChange = onToggleAmoledDark
            )
        }

        item(key = "section_mailto_integration") {
            AddySectionView(
                title = "Takma Addan İleti Gönder",
                description = "Alıcı adresleri için ters-alias (Send from alias) oluştur",
                startIcon = Icons.AutoMirrored.Filled.Send,
                onClick = onOpenReverseAliasTool
            )
        }

        // 2. Mailx Sunucu Ayarları (`43678.jpg`..`43690.jpg`)
        item(key = "header_defaults") {
            AddySectionHeader(
                title = "Mailx Sunucu Ayarları",
                actionText = "Senkronize Et",
                onActionClick = onSyncDefaults,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "section_default_domain") {
            AddySectionView(
                title = "Varsayılan Alan Adı (Default Domain)",
                description = "@${defaultsState.defaultDomain}",
                startIcon = Icons.Default.Language,
                endIcon = Icons.Default.Edit,
                onClick = { showDomainDialog = true }
            )
        }

        item(key = "section_default_recipient") {
            AddySectionView(
                title = "Varsayılan Alıcı (Default Recipient)",
                description = defaultsState.defaultRecipient.ifBlank { "Varsayılan alıcı seçin" },
                startIcon = Icons.Default.Inbox,
                endIcon = Icons.Default.Edit,
                onClick = { showRecipientDialog = true }
            )
        }

        item(key = "section_default_format") {
            AddySectionView(
                title = "Varsayılan Takma Ad Formatı",
                description = formatAliasTypeLabel(defaultsState.defaultFormat),
                startIcon = Icons.Default.AlternateEmail,
                endIcon = Icons.Default.Edit,
                onClick = { showFormatDialog = true }
            )
        }

        item(key = "section_from_name") {
            AddySectionView(
                title = "Gönderici Adı (From Name)",
                description = defaultsState.defaultFromName.ifBlank {
                    "Boş bırakıldığında takma ad e-posta adresi kullanılır"
                },
                startIcon = Icons.Default.Badge,
                endIcon = Icons.Default.Edit,
                onClick = { showFromNameDialog = true }
            )
        }

        item(key = "section_mailx_header") {
            val includeHeader = !defaultsState.removeHeader
            AddySectionView(
                title = "Mailx Başlığı Ekle (Mailx Header)",
                description = "İletilen e-postalara 'Sent to <alias> from <sender>' başlığı ekle",
                startIcon = Icons.Default.MailOutline,
                showSwitch = true,
                switchChecked = includeHeader,
                onSwitchChange = onUpdateIncludeMailxHeader
            )
        }

        item(key = "section_block_trackers") {
            AddySectionView(
                title = "E-posta Takipçilerini Engelle (Email Trackers)",
                description = "Bilinen e-posta açılma piksellerini ve takip bağlantılarını kaldır",
                startIcon = Icons.Default.Shield,
                showSwitch = true,
                switchChecked = defaultsState.blockTrackers,
                onSwitchChange = onUpdateBlockTrackers
            )
        }

        item(key = "section_unsubscribe_behaviour") {
            AddySectionView(
                title = "Abonelikten Çıkma Davranışı (Unsubscribe)",
                description = MailxRuleCatalog.unsubscribeLabel(defaultsState.unsubscribeBehaviour),
                startIcon = Icons.Default.Unsubscribe,
                endIcon = Icons.Default.Edit,
                onClick = { showUnsubscribeDialog = true }
            )
        }

        item(key = "section_enable_diagnostics") {
            AddySectionView(
                title = "Tanılama Günlüklerini Etkinleştir (Diagnostics)",
                description = "İletim hatalarını, geri dönen (bounce) ve engellenen iletileri kaydet",
                startIcon = Icons.Default.BugReport,
                showSwitch = true,
                switchChecked = defaultsState.logIssues,
                onSwitchChange = onUpdateLogIssues
            )
        }

        item(key = "section_open_diagnostics") {
            AddySectionView(
                title = "Tanılama Günlüklerini Görüntüle & Temizle",
                description = "İletim günlüklerini incele veya tüm kayıtları sil",
                startIcon = Icons.Default.ReceiptLong,
                onClick = onOpenDiagnosticsSheet
            )
        }

        // 3. Hesap, Güvenlik ve Veri Yönetimi (`43692.jpg`..`43702.jpg`)
        item(key = "header_manage_data") {
            AddySectionHeader(
                title = "Hesap, Güvenlik ve Veriler",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "section_account_security") {
            AddySectionView(
                title = "Hesap, Abonelik ve Güvenlik",
                description = "Abonelik durumu, 2FA (TOTP), erişim anahtarları, parola ve CSV içe/dışa aktarım",
                startIcon = Icons.Default.ManageAccounts,
                onClick = onOpenAccountSheet
            )
        }

        item(key = "section_announcements") {
            AddySectionView(
                title = "Duyurular (Announcements)",
                description = "Mailx servis güncellemeleri ve duyuruları",
                startIcon = Icons.Default.Campaign,
                onClick = onOpenAnnouncementsSheet
            )
        }

        item(key = "section_reset_logout") {
            AddySectionView(
                title = "Uygulamayı Sıfırla (Çıkış Yap)",
                description = "Bu cihazdaki tüm oturum verilerini ve önbelleği silerek çıkış yapar",
                startIcon = Icons.Default.RestartAlt,
                accentColor = AddyPalette.SoftRed,
                onClick = onRequestLogout
            )
        }

        // 4. Uygulama Hakkında Alt Bilgi
        item(key = "settings_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailUnread,
                            contentDescription = "Mailx",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = "Gizlilik ve özenle tasarlandı",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "v0.3.0 • $baseUrl",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showInterfaceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showInterfaceSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Görünüm ve Arayüz",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AddySectionView(
                            title = "Dinamik Renkler",
                            description = "Uygulama renklerini cihaz duvar kağıdına göre uyarla",
                            startIcon = Icons.Default.Palette,
                            showSwitch = true,
                            switchChecked = themeState.dynamicColorEnabled,
                            onSwitchChange = onToggleDynamicColor
                        )
                    }

                    AddySectionView(
                        title = "Saf Siyah Tema",
                        description = "Karanlık modda tam siyah arka plan kullan",
                        startIcon = Icons.Default.Contrast,
                        showSwitch = true,
                        switchChecked = themeState.amoledDarkEnabled,
                        onSwitchChange = onToggleAmoledDark
                    )

                    Text(
                        text = "Karanlık Mod",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            selected = themeState.darkModeMode == 1,
                            onClick = { onUpdateDarkModeMode(1) },
                            label = { Text("Açık") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            selected = themeState.darkModeMode == -1,
                            onClick = { onUpdateDarkModeMode(-1) },
                            label = { Text("Sistem") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            selected = themeState.darkModeMode == 0,
                            onClick = { onUpdateDarkModeMode(0) },
                            label = { Text("Kapalı") }
                        )
                    }
                }
            }
        }
    }

    if (showDomainDialog) {
        AlertDialog(
            onDismissRequest = { showDomainDialog = false },
            title = { Text("Varsayılan Alan Adı (Default Domain)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    defaultsState.availableDomains.forEach { dom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultDomain(dom)
                                    showDomainDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultDomain == dom,
                                onClick = {
                                    onUpdateDefaultDomain(dom)
                                    showDomainDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("@$dom")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDomainDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showRecipientDialog) {
        var customRec by remember { mutableStateOf(defaultsState.defaultRecipient) }
        AlertDialog(
            onDismissRequest = { showRecipientDialog = false },
            title = { Text("Varsayılan Alıcı (Default Recipient)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultsState.availableRecipients.forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultRecipient(rec)
                                    showRecipientDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultRecipient == rec,
                                onClick = {
                                    onUpdateDefaultRecipient(rec)
                                    showRecipientDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rec)
                        }
                    }
                    OutlinedTextField(
                        value = customRec,
                        onValueChange = { customRec = it },
                        label = { Text("Alıcı E-posta Adresi") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRecipientDialog = false
                        if (customRec.isNotBlank()) onUpdateDefaultRecipient(customRec.trim())
                    }
                ) {
                    Text("Güncelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecipientDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showFormatDialog) {
        val formats = listOf("words", "random", "uuid")
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Varsayılan Takma Ad Formatı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    formats.forEach { fmt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultFormat(fmt)
                                    showFormatDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultFormat == fmt,
                                onClick = {
                                    onUpdateDefaultFormat(fmt)
                                    showFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatAliasTypeLabel(fmt))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showFromNameDialog) {
        var nameInput by remember { mutableStateOf(defaultsState.defaultFromName) }
        AlertDialog(
            onDismissRequest = { showFromNameDialog = false },
            title = { Text("Gönderici Adı (From Name)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Takma ad üzerinden yanıt verirken veya yeni ileti gönderirken kullanılacak varsayılan görünen ad (Boş bırakılırsa takma ad adresi kullanılır):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Gönderici Adı") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFromNameDialog = false
                        onUpdateDefaultFromName(nameInput)
                    }
                ) {
                    Text("Güncelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFromNameDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // `43686.jpg`: Unsubscribe Behaviour (5 seçenek)
    if (showUnsubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeDialog = false },
            title = { Text("Abonelikten Çıkma Davranışı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MailxRuleCatalog.unsubscribeBehaviours.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateUnsubscribeBehaviour(code)
                                    showUnsubscribeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.unsubscribeBehaviour == code,
                                onClick = {
                                    onUpdateUnsubscribeBehaviour(code)
                                    showUnsubscribeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnsubscribeDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}
