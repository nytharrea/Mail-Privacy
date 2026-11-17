package net.mailx.android.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.AddyPalette

/**
 * Mailx `Account` (`43692.jpg`..`43702.jpg`: Subscription, Account Info, Change Email/Password,
 * 2-Factor Authentication TOTP, Access Keys, Alias CSV Import/Export) alt penceresi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBottomSheet(
    userAccount: UiUserAccount,
    accessKeys: List<UiAccessKey>,
    aliasCount: Int,
    onDismiss: () -> Unit,
    onChangePassword: (oldPassword: String, newPassword: String) -> Unit,
    onChangeEmail: (newEmail: String, currentPassword: String) -> Unit,
    onInitiateTotp: () -> Unit,
    onConfirmTotp: (otp: String) -> Unit,
    onDisableTotp: (otp: String) -> Unit,
    onCreateAccessKey: (name: String, expiresDays: Int?) -> Unit,
    onClearNewAccessKeyToken: () -> Unit,
    onDeleteAccessKey: (id: String) -> Unit,
    onExportAliasesCsv: (onCsvReady: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var totpConfirmInput by remember { mutableStateOf("") }
    var showDisableTotpDialog by remember { mutableStateOf(false) }
    var exportedCsvPreview by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Hesap, Abonelik ve Güvenlik",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 1. Abonelik ve Hesap Bilgisi (`43692.jpg`)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Abonelik Durumu (Subscription)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Surface(
                            shape = CircleShape,
                            color = AddyPalette.GreenActive.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = userAccount.subStatus.ifBlank { "Active" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AddyPalette.GreenActive,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (userAccount.subActiveUntil.isNotBlank()) {
                        Text(
                            text = "Geçerlilik Tarihi: ${formatIsoDateShort(userAccount.subActiveUntil)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (userAccount.email.isNotBlank()) {
                        Text(
                            text = "Birincil E-posta: ${userAccount.email} (${if (userAccount.isActive) "Doğrulandı" else "Doğrulanmadı"})",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (userAccount.id.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = "Mailx ID: ${userAccount.id}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. E-posta & Parola Yönetimi (`43692.jpg`, `43694.jpg`)
            AddySectionHeader(title = "Kimlik ve İki Aşamalı Doğrulama (2FA)")

            AddySectionView(
                title = "Birincil E-postayı Değiştir",
                description = userAccount.email.ifBlank { "Hesap e-posta adresini güncelle" },
                startIcon = Icons.Default.Email,
                endIcon = Icons.Default.Edit,
                onClick = { showChangeEmailDialog = true }
            )

            AddySectionView(
                title = "Parolayı Değiştir (Change Password)",
                description = "Hesap giriş parolanızı güncelleyin",
                startIcon = Icons.Default.LockReset,
                endIcon = Icons.Default.Edit,
                onClick = { showChangePasswordDialog = true }
            )

            // 3. 2FA TOTP (`43694.jpg`)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("İki Aşamalı Doğrulama (2FA TOTP)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = if (userAccount.totpEnabled) "Durum: Etkin (Active)" else "Durum: Devre Dışı",
                                fontSize = 13.sp,
                                color = if (userAccount.totpEnabled) AddyPalette.GreenActive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (userAccount.totpEnabled) {
                            OutlinedButton(
                                onClick = { showDisableTotpDialog = true },
                                shape = CircleShape
                            ) {
                                Text("Devre Dışı Bırak", color = AddyPalette.SoftRed)
                            }
                        } else {
                            Button(
                                onClick = onInitiateTotp,
                                shape = CircleShape
                            ) {
                                Text("2FA Etkinleştir")
                            }
                        }
                    }

                    if (userAccount.totpSetupSecret.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = "Kimlik doğrulayıcı uygulamanıza (Aegis, Ente Auth vb.) aşağıdaki TOTP anahtarını ekleyin ve 6 haneli kodu girin:",
                            fontSize = 12.sp
                        )
                        SelectionContainer {
                            Text(
                                text = userAccount.totpSetupSecret,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = totpConfirmInput,
                                onValueChange = { totpConfirmInput = it.take(6) },
                                label = { Text("6 Haneli TOTP Kodu") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = totpConfirmInput.length == 6,
                                onClick = { onConfirmTotp(totpConfirmInput) }
                            ) {
                                Text("Onayla")
                            }
                        }
                    }

                    if (userAccount.totpBackupCodes.isNotBlank()) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Yedek Kurtarma Kodunuz (Güvenli bir yere kaydedin):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                SelectionContainer {
                                    Text(
                                        text = userAccount.totpBackupCodes,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Erişim Anahtarları (`Access Keys` - `43698.jpg`, `43700.jpg`)
            AddySectionHeader(
                title = "Erişim Anahtarları (Access Keys)",
                countBadge = accessKeys.size,
                actionText = "Yeni Anahtar",
                onActionClick = { showCreateKeyDialog = true }
            )

            if (userAccount.newlyCreatedAccessKeyToken.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Yeni Erişim Anahtarınız (Yalnızca bir kez gösterilir):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        SelectionContainer {
                            Text(
                                text = userAccount.newlyCreatedAccessKeyToken,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        "Mailx Access Key",
                                        userAccount.newlyCreatedAccessKeyToken,
                                        "85 karakterlik erişim anahtarı kopyalandı"
                                    )
                                },
                                shape = CircleShape
                            ) {
                                Text("Anahtarı Kopyala")
                            }
                            TextButton(onClick = onClearNewAccessKeyToken) {
                                Text("Gizle")
                            }
                        }
                    }
                }
            }

            accessKeys.forEach { keyItem ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(keyItem.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = "ID: ${keyItem.id}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (keyItem.expiresAt.isNotBlank()) {
                                    "Bitiş: ${formatIsoDateShort(keyItem.expiresAt)}"
                                } else {
                                    "Süresiz • Oluşturulma: ${formatIsoDateShort(keyItem.createdAt)}"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteAccessKey(keyItem.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Anahtarı Sil",
                                tint = AddyPalette.SoftRed
                            )
                        }
                    }
                }
            }

            // 5. Takma Adları Dışa Aktar (`Alias Export CSV` - `43700.jpg`, `43702.jpg`)
            AddySectionHeader(title = "Takma Adları Dışa Aktar (CSV Export)")

            AddySectionView(
                title = "Tüm Takma Adları CSV Olarak Dışa Aktar",
                description = "$aliasCount takma adı durum, alıcı ve açıklamalarıyla CSV formatında dışa aktar",
                startIcon = Icons.Default.Download,
                onClick = {
                    onExportAliasesCsv { csvText ->
                        exportedCsvPreview = csvText
                    }
                }
            )
        }
    }

    if (showChangePasswordDialog) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Parolayı Değiştir") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPw,
                        onValueChange = { oldPw = it },
                        label = { Text("Mevcut Parola") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("Yeni Parola (En az 12 karakter)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = oldPw.isNotEmpty() && newPw.length >= 8,
                    onClick = {
                        showChangePasswordDialog = false
                        onChangePassword(oldPw, newPw)
                    }
                ) {
                    Text("Parolayı Güncelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showChangeEmailDialog) {
        var newEm by remember { mutableStateOf(userAccount.email) }
        var curPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangeEmailDialog = false },
            title = { Text("Birincil E-postayı Değiştir") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newEm,
                        onValueChange = { newEm = it },
                        label = { Text("Yeni E-posta Adresi") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = curPw,
                        onValueChange = { curPw = it },
                        label = { Text("Doğrulama İçin Mevcut Parolanız") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newEm.contains("@"),
                    onClick = {
                        showChangeEmailDialog = false
                        onChangeEmail(newEm, curPw)
                    }
                ) {
                    Text("Güncelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeEmailDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showCreateKeyDialog) {
        var keyName by remember { mutableStateOf("") }
        var expiryDays by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { showCreateKeyDialog = false },
            title = { Text("Yeni Erişim Anahtarı Oluştur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text("Anahtar Adı (Örn: Android Telefon)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expiryDays,
                        onValueChange = { expiryDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Geçerlilik Süresi (Gün, süresiz için boş bırakın)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateKeyDialog = false
                        onCreateAccessKey(keyName, expiryDays.toIntOrNull())
                    }
                ) {
                    Text("Oluştur")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateKeyDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showDisableTotpDialog) {
        var otp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableTotpDialog = false },
            title = { Text("2FA Doğrulamasını Devre Dışı Bırak") },
            text = {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.take(6) },
                    label = { Text("6 Haneli TOTP Kodu") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = otp.length == 6,
                    onClick = {
                        showDisableTotpDialog = false
                        onDisableTotp(otp)
                    }
                ) {
                    Text("Devre Dışı Bırak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableTotpDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    exportedCsvPreview?.let { csv ->
        AlertDialog(
            onDismissRequest = { exportedCsvPreview = null },
            title = { Text("Takma Adlar CSV Dışa Aktarımı") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = csv,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, "Mailx Aliases CSV", csv, "CSV içeriği panoya kopyalandı")
                        exportedCsvPreview = null
                    }
                ) {
                    Text("CSV'yi Panoya Kopyala")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedCsvPreview = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}

/**
 * Mailx `Diagnostics` (`43672.jpg`, `43690.jpg`: Delivery/Bounce/Block Logs, View Log File, Delete All Logs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    logs: List<UiDiagnosticLog>,
    logIssuesEnabled: Boolean,
    onToggleLogIssues: (Boolean) -> Unit,
    onFetchBounceFile: (logId: String, onLoaded: (String) -> Unit) -> Unit,
    onDeleteAllLogs: () -> Unit,
    onRefreshLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewingRawBounceText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tanılama Günlükleri (Diagnostics)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onRefreshLogs) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            AddySectionView(
                title = "Tanılama Kaydını Etkinleştir (Enable Diagnostics)",
                description = "İletim hatalarını, geri dönen (bounce) ve kural tarafından engellenen iletileri kaydet",
                startIcon = Icons.Default.BugReport,
                showSwitch = true,
                switchChecked = logIssuesEnabled,
                onSwitchChange = onToggleLogIssues
            )

            if (logs.isNotEmpty()) {
                Button(
                    onClick = onDeleteAllLogs,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tüm Günlükleri Sil (Delete All Logs)")
                }
            }

            if (logs.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Default.Verified,
                    title = "Kayıtlı tanılama günlüğü bulunmuyor",
                    subtitle = "Şu ana kadar kaydedilmiş bir iletim hatası, geri dönen (bounce) veya engellenen ileti kaydı yok."
                )
            } else {
                logs.forEach { log ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onFetchBounceFile(log.id) { raw ->
                                    viewingRawBounceText = raw
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.logType.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AddyPalette.PortalOrange
                                )
                                Text(
                                    text = formatIsoDateShort(log.attemptedAt.ifBlank { log.createdAt }),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.from.isNotBlank() || log.destination.isNotBlank()) {
                                Text(
                                    text = "${log.from} -> ${log.destination}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (log.message.isNotBlank()) {
                                Text(
                                    text = log.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.remoteMta.isNotBlank() || log.status.isNotBlank()) {
                                Text(
                                    text = "Durum: ${log.status} • MTA: ${log.remoteMta}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewingRawBounceText?.let { raw ->
        AlertDialog(
            onDismissRequest = { viewingRawBounceText = null },
            title = { Text("Ham Günlük Detayı (Bounce Log)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(raw, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingRawBounceText = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsBottomSheet(
    announcements: List<UiAnnouncement>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Duyurular (Announcements)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (announcements.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Default.Campaign,
                    title = "Yeni duyuru bulunmuyor",
                    subtitle = "Mailx servisiyle ilgili yeni bir duyuru yayınlandığında burada görüntülenecektir."
                )
            } else {
                announcements.forEach { ann ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(ann.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(ann.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
