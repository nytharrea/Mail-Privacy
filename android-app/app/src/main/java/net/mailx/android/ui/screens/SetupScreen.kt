package net.mailx.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.mailxTextFieldColors
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

    val cleanFieldColors = mailxTextFieldColors()

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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Focal Brand Header Card with subtle Teal border accent
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    ),
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
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.ShieldCheck,
                                    contentDescription = "Mailx Privacy",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
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
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Rust Core",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Audited alias management, PGP forwarding & local tracker inspection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Authentication Form Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
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
                                fontWeight = FontWeight.SemiBold,
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
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.ClipboardPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Key", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        MailxSegmentedControl(
                            options = listOf("Email & Password", "Access Key"),
                            selectedIndex = if (isCredentialsMode) 0 else 1,
                            onSelect = { idx ->
                                onLoginModeChange(if (idx == 0) "credentials" else "access_key")
                            }
                        )

                        MailxSegmentedControl(
                            options = listOf("Official (api.mailx.net)", "Self-Hosted"),
                            selectedIndex = if (isOfficial) 0 else 1,
                            onSelect = { idx ->
                                if (idx == 0) {
                                    onBaseUrlChange("https://api.mailx.net")
                                } else if (isOfficial) {
                                    onBaseUrlChange("https://")
                                }
                            },
                            compact = true
                        )

                        AnimatedVisibility(visible = !isOfficial) {
                            OutlinedTextField(
                                value = authState.baseUrl,
                                onValueChange = onBaseUrlChange,
                                label = { Text("Self-Hosted Server URL") },
                                leadingIcon = { Icon(MailxIcons.Server, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
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
                                shape = RoundedCornerShape(12.dp),
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
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanFieldColors,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!authState.isLoading) {
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
                                    Text(
                                        text = "Forgot Password?",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!authState.totpRequired) {
                            authState.errorMessage?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
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

                        // Brand Teal primary button
                        Button(
                            onClick = {
                                if (isCredentialsMode) {
                                    onConnectWithCredentials()
                                } else {
                                    onConnectWithAccessKey()
                                }
                            },
                            enabled = !authState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (authState.isLoading && !authState.totpRequired) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing in...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Sign In", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (isCredentialsMode) {
                            FilledTonalButton(
                                onClick = { onConnectWithPasskey(context) },
                                enabled = !authState.isLoading,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Passkey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Passkey",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
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
                            fontWeight = FontWeight.Medium,
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

        MailxModernDialog(
            title = "2FA Verification",
            subtitle = "Enter the 6-digit code from your authenticator app to finish signing in.",
            icon = MailxIcons.ShieldCheck,
            confirmText = if (authState.isLoading) "Verifying..." else "Verify",
            confirmEnabled = !authState.isLoading && authState.otpInput.trim().length == 6,
            onConfirm = onConnectWithCredentials,
            onDismiss = {
                if (!authState.isLoading) {
                    onDismissTotp()
                }
            }
        ) {
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
                shape = RoundedCornerShape(12.dp),
                colors = cleanFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(otpFocusRequester)
            )

            authState.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
    }

    if (showResetPasswordDialog) {
        var resetStep by remember { mutableIntStateOf(0) }
        var resetEmail by remember { mutableStateOf(authState.emailInput) }
        var resetToken by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }

        MailxModernDialog(
            title = "Reset Password",
            subtitle = if (resetStep == 0) {
                "Enter your account email to receive a password reset token."
            } else {
                "Enter the 32-character reset token and choose a new password (12+ chars)."
            },
            icon = MailxIcons.Lock,
            confirmText = if (resetStep == 0) "Send Code" else "Update Password",
            confirmEnabled = if (resetStep == 0) resetEmail.contains("@") else (resetToken.length == 32 && newPw.length >= 12),
            onConfirm = {
                if (resetStep == 0) {
                    onInitiatePasswordReset(resetEmail.trim())
                    resetStep = 1
                } else {
                    showResetPasswordDialog = false
                    onConfirmPasswordReset(resetToken, newPw)
                }
            },
            dismissText = "Close",
            onDismiss = { showResetPasswordDialog = false }
        ) {
            MailxSegmentedControl(
                options = listOf("1. Request Code", "2. Set Password"),
                selectedIndex = resetStep,
                onSelect = { resetStep = it },
                compact = true
            )

            if (resetStep == 0) {
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = cleanFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = resetToken,
                    onValueChange = { resetToken = it.trim() },
                    label = { Text("Reset Token (32 chars)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = cleanFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = cleanFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
