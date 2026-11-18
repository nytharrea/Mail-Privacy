package net.mailx.android.ui.sheets

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxGroupDivider
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxOptionPickerDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.MailxSettingsGroupCard
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONObject
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBottomSheet(
    userAccount: UiUserAccount,
    accessKeys: List<UiAccessKey>,
    passkeys: List<UiPasskeyCredential> = emptyList(),
    aliasCount: Int,
    onDismiss: () -> Unit,
    onSendAccountOtp: () -> Unit = {},
    onVerifyAccountOtp: (otp: String) -> Unit = {},
    onChangePassword: (oldPassword: String, newPassword: String) -> Unit,
    onChangeEmail: (newEmail: String, currentPassword: String) -> Unit,
    onInitiateTotp: () -> Unit,
    onConfirmTotp: (otp: String) -> Unit,
    onDisableTotp: (otp: String) -> Unit,
    onRegisterPasskey: (Context) -> Unit = {},
    onDeletePasskey: (id: String) -> Unit = {},
    onCreateAccessKey: (name: String, expiresDays: Int?) -> Unit,
    onClearNewAccessKeyToken: () -> Unit,
    onDeleteAccessKey: (id: String) -> Unit,
    onExportAliasesCsv: (onCsvReady: (String) -> Unit) -> Unit,
    onImportAliasesCsv: (csvText: String) -> Unit = {},
    onRequestDeleteAccount: (currentPassword: String) -> Unit = {},
    onConfirmDeleteAccount: (otp: String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var deletingAccessKey by remember { mutableStateOf<UiAccessKey?>(null) }
    var deletingPasskey by remember { mutableStateOf<UiPasskeyCredential?>(null) }
    var totpConfirmInput by remember { mutableStateOf("") }
    var accountOtpInput by remember { mutableStateOf("") }
    var showDisableTotpDialog by remember { mutableStateOf(false) }
    var exportedCsvPreview by remember { mutableStateOf<String?>(null) }
    var showImportCsvDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val saveBackupTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && userAccount.totpBackupCodes.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val content = buildString {
                        appendLine("Mailx Privacy - 2FA Backup Recovery Codes")
                        if (userAccount.email.isNotBlank()) {
                            appendLine("Account: ${userAccount.email}")
                        }
                        appendLine("----------------------------------------")
                        userAccount.totpBackupCodes
                            .trim()
                            .split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .forEachIndexed { idx, code ->
                                appendLine("${idx + 1}. $code")
                            }
                    }
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "Backup codes saved as .txt", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Failed to save backup codes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Account, Subscription & Security",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subscription Status", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MailxPalette.ForwardEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = userAccount.subStatus.ifBlank { "Active" },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MailxPalette.ForwardEmerald,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (userAccount.subActiveUntil.isNotBlank()) {
                        Text(
                            text = "Valid Until: ${formatIsoDateShort(userAccount.subActiveUntil)}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (userAccount.email.isNotBlank()) {
                        Text(
                            text = "Primary Email: ${userAccount.email} (${if (userAccount.isActive) "Verified" else "Unverified"})",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (userAccount.id.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = "Mailx ID: ${userAccount.id}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!userAccount.isActive) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Verify Your Primary Email Address",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Enter the 6-digit OTP code sent to your primary email address to activate email forwarding:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = accountOtpInput,
                                onValueChange = { accountOtpInput = it.take(6) },
                                label = { Text("6-Digit OTP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = accountOtpInput.length == 6,
                                onClick = { onVerifyAccountOtp(accountOtpInput) }
                            ) {
                                Text("Verify")
                            }
                        }
                        TextButton(onClick = onSendAccountOtp) {
                            Text("Resend OTP Code")
                        }
                    }
                }
            }

            MailxSectionHeader(title = "Security & Credentials")

            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Change Primary Email",
                    description = userAccount.email.ifBlank { "Update account email address" },
                    startIcon = Icons.Outlined.Email,
                    iconTint = MailxPalette.ReplySky,
                    endIcon = Icons.Outlined.Edit,
                    grouped = true,
                    onClick = { showChangeEmailDialog = true }
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Change Password",
                    description = "Update your account login password",
                    startIcon = Icons.Outlined.LockReset,
                    iconTint = MailxPalette.SentIndigo,
                    endIcon = Icons.Outlined.Edit,
                    grouped = true,
                    onClick = { showChangePasswordDialog = true }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val totpColor = if (userAccount.totpEnabled) {
                                MailxPalette.ForwardEmerald
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = totpColor.copy(alpha = 0.14f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = MailxIcons.ShieldCheck,
                                        contentDescription = null,
                                        tint = totpColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "2FA Authenticator",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "TOTP",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (userAccount.totpEnabled) {
                                                    MailxPalette.ForwardEmerald
                                                } else {
                                                    MailxPalette.Slate500
                                                }
                                            )
                                    )
                                    Text(
                                        text = if (userAccount.totpEnabled) "Enabled" else "Disabled",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (userAccount.totpEnabled) {
                                            MailxPalette.ForwardEmerald
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (userAccount.totpEnabled) {
                            OutlinedButton(
                                onClick = { showDisableTotpDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MailxPalette.BlockedRose.copy(alpha = 0.5f)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Disable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MailxPalette.BlockedRose
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onInitiateTotp,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Enable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (userAccount.totpSetupSecret.isNotBlank()) {
                        val qrUri = remember(userAccount.totpSetupUri, userAccount.totpSetupSecret, userAccount.email) {
                            if (userAccount.totpSetupUri.isNotBlank()) {
                                userAccount.totpSetupUri
                            } else {
                                val label = URLEncoder.encode(userAccount.email.ifBlank { "Mailx" }, "UTF-8")
                                "otpauth://totp/Mailx:$label?secret=${userAccount.totpSetupSecret}&issuer=Mailx"
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                        Text(
                            text = "Scan QR code or copy setup key into your authenticator app:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TotpQrCodeView(
                                payload = qrUri,
                                modifier = Modifier.size(152.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SelectionContainer(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userAccount.totpSetupSecret,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        copyToClipboard(
                                            context,
                                            "TOTP Secret",
                                            userAccount.totpSetupSecret,
                                            "TOTP secret key copied"
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy Secret Key",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qrUri))
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "No authenticator app found for otpauth:// links",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Authenticator App", fontSize = 12.sp)
                        }
                        val rustTotpPreview = remember(userAccount.totpSetupSecret) {
                            runCatching {
                                val res = JSONObject(
                                    MailxRustBridge.nativeGenerateTotpToken(userAccount.totpSetupSecret, 0L)
                                )
                                res.optJSONObject("data")?.takeIf { it.optBoolean("valid", false) }
                            }.getOrNull()
                        }
                        if (rustTotpPreview != null) {
                            val liveCode = rustTotpPreview.optString("code")
                            val remSecs = rustTotpPreview.optInt("remaining_seconds", 30)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MailxPalette.ForwardedGreen.copy(alpha = 0.10f),
                                border = BorderStroke(0.75.dp, MailxPalette.ForwardedGreen.copy(alpha = 0.28f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "RUST RFC-6238 LIVE CODE (${remSecs}s)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MailxPalette.ForwardedGreen
                                        )
                                        Text(
                                            text = liveCode,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 2.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { totpConfirmInput = liveCode },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Fill Code", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = totpConfirmInput,
                                onValueChange = { totpConfirmInput = it.take(6) },
                                label = { Text("6-Digit Code") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = mailxTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = totpConfirmInput.length == 6,
                                shape = RoundedCornerShape(11.dp),
                                onClick = { onConfirmTotp(totpConfirmInput) }
                            ) {
                                Text("Confirm")
                            }
                        }
                    }

                    if (userAccount.totpBackupCodes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Backup Recovery Codes (Save in a secure place):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                SelectionContainer {
                                    Text(
                                        text = userAccount.totpBackupCodes,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            copyToClipboard(
                                                context,
                                                "Mailx 2FA Backup Codes",
                                                userAccount.totpBackupCodes,
                                                "Backup codes copied"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Codes", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            saveBackupTxtLauncher.launch("mailx-2fa-backup-codes.txt")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save .txt", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MailxSectionHeader(
                title = "Passkeys (WebAuthn)",
                countBadge = passkeys.size,
                actionText = "Add Passkey",
                onActionClick = { onRegisterPasskey(context) }
            )

            if (passkeys.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.Passkey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Passwordless Sign-In",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Register a device passkey via Android Credential Manager for biometric sign-in.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                passkeys.forEach { pk ->
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
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
                                Text("Passkey Credential", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = "ID: ${pk.id}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Created: ${formatIsoDateShort(pk.createdAt)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deletingPasskey = pk }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete Passkey",
                                    tint = MailxPalette.BlockedRose
                                )
                            }
                        }
                    }
                }
            }

            MailxSectionHeader(
                title = "Access Keys",
                countBadge = accessKeys.size,
                actionText = "New Key",
                onActionClick = { showCreateKeyDialog = true }
            )

            if (userAccount.newlyCreatedAccessKeyToken.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "New Access Key (Shown only once):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer {
                            Text(
                                text = userAccount.newlyCreatedAccessKeyToken,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        "Mailx Access Key",
                                        userAccount.newlyCreatedAccessKeyToken,
                                        "85-character access key copied"
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Copy Key")
                            }
                            TextButton(onClick = onClearNewAccessKeyToken) {
                                Text("Hide")
                            }
                        }
                    }
                }
            }

            accessKeys.forEach { keyItem ->
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
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
                            Text(keyItem.name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                            Text(
                                text = "ID: ${keyItem.id}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (keyItem.expiresAt.isNotBlank()) {
                                    "Expires: ${formatIsoDateShort(keyItem.expiresAt)}"
                                } else {
                                    "No expiration • Created: ${formatIsoDateShort(keyItem.createdAt)}"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deletingAccessKey = keyItem }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Key",
                                tint = MailxPalette.BlockedRose
                            )
                        }
                    }
                }
            }

            MailxSectionHeader(title = "Alias CSV Import & Export")

            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Export All Aliases as CSV",
                    description = "Export $aliasCount aliases with status, recipients, and descriptions",
                    startIcon = Icons.Outlined.Download,
                    iconTint = MailxPalette.BrandAccentTeal,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = {
                        onExportAliasesCsv { csvText ->
                            exportedCsvPreview = csvText
                        }
                    }
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Import Aliases from CSV",
                    description = "Import custom-domain aliases using CSV rows (alias,description,enabled,recipients)",
                    startIcon = Icons.Outlined.UploadFile,
                    iconTint = MailxPalette.ReplySky,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = { showImportCsvDialog = true }
                )
            }

            MailxSectionHeader(title = "Danger Zone")

            MailxSectionRow(
                title = "Delete Account Permanently",
                description = "Permanently delete your Mailx account and all associated aliases",
                startIcon = Icons.Outlined.DeleteForever,
                accentColor = MailxPalette.BlockedRose,
                onClick = { showDeleteAccountDialog = true }
            )
        }
    }

    if (showChangePasswordDialog) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Change Password",
            subtitle = "Enter your current password and choose a strong new password (12+ characters).",
            icon = Icons.Outlined.LockReset,
            confirmText = "Update Password",
            confirmEnabled = oldPw.isNotEmpty() && newPw.length >= 8,
            onConfirm = {
                showChangePasswordDialog = false
                onChangePassword(oldPw, newPw)
            },
            onDismiss = { showChangePasswordDialog = false }
        ) {
            OutlinedTextField(
                value = oldPw,
                onValueChange = { oldPw = it },
                label = { Text("Current Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPw,
                onValueChange = { newPw = it },
                label = { Text("New Password (At least 12 characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showChangeEmailDialog) {
        var newEm by remember { mutableStateOf(userAccount.email) }
        var curPw by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Change Primary Email",
            subtitle = "Update the primary email address associated with your account.",
            icon = Icons.Outlined.Email,
            confirmText = "Update",
            confirmEnabled = newEm.contains("@"),
            onConfirm = {
                showChangeEmailDialog = false
                onChangeEmail(newEm, curPw)
            },
            onDismiss = { showChangeEmailDialog = false }
        ) {
            OutlinedTextField(
                value = newEm,
                onValueChange = { newEm = it },
                label = { Text("New Email Address") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = curPw,
                onValueChange = { curPw = it },
                label = { Text("Current Password for Verification") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showCreateKeyDialog) {
        val expiryOptions = remember {
            listOf(
                7 to "1 week",
                30 to "1 month",
                90 to "3 months",
                180 to "6 months",
                365 to "1 year"
            )
        }
        var keyName by remember { mutableStateOf("") }
        var nameError by remember { mutableStateOf(false) }
        var selectedExpiryDays by remember { mutableIntStateOf(365) }
        var showExpiryPickerDialog by remember { mutableStateOf(false) }
        val selectedExpiryLabel = expiryOptions.firstOrNull { it.first == selectedExpiryDays }?.second ?: "1 year"

        MailxModernDialog(
            title = "New Access Key",
            subtitle = "Create a scoped API Access Key associated with your account.",
            icon = MailxIcons.KeyRound,
            confirmText = "Create Access Key",
            onConfirm = {
                if (keyName.trim().isEmpty()) {
                    nameError = true
                } else {
                    showCreateKeyDialog = false
                    onCreateAccessKey(keyName.trim(), selectedExpiryDays)
                }
            },
            onDismiss = { showCreateKeyDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = {
                        keyName = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Key Name") },
                    placeholder = { Text("e.g. Android Phone, CLI") },
                    isError = nameError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError) {
                    Text(
                        text = "Key name is required",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXPIRES AFTER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.65.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Max 1 year",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    expiryOptions.forEach { (days, label) ->
                        val isSelected = selectedExpiryDays == days
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedExpiryDays = days },
                            shape = RoundedCornerShape(9.dp),
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Surface(
                    onClick = { showExpiryPickerDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Selected duration: $selectedExpiryLabel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = "Select Expiration",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showExpiryPickerDialog) {
            MailxOptionPickerDialog(
                title = "Key Expiration",
                subtitle = "Access Keys automatically expire after the selected period.",
                options = expiryOptions,
                selectedKey = selectedExpiryDays,
                onSelect = { days ->
                    selectedExpiryDays = days
                    showExpiryPickerDialog = false
                },
                onDismiss = { showExpiryPickerDialog = false }
            )
        }
    }

    deletingAccessKey?.let { keyToDelete ->
        MailxModernDialog(
            title = "Delete Access Key",
            subtitle = "Are you sure you want to delete Access Key \"${keyToDelete.name.ifBlank { keyToDelete.id }}\"? Active sessions using this key will be terminated immediately.",
            icon = MailxIcons.Trash2,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                val targetId = keyToDelete.id
                deletingAccessKey = null
                onDeleteAccessKey(targetId)
            },
            onDismiss = { deletingAccessKey = null }
        ) {}
    }

    deletingPasskey?.let { pkToDelete ->
        MailxModernDialog(
            title = "Delete Passkey",
            subtitle = "Are you sure you want to remove passkey credential (${pkToDelete.id}) from your account?",
            icon = MailxIcons.Trash2,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                val targetId = pkToDelete.id
                deletingPasskey = null
                onDeletePasskey(targetId)
            },
            onDismiss = { deletingPasskey = null }
        ) {}
    }

    if (showDisableTotpDialog) {
        var otp by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Disable 2FA",
            subtitle = "Enter your current 6-digit authenticator code to disable TOTP.",
            icon = MailxIcons.ShieldCheck,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Disable",
            confirmEnabled = otp.length == 6,
            isDestructive = true,
            onConfirm = {
                showDisableTotpDialog = false
                onDisableTotp(otp)
            },
            onDismiss = { showDisableTotpDialog = false }
        ) {
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it.take(6) },
                label = { Text("6-Digit 2FA Code") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    exportedCsvPreview?.let { csv ->
        MailxModernDialog(
            title = "Aliases CSV Export",
            subtitle = "Copy your exported aliases CSV below:",
            icon = Icons.Outlined.Download,
            confirmText = "Copy CSV",
            onConfirm = {
                copyToClipboard(context, "Mailx Aliases CSV", csv, "CSV content copied to clipboard")
                exportedCsvPreview = null
            },
            dismissText = "Close",
            onDismiss = { exportedCsvPreview = null }
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = csv,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    if (showImportCsvDialog) {
        var csvInput by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Import Aliases from CSV",
            subtitle = "Paste CSV lines in format: alias,description,enabled,recipients",
            icon = Icons.Outlined.UploadFile,
            confirmText = "Import",
            confirmEnabled = csvInput.isNotBlank(),
            onConfirm = {
                val content = csvInput
                showImportCsvDialog = false
                onImportAliasesCsv(content)
            },
            onDismiss = { showImportCsvDialog = false }
        ) {
            OutlinedTextField(
                value = csvInput,
                onValueChange = { csvInput = it },
                placeholder = {
                    Text("alias,description,enabled,recipients\nshop@mydomain.com,Shopping,true,me@proton.me", fontSize = 11.5.sp)
                },
                minLines = 5,
                maxLines = 9,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteAccountDialog) {
        var stepPassword by remember { mutableStateOf("") }
        var confirmCode by remember { mutableStateOf("") }
        val expectedCode = userAccount.deleteAccountOtp

        MailxModernDialog(
            title = "Delete Account",
            subtitle = "WARNING: This operation cannot be undone. Deleting your account permanently removes all your aliases and data.",
            icon = Icons.Outlined.DeleteForever,
            iconTint = MailxPalette.BlockedRose,
            confirmText = if (expectedCode.isBlank()) "Request Code" else "Permanently Delete",
            confirmEnabled = if (expectedCode.isBlank()) stepPassword.isNotEmpty() else confirmCode.trim().length == 8,
            isDestructive = true,
            onConfirm = {
                if (expectedCode.isBlank()) {
                    onRequestDeleteAccount(stepPassword)
                } else {
                    showDeleteAccountDialog = false
                    onConfirmDeleteAccount(confirmCode.trim())
                }
            },
            onDismiss = { showDeleteAccountDialog = false }
        ) {
            if (expectedCode.isBlank()) {
                OutlinedTextField(
                    value = stepPassword,
                    onValueChange = { stepPassword = it },
                    label = { Text("Enter Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Enter confirmation code: $expectedCode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MailxPalette.BlockedRose
                )
                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = { confirmCode = it.take(8) },
                    label = { Text("8-Symbol Confirmation Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    logs: List<UiDiagnosticLog>,
    logIssuesEnabled: Boolean,
    serverHealthStatus: String = "",
    onCheckServerHealth: () -> Unit = {},
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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostics & Delivery Logs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRefreshLogs) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Server Health Check (/livez & /readyz)",
                    description = serverHealthStatus.ifBlank { "Tap to verify live connectivity with the Mailx API server" },
                    startIcon = Icons.Outlined.Dns,
                    iconTint = MailxPalette.ReplySky,
                    grouped = true,
                    onClick = onCheckServerHealth
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Enable Diagnostics Logging",
                    description = "Record delivery errors, bounced messages, and rule-blocked emails",
                    startIcon = Icons.Outlined.BugReport,
                    iconTint = MailxPalette.PausedAmber,
                    showSwitch = true,
                    switchChecked = logIssuesEnabled,
                    onSwitchChange = onToggleLogIssues,
                    grouped = true
                )
            }

            if (logs.isNotEmpty()) {
                Button(
                    onClick = onDeleteAllLogs,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Logs")
                }
            }

            if (logs.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Outlined.Verified,
                    title = "No diagnostic logs found",
                    subtitle = "There are no recorded delivery failures, bounces, or blocked message logs."
                )
            } else {
                logs.forEach { log ->
                    val bounceReport = remember(log.id, log.logType, log.status, log.message, log.remoteMta) {
                        runCatching {
                            val res = JSONObject(
                                MailxRustBridge.nativeAnalyzeMtaBounce(
                                    log.logType,
                                    log.status,
                                    log.message,
                                    log.remoteMta,
                                    ""
                                )
                            )
                            res.optJSONObject("data")
                        }.getOrNull()
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onFetchBounceFile(log.id) { raw ->
                                    viewingRawBounceText = raw
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = log.logType.uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MailxPalette.PausedAmber
                                    )
                                    bounceReport?.let { rep ->
                                        val isPerm = rep.optBoolean("is_permanent_failure", false)
                                        val catColor = if (isPerm) MailxPalette.BlockedRose else MailxPalette.CryptoCyan
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = catColor.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = rep.optString("category", "Diagnostic"),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = catColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = formatIsoDateShort(log.attemptedAt.ifBlank { log.createdAt }),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.from.isNotBlank() || log.destination.isNotBlank()) {
                                Text(
                                    text = "${log.from} → ${log.destination}",
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
                                    text = "Status: ${log.status} • MTA: ${log.remoteMta}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            bounceReport?.let { rep ->
                                val recAction = rep.optString("recommended_action")
                                val enhCode = rep.optString("enhanced_code")
                                if (recAction.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = buildString {
                                                if (enhCode.isNotBlank()) append("RFC-3463 [$enhCode]: ")
                                                append(recAction)
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingRawBounceText?.let { raw ->
        val rawAudit = remember(raw) {
            runCatching {
                JSONObject(MailxRustBridge.nativeAnalyzeMtaBounce("bounced", "", "", "", raw))
                    .optJSONObject("data")
            }.getOrNull()
        }
        MailxModernDialog(
            title = "Raw Bounce Log (Rust RFC-3463)",
            icon = Icons.Outlined.ReceiptLong,
            dismissText = "Close",
            onDismiss = { viewingRawBounceText = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rawAudit?.let { rep ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MailxPalette.CryptoCyan.copy(alpha = 0.11f),
                        border = BorderStroke(0.75.dp, MailxPalette.CryptoCyan.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "${rep.optString("category")} • ${rep.optString("enhanced_code").ifBlank { "SMTP" }}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MailxPalette.CryptoCyan
                            )
                            Text(
                                text = rep.optString("recommended_action"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(raw, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Service Announcements",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            if (announcements.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Outlined.Campaign,
                    title = "No announcements",
                    subtitle = "Service updates and announcements from Mailx will appear here."
                )
            } else {
                announcements.forEach { ann ->
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(ann.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(ann.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ann.link.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = ann.link,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersReportBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputUrlOrHash by remember { mutableStateOf("") }
    var decodedReport by remember { mutableStateOf<UiTrackersReport?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }

    fun decodeInput(raw: String) {
        if (raw.isBlank()) {
            decodedReport = null
            decodeError = null
            return
        }
        val res = JSONObject(MailxRustBridge.nativeDecodeTrackersReport(raw))
        if (res.optBoolean("ok", false)) {
            decodedReport = UiTrackersReport.fromJson(res.optJSONObject("data"))
            decodeError = null
        } else {
            decodedReport = null
            decodeError = res.optString("error", "Invalid report link or fragment")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Trackers Report Inspector",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            Text(
                text = "Paste a 'View report' link (or its #fragment) from any forwarded email. The report is decoded locally on your device using Rust and never contacts any server.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = inputUrlOrHash,
                onValueChange = {
                    inputUrlOrHash = it
                    decodeInput(it)
                },
                label = { Text("Trackers Report URL or #fragment") },
                placeholder = { Text("https://mailx.net/trackers-report#eyJuI...") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                            if (clip.isNotEmpty()) {
                                inputUrlOrHash = clip
                                decodeInput(clip)
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            decodeError?.let { err ->
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            decodedReport?.let { report ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Mailx removed ${report.total} ${if (report.total == 1) "tracker" else "trackers"} from this email before forwarding it.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MailxPalette.BlockedRose.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${report.highSeverityCount} High Severity",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MailxPalette.BlockedRose,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MailxPalette.PausedAmber.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${report.pixelBeaconCount} Pixel Beacons",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MailxPalette.PausedAmber,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MailxPalette.CryptoCyan.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${report.linkRedirectCount} Redirects",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MailxPalette.CryptoCyan,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                report.trackers.forEachIndexed { idx, tracker ->
                    val sevColor = when (tracker.severity.lowercase()) {
                        "high" -> MailxPalette.BlockedRose
                        "medium" -> MailxPalette.PausedAmber
                        else -> MailxPalette.CryptoCyan
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${tracker.domain}",
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sevColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${tracker.severity.uppercase()} • ${tracker.vendorName}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = sevColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Element: ${tracker.elementLabel} • Reason: ${tracker.reasonLabel} • ${tracker.category}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tracker.description.isNotBlank()) {
                                Text(
                                    text = tracker.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotpQrCodeView(
    payload: String,
    modifier: Modifier = Modifier
) {
    val qrRows = remember(payload) {
        runCatching {
            val res = JSONObject(MailxRustBridge.nativeEncodeQrMatrix(payload))
            val data = res.optJSONObject("data")
            val arr = data?.optJSONArray("rows")
            if (res.optBoolean("ok", false) && arr != null && arr.length() > 0) {
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    list.add(arr.optString(i, ""))
                }
                list
            } else {
                null
            }
        }.getOrNull()
    }

    if (!qrRows.isNullOrEmpty()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            modifier = modifier
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                val count = qrRows.size
                val cell = size.width / count.toFloat()
                for (y in 0 until count) {
                    val row = qrRows[y]
                    val cols = row.length.coerceAtMost(count)
                    for (x in 0 until cols) {
                        if (row[x] == '1') {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.5f, cell + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
