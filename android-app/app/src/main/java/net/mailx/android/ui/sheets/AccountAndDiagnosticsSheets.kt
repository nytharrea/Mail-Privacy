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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.net.URLEncoder
import androidx.compose.foundation.clickable
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
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONObject

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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "Account, Subscription & Security",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                        Text("Subscription Status", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Surface(
                            shape = CircleShape,
                            color = MailxPalette.ForwardEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = userAccount.subStatus.ifBlank { "Active" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MailxPalette.ForwardEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (userAccount.subActiveUntil.isNotBlank()) {
                        Text(
                            text = "Valid Until: ${formatIsoDateShort(userAccount.subActiveUntil)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (userAccount.email.isNotBlank()) {
                        Text(
                            text = "Primary Email: ${userAccount.email} (${if (userAccount.isActive) "Verified" else "Unverified"})",
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

            if (!userAccount.isActive) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    ),
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

            MailxSectionRow(
                title = "Change Primary Email",
                description = userAccount.email.ifBlank { "Update account email address" },
                startIcon = Icons.Outlined.Email,
                endIcon = Icons.Outlined.Edit,
                onClick = { showChangeEmailDialog = true }
            )

            MailxSectionRow(
                title = "Change Password",
                description = "Update your account login password",
                startIcon = Icons.Outlined.LockReset,
                endIcon = Icons.Outlined.Edit,
                onClick = { showChangePasswordDialog = true }
            )

            // Compact, single-line 2FA Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(34.dp)
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
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "TOTP",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                shape = CircleShape,
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
                                shape = CircleShape,
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Authenticator App", fontSize = 12.sp)
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
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = totpConfirmInput.length == 6,
                                onClick = { onConfirmTotp(totpConfirmInput) }
                            ) {
                                Text("Confirm")
                            }
                        }
                    }

                    if (userAccount.totpBackupCodes.isNotBlank()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
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
                                        shape = CircleShape
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
                                        shape = CircleShape
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

            // Passkeys Section (Always visible with "+ Add Passkey" button)
            MailxSectionHeader(
                title = "Passkeys (WebAuthn)",
                countBadge = passkeys.size,
                actionText = "Add Passkey",
                onActionClick = { onRegisterPasskey(context) }
            )

            if (passkeys.isEmpty()) {
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
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(34.dp)
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
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Register a device passkey via Android Credential Manager for biometric sign-in.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                passkeys.forEach { pk ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                            text = "New Access Key (Shown only once):",
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
                                        "85-character access key copied"
                                    )
                                },
                                shape = CircleShape
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
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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

            MailxSectionRow(
                title = "Export All Aliases as CSV",
                description = "Export $aliasCount aliases with status, recipients, and descriptions in CSV format",
                startIcon = Icons.Outlined.Download,
                onClick = {
                    onExportAliasesCsv { csvText ->
                        exportedCsvPreview = csvText
                    }
                }
            )

            MailxSectionRow(
                title = "Import Aliases from CSV",
                description = "Import custom-domain aliases using CSV rows (alias,description,enabled,recipients)",
                startIcon = Icons.Outlined.UploadFile,
                onClick = { showImportCsvDialog = true }
            )

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
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPw,
                        onValueChange = { oldPw = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("New Password (At least 12 characters)") },
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
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChangeEmailDialog) {
        var newEm by remember { mutableStateOf(userAccount.email) }
        var curPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangeEmailDialog = false },
            title = { Text("Change Primary Email") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newEm,
                        onValueChange = { newEm = it },
                        label = { Text("New Email Address") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = curPw,
                        onValueChange = { curPw = it },
                        label = { Text("Current Password for Verification") },
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
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeEmailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

        AlertDialog(
            onDismissRequest = { showCreateKeyDialog = false },
            title = {
                Text(
                    text = "New Access Key",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Create a new Access Key associated with your account.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = keyName,
                            onValueChange = {
                                keyName = it
                                if (it.isNotBlank()) nameError = false
                            },
                            label = { Text("Name") },
                            placeholder = { Text("e.g. Android Phone") },
                            isError = nameError,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (nameError) {
                            Text(
                                text = "Required",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Expires After:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            onClick = { showExpiryPickerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedExpiryLabel,
                                    fontSize = 14.sp,
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
                        Text(
                            text = "Access Keys expire after one year at most.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        if (keyName.trim().isEmpty()) {
                            nameError = true
                        } else {
                            showCreateKeyDialog = false
                            onCreateAccessKey(keyName.trim(), selectedExpiryDays)
                        }
                    }
                ) {
                    Text("Create Access Key", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        if (showExpiryPickerDialog) {
            AlertDialog(
                onDismissRequest = { showExpiryPickerDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        expiryOptions.forEachIndexed { idx, (days, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedExpiryDays = days
                                        showExpiryPickerDialog = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                RadioButton(
                                    selected = selectedExpiryDays == days,
                                    onClick = {
                                        selectedExpiryDays = days
                                        showExpiryPickerDialog = false
                                    }
                                )
                            }
                            if (idx < expiryOptions.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }

    deletingAccessKey?.let { keyToDelete ->
        AlertDialog(
            onDismissRequest = { deletingAccessKey = null },
            icon = {
                Icon(
                    imageVector = MailxIcons.Trash2,
                    contentDescription = null,
                    tint = MailxPalette.BlockedRose
                )
            },
            title = {
                Text(
                    text = "Delete Access Key",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete Access Key \"${keyToDelete.name.ifBlank { keyToDelete.id }}\"? Any active sessions using this key will be terminated immediately."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        val targetId = keyToDelete.id
                        deletingAccessKey = null
                        onDeleteAccessKey(targetId)
                    }
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccessKey = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingPasskey?.let { pkToDelete ->
        AlertDialog(
            onDismissRequest = { deletingPasskey = null },
            icon = {
                Icon(
                    imageVector = MailxIcons.Trash2,
                    contentDescription = null,
                    tint = MailxPalette.BlockedRose
                )
            },
            title = {
                Text(
                    text = "Delete Passkey",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove passkey credential (${pkToDelete.id}) from your account?"
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        val targetId = pkToDelete.id
                        deletingPasskey = null
                        onDeletePasskey(targetId)
                    }
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPasskey = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDisableTotpDialog) {
        var otp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableTotpDialog = false },
            title = { Text("Disable 2FA") },
            text = {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.take(6) },
                    label = { Text("6-Digit 2FA Code") },
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
                    Text("Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableTotpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    exportedCsvPreview?.let { csv ->
        AlertDialog(
            onDismissRequest = { exportedCsvPreview = null },
            title = { Text("Aliases CSV Export") },
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
                        copyToClipboard(context, "Mailx Aliases CSV", csv, "CSV content copied to clipboard")
                        exportedCsvPreview = null
                    }
                ) {
                    Text("Copy CSV to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedCsvPreview = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showImportCsvDialog) {
        var csvInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportCsvDialog = false },
            title = { Text("Import Aliases from CSV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste CSV lines in format:\nalias,description,enabled,recipients",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = csvInput,
                        onValueChange = { csvInput = it },
                        placeholder = {
                            Text("alias,description,enabled,recipients\nshop@mydomain.com,Shopping,true,me@proton.me")
                        },
                        minLines = 5,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = csvInput.isNotBlank(),
                    onClick = {
                        val content = csvInput
                        showImportCsvDialog = false
                        onImportAliasesCsv(content)
                    }
                ) {
                    Text("Import Aliases")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportCsvDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        var stepPassword by remember { mutableStateOf("") }
        var confirmCode by remember { mutableStateOf("") }
        val expectedCode = userAccount.deleteAccountOtp

        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "WARNING: This operation cannot be undone. Deleting your account permanently removes all your aliases and data.",
                        fontSize = 13.sp,
                        color = MailxPalette.BlockedRose
                    )
                    if (expectedCode.isBlank()) {
                        OutlinedTextField(
                            value = stepPassword,
                            onValueChange = { stepPassword = it },
                            label = { Text("Enter Current Password to Request Code") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Enter the following 8-character confirmation code to permanently delete your account: $expectedCode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = confirmCode,
                            onValueChange = { confirmCode = it.take(8) },
                            label = { Text("8-Symbol Confirmation Code") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (expectedCode.isBlank()) {
                    Button(
                        enabled = stepPassword.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MailxPalette.BlockedRose,
                            contentColor = Color.White
                        ),
                        onClick = { onRequestDeleteAccount(stepPassword) }
                    ) {
                        Text("Request Deletion Code")
                    }
                } else {
                    Button(
                        enabled = confirmCode.trim().length == 8,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MailxPalette.BlockedRose,
                            contentColor = Color.White
                        ),
                        onClick = {
                            showDeleteAccountDialog = false
                            onConfirmDeleteAccount(confirmCode.trim())
                        }
                    ) {
                        Text("Permanently Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostics & Delivery Logs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onRefreshLogs) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MailxSectionRow(
                title = "Server Health Check (/livez & /readyz)",
                description = serverHealthStatus.ifBlank { "Tap to verify live connectivity with the Mailx API server" },
                startIcon = Icons.Outlined.Dns,
                onClick = onCheckServerHealth
            )

            MailxSectionRow(
                title = "Enable Diagnostics Logging",
                description = "Record delivery errors, bounced messages, and rule-blocked emails",
                startIcon = Icons.Outlined.BugReport,
                showSwitch = true,
                switchChecked = logIssuesEnabled,
                onSwitchChange = onToggleLogIssues
            )

            if (logs.isNotEmpty()) {
                Button(
                    onClick = onDeleteAllLogs,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
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
                                    color = MailxPalette.PausedAmber
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
                                    text = "Status: ${log.status} • MTA: ${log.remoteMta}",
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
            title = { Text("Raw Log Details (Bounce Log)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(raw, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingRawBounceText = null }) {
                    Text("Close")
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "Announcements",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (announcements.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Outlined.Campaign,
                    title = "No announcements",
                    subtitle = "Service updates and announcements from Mailx will appear here."
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "Trackers Report Inspector",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = "Paste a 'View report' link (or its #fragment) from any forwarded email. The report is decoded locally on your device using Rust and never contacts any server.",
                fontSize = 13.sp,
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
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mailx removed ${report.total} ${if (report.total == 1) "tracker" else "trackers"} from this email before forwarding it.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                report.trackers.forEachIndexed { idx, tracker ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${idx + 1}. ${tracker.domain}",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Element: ${tracker.elementLabel} • Reason: ${tracker.reasonLabel}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
