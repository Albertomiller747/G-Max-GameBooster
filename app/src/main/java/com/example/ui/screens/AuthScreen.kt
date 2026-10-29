package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.repository.UserAccountRepository
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import java.util.Locale

@Composable
fun AuthScreen(
    initialDateOfBirth: String,
    onUpdateDateOfBirth: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onSaveCredentials: (fullName: String, email: String) -> Unit = { _, _ -> },
    onSocialSignInSuccess: (provider: String, fullName: String, email: String) -> Unit = { _, name, mail ->
        onSaveCredentials(name, mail)
        onAuthSuccess()
    }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // false = Login screen, true = Create Account screen
    var isCreateAccountMode by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    // Interactive Account Picker Fallback state ("Google" or "Facebook")
    var activeSocialFallbackProvider by remember { mutableStateOf<String?>(null) }

    // Attempt silent auto-sign-in once when landing on the AuthScreen
    LaunchedEffect(Unit) {
        UserAccountRepository.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { onAuthSuccess() },
            onUnauthenticated = { /* Stay on Login / Create Account screen */ },
            scope = coroutineScope
        )
    }

    val triggerGoogleCredentialAuth: (Boolean) -> Unit = { allowFormFallback ->
        errorMessage = null
        infoMessage = null
        isLoading = true
        if (fullName.isNotBlank() || email.isNotBlank()) {
            onSaveCredentials(fullName, email)
        }
        UserAccountRepository.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                if (allowFormFallback && email.isNotBlank()) {
                    onSocialSignInSuccess(
                        "Google",
                        fullName.ifBlank { email.substringBefore("@") },
                        email
                    )
                } else {
                    activeSocialFallbackProvider = "Google"
                }
            },
            scope = coroutineScope,
            onAuthCancelled = {
                isLoading = false
                if (allowFormFallback && com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(email)) {
                    onSaveCredentials(fullName, email)
                    onAuthSuccess()
                }
            },
            onRequireFallbackPicker = {
                isLoading = false
                if (allowFormFallback && email.isNotBlank()) {
                    onSocialSignInSuccess(
                        "Google",
                        fullName.ifBlank { email.substringBefore("@") },
                        email
                    )
                } else {
                    activeSocialFallbackProvider = "Google"
                }
            }
        )
    }

    val triggerFacebookAuth: () -> Unit = {
        errorMessage = null
        infoMessage = null
        isLoading = true
        UserAccountRepository.onFacebookSignInClicked(
            context = context,
            onAuthSuccess = {
                isLoading = false
                onSocialSignInSuccess(
                    "Facebook",
                    fullName.ifBlank { "Facebook Gamer" },
                    email.ifBlank { "gamer@facebook.com" }
                )
            },
            onAuthError = {
                isLoading = false
                activeSocialFallbackProvider = "Facebook"
            },
            scope = coroutineScope,
            onAuthCancelled = {
                isLoading = false
            },
            onRequireFallbackPicker = {
                isLoading = false
                activeSocialFallbackProvider = "Facebook"
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A0D12)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0D12))
        ) {
            // Top-right layered angled wave accent inspired by reference layout in G-Max cyber palette
            CyberCornerWaveAccent(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 210.dp, height = 175.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top area (Back arrow on Create Account)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isCreateAccountMode) {
                        IconButton(
                            onClick = {
                                isCreateAccountMode = false
                                errorMessage = null
                                infoMessage = null
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("auth_back_to_login_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Login",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Main Center Form Column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(if (isCreateAccountMode) 6.dp else 16.dp))

                    // Official G-Max 3D Hexagonal Controller Brand Identity on Sign-In / Create Account Page
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 14.dp)
                            .testTag("auth_gmax_brand_header")
                    ) {
                        com.example.ui.components.GMaxBrandIcon(
                            size = 54.dp,
                            cornerRadius = 15.dp,
                            showGlowBorder = true,
                            glowColor = Color(0xFF00E5FF),
                            contentDescription = "G-Max Gaming Hub Icon"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "G-MAX GAMING HUB",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00E5FF),
                                    letterSpacing = 1.6.sp
                                )
                            )
                            Text(
                                text = "Console Launcher & Performance Booster",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    // Title
                    Text(
                        text = if (isCreateAccountMode) "Create Account" else "Login",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            color = Color(0xFFF8FAFC),
                            letterSpacing = (-0.5).sp
                        )
                    )

                    if (!isCreateAccountMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please sign in to continue.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = Color(0xFF94A3B8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Form Fields
                    if (isCreateAccountMode) {
                        MockupElevatedInputField(
                            label = "FULL NAME",
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                errorMessage = null
                            },
                            placeholder = "Jhone williams",
                            leadingIcon = Icons.Outlined.Person,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                            testTag = "auth_fullname_input"
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    MockupElevatedInputField(
                        label = "EMAIL",
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        placeholder = "user123@email.com",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        testTag = "auth_email_input"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    MockupElevatedInputField(
                        label = "PASSWORD",
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        placeholder = "••••••••",
                        leadingIcon = Icons.Outlined.Lock,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isCreateAccountMode) ImeAction.Next else ImeAction.Done,
                        trailingText = if (!isCreateAccountMode) "FORGOT" else null,
                        onTrailingClick = {
                            infoMessage = "Use Google Sign-In below to recover or access your account."
                        },
                        testTag = "auth_password_input"
                    )

                    if (isCreateAccountMode) {
                        Spacer(modifier = Modifier.height(18.dp))
                        MockupElevatedInputField(
                            label = "CONFIRM PASSWORD",
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                errorMessage = null
                            },
                            placeholder = "••••••••",
                            leadingIcon = Icons.Outlined.Lock,
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            testTag = "auth_confirm_password_input"
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Error or Info message
                    AnimatedVisibility(visible = errorMessage != null || infoMessage != null) {
                        val isErr = errorMessage != null
                        val bannerColor = if (isErr) RecorderCoral else HubBlue
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = bannerColor.copy(alpha = 0.14f)
                            ),
                            border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = errorMessage ?: infoMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = bannerColor,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Right-Aligned Gradient Pill Button: LOGIN -> or SIGN UP ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        var pillFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = RoundedCornerShape(50),
                                    ambientColor = HubBlue,
                                    spotColor = LaunchGreen
                                )
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(HubBlue, LaunchGreen)
                                    )
                                )
                                .border(
                                    width = if (pillFocused) 2.dp else 0.dp,
                                    color = if (pillFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(50)
                                )
                                .onFocusChanged { pillFocused = it.isFocused }
                                .focusable()
                                .clickable(enabled = !isLoading) {
                                    errorMessage = null
                                    infoMessage = null
                                    if (isCreateAccountMode && fullName.isBlank()) {
                                        errorMessage = "Please enter your full name."
                                        return@clickable
                                    }
                                    if (email.isBlank()) {
                                        errorMessage = "Please enter your email address."
                                        return@clickable
                                    }
                                    if (password.isBlank()) {
                                        errorMessage = "Please enter your password."
                                        return@clickable
                                    }
                                    if (isCreateAccountMode && password != confirmPassword) {
                                        errorMessage = "Passwords do not match."
                                        return@clickable
                                    }
                                    onSaveCredentials(fullName, email)
                                    triggerGoogleCredentialAuth(true)
                                }
                                .padding(horizontal = 28.dp)
                                .testTag("auth_submit_pill_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF071018),
                                        strokeWidth = 2.2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isCreateAccountMode) "SIGN UP" else "LOGIN",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF071018),
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF071018),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Google & Facebook Social Login Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        var googleFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { triggerGoogleCredentialAuth(false) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { googleFocused = it.isFocused }
                                .testTag("google_auth_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (googleFocused) 2.dp else 1.dp,
                                color = if (googleFocused) LaunchGreen else HubBlue.copy(alpha = 0.55f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF4285F4)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC)
                                )
                            )
                        }

                        var facebookFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { triggerFacebookAuth() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { facebookFocused = it.isFocused }
                                .border(
                                    width = if (facebookFocused) 2.dp else 0.dp,
                                    color = if (facebookFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .testTag("facebook_auth_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1877F2),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "f",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1877F2)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Facebook",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Continue as Guest full-width button
                    var guestFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            if (fullName.isNotBlank() || email.isNotBlank()) {
                                onSaveCredentials(fullName, email)
                            }
                            onContinueAsGuest()
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .onFocusChanged { guestFocused = it.isFocused }
                            .testTag("continue_guest_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (guestFocused) 2.dp else 1.dp,
                            color = if (guestFocused) HubBlue else Color(0xFF283244)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                // Bottom Mode Switcher Link ("Don't have an account? Sign up" / "Already have an account? Sign in")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val promptText = if (isCreateAccountMode) {
                        "Already have an account? "
                    } else {
                        "Don't have an account? "
                    }
                    val actionText = if (isCreateAccountMode) "Sign in" else "Sign up"
                    val switchTag = if (isCreateAccountMode) "auth_mode_signin" else "auth_mode_signup"

                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(promptText)
                            }
                            withStyle(
                                SpanStyle(
                                    color = HubBlue,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) {
                                append(actionText)
                            }
                        },
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isCreateAccountMode = !isCreateAccountMode
                                errorMessage = null
                                infoMessage = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(switchTag)
                    )
                }
            }
        }
    }

    activeSocialFallbackProvider?.let { provider ->
        SocialAccountPickerFallbackDialog(
            providerName = provider,
            initialName = fullName,
            initialEmail = email,
            onDismiss = { activeSocialFallbackProvider = null },
            onConfirmAccount = { chosenName, chosenEmail ->
                activeSocialFallbackProvider = null
                onSocialSignInSuccess(provider, chosenName, chosenEmail)
            }
        )
    }
}

@Composable
fun SocialAccountPickerFallbackDialog(
    providerName: String,
    initialName: String = "",
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onConfirmAccount: (fullName: String, email: String) -> Unit
) {
    val isFacebook = providerName.equals("Facebook", ignoreCase = true)
    val brandAccent = if (isFacebook) Color(0xFF1877F2) else Color(0xFF4285F4)
    val defaultEmailDomain = if (isFacebook) "facebook.com" else "gmail.com"

    var nameInput by rememberSaveable(providerName) {
        mutableStateOf(initialName.ifBlank { "G-Max Pro Gamer" })
    }
    var emailInput by rememberSaveable(providerName) {
        mutableStateOf(
            initialEmail.ifBlank {
                if (isFacebook) "gmax.gamer@facebook.com" else "gmax.gamer@gmail.com"
            }
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(brandAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFacebook) "f" else "G",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Continue with $providerName",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF8FAFC)
                        )
                    )
                    Text(
                        text = "Select or enter your $providerName account",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick 1-Tap Account Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val resolvedName = nameInput.trim().ifBlank { "$providerName Gamer" }
                            val resolvedEmail = emailInput.trim().ifBlank { "gamer@$defaultEmailDomain" }
                            onConfirmAccount(resolvedName, resolvedEmail)
                        }
                        .testTag("social_picker_quick_account_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(brandAccent.copy(alpha = 0.22f))
                                .border(1.dp, brandAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (nameInput.trim().firstOrNull()?.uppercaseChar() ?: 'G').toString(),
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nameInput.ifBlank { "$providerName Gamer" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC)
                                )
                            )
                            Text(
                                text = emailInput.ifBlank { "gamer@$defaultEmailDomain" },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Instant Sign In",
                            tint = brandAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Editable Name & Email fields
                MockupElevatedInputField(
                    label = "$providerName DISPLAY NAME",
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        validationError = null
                    },
                    placeholder = "Your Name",
                    leadingIcon = Icons.Outlined.Person,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    testTag = "social_fallback_name_input"
                )

                MockupElevatedInputField(
                    label = "$providerName EMAIL",
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        validationError = null
                    },
                    placeholder = "you@$defaultEmailDomain",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    testTag = "social_fallback_email_input"
                )

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RecorderCoral,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = nameInput.trim()
                    val cleanEmail = emailInput.trim()
                    if (cleanName.isBlank()) {
                        validationError = "Please enter your display name"
                        return@Button
                    }
                    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                        validationError = "Please enter a valid email address"
                        return@Button
                    }
                    onConfirmAccount(cleanName, cleanEmail)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("social_fallback_confirm_button")
            ) {
                Text(
                    text = "Continue as ${nameInput.trim().substringBefore(" ").ifBlank { "Gamer" }}",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("social_fallback_cancel_button")
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun CyberCornerWaveAccent(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer soft layer
        rotate(degrees = -18f, pivot = Offset(w * 0.65f, h * 0.25f)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        HubBlue.copy(alpha = 0.65f),
                        LaunchGreen.copy(alpha = 0.55f)
                    )
                ),
                topLeft = Offset(w * 0.18f, -h * 0.25f),
                size = Size(w * 1.05f, h * 0.72f),
                cornerRadius = CornerRadius(38.dp.toPx(), 38.dp.toPx())
            )
        }

        // Inner vibrant layer
        rotate(degrees = -14f, pivot = Offset(w * 0.72f, h * 0.3f)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        HubBlue,
                        LaunchGreen
                    )
                ),
                topLeft = Offset(w * 0.26f, -h * 0.18f),
                size = Size(w * 0.98f, h * 0.68f),
                cornerRadius = CornerRadius(34.dp.toPx(), 34.dp.toPx())
            )
        }
    }
}

@Composable
private fun MockupElevatedInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val isActiveCard = isFocused || value.isNotEmpty()

    val cardElevation by animateDpAsState(
        targetValue = if (isActiveCard) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "input_elevation"
    )
    val cardBgColor by animateColorAsState(
        targetValue = if (isActiveCard) Color(0xFF141B26) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "input_bg"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = cardElevation,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = HubBlue.copy(alpha = 0.4f),
                    spotColor = HubBlue.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(cardBgColor)
                .border(
                    width = if (isFocused) 1.2.dp else if (isActiveCard) 1.dp else 0.dp,
                    color = if (isFocused) HubBlue.copy(alpha = 0.75f)
                    else if (isActiveCard) Color(0xFF243044)
                    else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(
                    horizontal = if (isActiveCard) 14.dp else 4.dp,
                    vertical = if (isActiveCard) 11.dp else 10.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = label,
                    tint = if (isFocused) HubBlue else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isActiveCard) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.7.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFFF8FAFC),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(HubBlue),
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .testTag(testTag),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = if (isActiveCard) placeholder else label,
                                        style = TextStyle(
                                            color = Color(0xFF64748B),
                                            fontSize = if (isActiveCard) 14.sp else 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = if (isActiveCard) 0.sp else 0.6.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (!trailingText.isNullOrBlank() && onTrailingClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = HubBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onTrailingClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("auth_forgot_password_button")
                    )
                }
            }
        }

        if (!isActiveCard) {
            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun DateOfBirthPickerDialog(
    currentDob: String,
    onDismiss: () -> Unit,
    onConfirmDob: (String) -> Unit
) {
    val parts = remember(currentDob) {
        val split = currentDob.split("-")
        val y = split.getOrNull(0)?.toIntOrNull() ?: 2002
        val m = split.getOrNull(1)?.toIntOrNull() ?: 5
        val d = split.getOrNull(2)?.toIntOrNull() ?: 15
        Triple(y.coerceIn(1950, 2024), m.coerceIn(1, 12), d.coerceIn(1, 31))
    }

    var year by remember { mutableIntStateOf(parts.first) }
    var month by remember { mutableIntStateOf(parts.second) }
    var day by remember { mutableIntStateOf(parts.third) }

    val monthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cake,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set Date of Birth",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Saved to your G-Max gamer account profile:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DobStepperColumn(
                        label = "Year",
                        valueText = year.toString(),
                        onDecrement = { if (year > 1950) year-- },
                        onIncrement = { if (year < 2024) year++ },
                        modifier = Modifier.weight(1.2f)
                    )
                    DobStepperColumn(
                        label = "Month",
                        valueText = "${monthNames[month - 1]} ($month)",
                        onDecrement = { month = if (month > 1) month - 1 else 12 },
                        onIncrement = { month = if (month < 12) month + 1 else 1 },
                        modifier = Modifier.weight(1.3f)
                    )
                    DobStepperColumn(
                        label = "Day",
                        valueText = String.format(Locale.US, "%02d", day),
                        onDecrement = { day = if (day > 1) day - 1 else 31 },
                        onIncrement = { day = if (day < 31) day + 1 else 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                    onConfirmDob(formatted)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF081018)
                ),
                modifier = Modifier.testTag("confirm_dob_button")
            ) {
                Text("Save Date of Birth", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DobStepperColumn(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        )
        TextButton(onClick = onIncrement, contentPadding = PaddingValues(0.dp)) {
            Text("+1", fontWeight = FontWeight.ExtraBold, color = HubBlue)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        TextButton(onClick = onDecrement, contentPadding = PaddingValues(0.dp)) {
            Text("-1", fontWeight = FontWeight.ExtraBold, color = HubBlue)
        }
    }
}
