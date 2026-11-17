package net.mailx.android.ui.sheets

import android.content.ClipboardManager
import android.content.Context
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
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
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
    var totpConfirmInput by remember { mutableStateOf("") }
    var accountOtpInput by remember { mutableStateOf("") }
    var showDisableTotpDialog by remember { mutableStateOf(false) }
    var exportedCsvPreview by remember { mutableStateOf<String?>(null) }
    var showImportCsvDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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

            MailxSectionHeader(title = "Credentials & Two-Factor Authentication")

            MailxSectionRow(
                title = "Change Primary Email",
                description = userAccount.email.ifBlank { "Update account email address" },
                startIcon = Icons.Default.Email,
                endIcon = Icons.Default.Edit,
                onClick = { showChangeEmailDialog = true }
            )

            MailxSectionRow(
                title = "Change Password",
                description = "Update your account login password",
                startIcon = Icons.Default.LockReset,
                endIcon = Icons.Default.Edit,
                onClick = { showChangePasswordDialog = true }
            )

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
                            Text("Two-Factor Authentication (2FA TOTP)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = if (userAccount.totpEnabled) "Status: Enabled" else "Status: Disabled",
                                fontSize = 13.sp,
                                color = if (userAccount.totpEnabled) MailxPalette.ForwardEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (userAccount.totpEnabled) {
                            OutlinedButton(
                                onClick = { showDisableTotpDialog = true },
                                shape = CircleShape
                            ) {
                                Text("Disable", color = MailxPalette.BlockedRose)
                            }
                        } else {
                            Button(
                                onClick = onInitiateTotp,
                                shape = CircleShape
                            ) {
                                Text("Enable 2FA")
                            }
                        }
                    }

                    if (userAccount.totpSetupSecret.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = "Add the following TOTP secret to your authenticator app (Aegis, Ente Auth, etc.) and enter the 6-digit code:",
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
                                label = { Text("6-Digit TOTP Code") },
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
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Backup Recovery Code (Save in a secure place):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

            if (passkeys.isNotEmpty()) {
                MailxSectionHeader(
                    title = "Passkeys",
                    countBadge = passkeys.size
                )

                passkeys.forEach { pk ->
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
                            IconButton(onClick = { onDeletePasskey(pk.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
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
                                    "Expires: ${formatIsoDateShort(keyItem.expiresAt)}"
                                } else {
                                    "No expiration • Created: ${formatIsoDateShort(keyItem.createdAt)}"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteAccessKey(keyItem.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
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
                startIcon = Icons.Default.Download,
                onClick = {
                    onExportAliasesCsv { csvText ->
                        exportedCsvPreview = csvText
                    }
                }
            )

            MailxSectionRow(
                title = "Import Aliases from CSV",
                description = "Import custom-domain aliases using CSV rows (alias,description,enabled,recipients)",
                startIcon = Icons.Default.UploadFile,
                onClick = { showImportCsvDialog = true }
            )

            MailxSectionHeader(title = "Danger Zone")

            MailxSectionRow(
                title = "Delete Account Permanently",
                description = "Permanently delete your Mailx account and all associated aliases",
                startIcon = Icons.Default.DeleteForever,
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
        var keyName by remember { mutableStateOf("") }
        var expiryDays by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { showCreateKeyDialog = false },
            title = { Text("Create New Access Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text("Key Name (e.g. Android Phone)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expiryDays,
                        onValueChange = { expiryDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Expiration Days (leave blank for no expiry)") },
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
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDisableTotpDialog) {
        var otp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableTotpDialog = false },
            title = { Text("Disable Two-Factor Authentication") },
            text = {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.take(6) },
                    label = { Text("6-Digit TOTP Code") },
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MailxSectionRow(
                title = "Server Health Check (/livez & /readyz)",
                description = serverHealthStatus.ifBlank { "Tap to verify live connectivity with the Mailx API server" },
                startIcon = Icons.Default.Dns,
                onClick = onCheckServerHealth
            )

            MailxSectionRow(
                title = "Enable Diagnostics Logging",
                description = "Record delivery errors, bounced messages, and rule-blocked emails",
                startIcon = Icons.Default.BugReport,
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
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Logs")
                }
            }

            if (logs.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Default.Verified,
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    icon = Icons.Default.Campaign,
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
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
