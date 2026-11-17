package net.mailx.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.mailx.android.ui.model.AuthUiState
import net.mailx.android.ui.theme.AddyPalette
import kotlin.random.Random

/**
 * `anonaddy/addy-android` içindeki `activity_setup.xml` (`SetupActivity.kt`) ve
 * `bottomsheet_api.xml` (`AddApiBottomDialogFragment.kt`) tasarımlarının birebir uyarlaması.
 * Hem E-posta/Parola (+ 2FA TOTP) hem de 85 karakterlik Access Key ile girişi destekler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    authState: AuthUiState,
    onBaseUrlChange: (String) -> Unit,
    onLoginModeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onAccessKeyChange: (String) -> Unit,
    onConnectWithCredentials: () -> Unit,
    onConnectWithAccessKey: () -> Unit
) {
    val context = LocalContext.current
    var showLoginSheet by remember { mutableStateOf(false) }

    LaunchedEffect(authState.errorMessage, authState.totpRequired) {
        if (authState.errorMessage != null || authState.totpRequired) {
            showLoginSheet = true
        }
    }

    var backgroundApiWatermark by remember {
        mutableStateOf(
            buildString {
                val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                repeat(220) { append(chars[Random.nextInt(chars.length)]) }
            }
        )
    }

    LaunchedEffect(Unit) {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        while (true) {
            delay(Random.nextLong(350, 1100))
            val sb = StringBuilder(backgroundApiWatermark)
            val idx = Random.nextInt(sb.length)
            sb.setCharAt(idx, chars[Random.nextInt(chars.length)])
            backgroundApiWatermark = sb.toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AddyPalette.PrimaryColorStatic)
    ) {
        Text(
            text = backgroundApiWatermark,
            fontSize = 84.sp,
            lineHeight = 90.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.05f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WavingHand,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Merhaba!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Hoş geldiniz",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AddyPalette.PrimaryInnerStatic,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailUnread,
                                contentDescription = "Mailx",
                                tint = AddyPalette.LauncherForeground,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Text(
                        text = "mailx",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                }

                Text(
                    text = "Anonim E-posta Yönlendirme",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                        if (clipText.length == 85 && authState.accessKeyInput.isBlank()) {
                            onAccessKeyChange(clipText)
                            onLoginModeChange("access_key")
                        }
                        showLoginSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.TertiaryDark,
                        contentColor = AddyPalette.OnTertiaryDark
                    )
                ) {
                    Text(
                        text = "Başla",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                            if (clipText.isNotEmpty()) {
                                onAccessKeyChange(clipText)
                            }
                            onLoginModeChange("access_key")
                            showLoginSheet = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AddyPalette.PrimaryDark,
                            contentColor = AddyPalette.OnPrimaryDark
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "Anahtar Yapıştır",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Button(
                        onClick = {
                            onBaseUrlChange("https://")
                            showLoginSheet = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AddyPalette.PrimaryDark,
                            contentColor = AddyPalette.OnPrimaryDark
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "Özel Sunucu",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showLoginSheet) {
        AddyApiLoginBottomSheet(
            authState = authState,
            onBaseUrlChange = onBaseUrlChange,
            onLoginModeChange = onLoginModeChange,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onOtpChange = onOtpChange,
            onAccessKeyChange = onAccessKeyChange,
            onConnectWithCredentials = onConnectWithCredentials,
            onConnectWithAccessKey = onConnectWithAccessKey,
            onDismiss = { if (!authState.isLoading) showLoginSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddyApiLoginBottomSheet(
    authState: AuthUiState,
    onBaseUrlChange: (String) -> Unit,
    onLoginModeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onAccessKeyChange: (String) -> Unit,
    onConnectWithCredentials: () -> Unit,
    onConnectWithAccessKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSecret by remember { mutableStateOf(false) }
    val isOfficial = authState.baseUrl.contains("api.mailx.net") || authState.baseUrl.isBlank()
    val isCredentialsMode = authState.loginMode == "credentials"

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
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Giriş Yap",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Giriş yöntemi seçici: E-posta & Parola vs Erişim Anahtarı (Access Key)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        selected = isCredentialsMode,
                        onClick = { onLoginModeChange("credentials") },
                        label = { Text("E-posta & Parola") }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        selected = !isCredentialsMode,
                        onClick = { onLoginModeChange("access_key") },
                        label = { Text("Erişim Anahtarı") }
                    )
                }

                // Sunucu seçici (Resmi Sunucu / Özel Sunucu)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        selected = isOfficial,
                        onClick = { onBaseUrlChange("https://api.mailx.net") },
                        label = { Text("Resmi Sunucu") }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        selected = !isOfficial,
                        onClick = {
                            if (isOfficial) {
                                onBaseUrlChange("https://")
                            }
                        },
                        label = { Text("Özel Sunucu") }
                    )
                }

                AnimatedVisibility(visible = !isOfficial) {
                    OutlinedTextField(
                        value = authState.baseUrl,
                        onValueChange = onBaseUrlChange,
                        label = { Text("Mailx Sunucu Adresi") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isCredentialsMode) {
                    OutlinedTextField(
                        value = authState.emailInput,
                        onValueChange = onEmailChange,
                        label = { Text("E-posta Adresi") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = authState.passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text("Parola") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Göster / Gizle"
                                )
                            }
                        },
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = authState.totpRequired || authState.otpInput.isNotEmpty()) {
                        OutlinedTextField(
                            value = authState.otpInput,
                            onValueChange = onOtpChange,
                            label = { Text("2FA Doğrulama Kodu (6 Haneli TOTP)") },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = authState.accessKeyInput,
                        onValueChange = onAccessKeyChange,
                        label = { Text("Erişim Anahtarı (Access Key)") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                    if (clipText.isNotEmpty()) onAccessKeyChange(clipText)
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Yapıştır")
                                }
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Göster / Gizle"
                                    )
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                text = "${authState.accessKeyInput.trim().length} / 85 karakter",
                                fontSize = 12.sp
                            )
                        },
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                authState.errorMessage?.let { err ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
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
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Giriş yapılıyor...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Giriş Yap", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
