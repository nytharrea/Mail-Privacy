package net.mailx.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.model.AuthUiState
import net.mailx.android.ui.theme.MailxIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    authState: AuthUiState,
    onBaseUrlChange: (String) -> Unit,
    onLoginModeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onDismissTotp: () -> Unit = {},
    onAccessKeyChange: (String) -> Unit,
    onConnectWithCredentials: () -> Unit,
    onConnectWithPasskey: (Context) -> Unit = {},
    onConnectWithAccessKey: () -> Unit,
    onInitiatePasswordReset: (email: String) -> Unit = {},
    onConfirmPasswordReset: (otpToken: String, newPassword: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showSecret by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }

    val isOfficial = authState.baseUrl.contains("api.mailx.net") || authState.baseUrl.isBlank()
    val isCredentialsMode = authState.loginMode == "credentials"

    val cleanFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    CompositionLocalProvider(LocalAutofillHighlightColor provides Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.ShieldCheck,
                                    contentDescription = "Mailx Privacy",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Mailx Privacy",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Rust Skeleton",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Material 3 Expressive • Audited alias management, PGP forwarding & local tracker inspection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Instance & Authentication",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FilledTonalButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                    if (clipText.isNotEmpty()) {
                                        onAccessKeyChange(clipText)
                                    }
                                    onLoginModeChange("access_key")
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.ClipboardPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                selected = isCredentialsMode,
                                onClick = { onLoginModeChange("credentials") },
                                label = { Text("Email & Password", fontSize = 12.sp) }
                            )
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                selected = !isCredentialsMode,
                                onClick = { onLoginModeChange("access_key") },
                                label = { Text("Access Key", fontSize = 12.sp) }
                            )
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                selected = isOfficial,
                                onClick = { onBaseUrlChange("https://api.mailx.net") },
                                label = { Text("Official (api.mailx.net)", fontSize = 12.sp) }
                            )
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                selected = !isOfficial,
                                onClick = {
                                    if (isOfficial) {
                                        onBaseUrlChange("https://")
                                    }
                                },
                                label = { Text("Self-Hosted", fontSize = 12.sp) }
                            )
                        }

                        AnimatedVisibility(visible = !isOfficial) {
                            OutlinedTextField(
                                value = authState.baseUrl,
                                onValueChange = onBaseUrlChange,
                                label = { Text("Self-Hosted Server URL") },
                                leadingIcon = { Icon(MailxIcons.Server, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = cleanFieldColors,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isCredentialsMode) {
                            OutlinedTextField(
                                value = authState.emailInput,
                                onValueChange = onEmailChange,
                                label = { Text("Account Email") },
                                leadingIcon = { Icon(MailxIcons.Mail, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = cleanFieldColors,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = authState.passwordInput,
                                onValueChange = onPasswordChange,
                                label = { Text("Password") },
                                leadingIcon = { Icon(MailxIcons.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showSecret = !showSecret }) {
                                        Icon(
                                            imageVector = if (showSecret) MailxIcons.EyeOff else MailxIcons.Eye,
                                            contentDescription = "Toggle password"
                                        )
                                    }
                                },
                                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = cleanFieldColors,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!authState.isLoading && authState.emailInput.isNotBlank() && authState.passwordInput.isNotEmpty()) {
                                            onConnectWithCredentials()
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showResetPasswordDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Forgot Password?", fontSize = 12.sp)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = authState.accessKeyInput,
                                onValueChange = onAccessKeyChange,
                                label = { Text("Access Key") },
                                leadingIcon = { Icon(MailxIcons.KeyRound, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showSecret = !showSecret }) {
                                        Icon(
                                            imageVector = if (showSecret) MailxIcons.EyeOff else MailxIcons.Eye,
                                            contentDescription = "Toggle key"
                                        )
                                    }
                                },
                                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = cleanFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!authState.totpRequired) {
                            authState.errorMessage?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isCredentialsMode) {
                                    onConnectWithCredentials()
                                } else {
                                    onConnectWithAccessKey()
                                }
                            },
                            enabled = !authState.isLoading && (
                                if (isCredentialsMode) {
                                    authState.emailInput.isNotBlank() && authState.passwordInput.isNotEmpty()
                                } else {
                                    authState.accessKeyInput.isNotBlank()
                                }
                                ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = CircleShape
                        ) {
                            if (authState.isLoading && !authState.totpRequired) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing in...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Login", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isCredentialsMode) {
                            OutlinedButton(
                                onClick = { onConnectWithPasskey(context) },
                                enabled = !authState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Passkey,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Passkey",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = MailxIcons.Globe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = authState.baseUrl.ifBlank { "https://api.mailx.net" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Zero-knowledge memory drop",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (authState.totpRequired) {
        val otpFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            runCatching { otpFocusRequester.requestFocus() }
        }

        AlertDialog(
            onDismissRequest = {
                if (!authState.isLoading) {
                    onDismissTotp()
                }
            },
            icon = {
                Icon(
                    imageVector = MailxIcons.ShieldCheck,
                    contentDescription = "2FA Verification",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "2FA Verification",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the 6-digit code from your authenticator app to finish signing in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = authState.otpInput,
                        onValueChange = { input ->
                            onOtpChange(input.filter { it.isDigit() }.take(6))
                        },
                        label = { Text("6-Digit 2FA Code") },
                        leadingIcon = { Icon(MailxIcons.KeyRound, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipDigits = cb.primaryClip?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                        ?.filter { it.isDigit() }
                                        ?.take(6)
                                        .orEmpty()
                                    if (clipDigits.isNotEmpty()) {
                                        onOtpChange(clipDigits)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MailxIcons.ClipboardPaste,
                                    contentDescription = "Paste 2FA code"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!authState.isLoading && authState.otpInput.trim().length == 6) {
                                    onConnectWithCredentials()
                                }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(otpFocusRequester)
                    )

                    authState.errorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !authState.isLoading && authState.otpInput.trim().length == 6,
                    onClick = onConnectWithCredentials
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Verify")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !authState.isLoading,
                    onClick = onDismissTotp
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetPasswordDialog) {
        var resetStep by remember { mutableIntStateOf(0) }
        var resetEmail by remember { mutableStateOf(authState.emailInput) }
        var resetToken by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showResetPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            selected = resetStep == 0,
                            onClick = { resetStep = 0 },
                            label = { Text("1. Request Code") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            selected = resetStep == 1,
                            onClick = { resetStep = 1 },
                            label = { Text("2. Set Password") }
                        )
                    }

                    if (resetStep == 0) {
                        Text(
                            text = "Enter your account email to receive a password reset token:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Enter the 32-character reset token and choose a new password (12+ chars):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = resetToken,
                            onValueChange = { resetToken = it.trim() },
                            label = { Text("Reset Token (32 chars)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPw,
                            onValueChange = { newPw = it },
                            label = { Text("New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (resetStep == 0) {
                    Button(
                        enabled = resetEmail.contains("@"),
                        onClick = {
                            onInitiatePasswordReset(resetEmail.trim())
                            resetStep = 1
                        }
                    ) {
                        Text("Send Instructions")
                    }
                } else {
                    Button(
                        enabled = resetToken.length == 32 && newPw.length >= 12,
                        onClick = {
                            showResetPasswordDialog = false
                            onConfirmPasswordReset(resetToken, newPw)
                        }
                    ) {
                        Text("Update Password")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasswordDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
