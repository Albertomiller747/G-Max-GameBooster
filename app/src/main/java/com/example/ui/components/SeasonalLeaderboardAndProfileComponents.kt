package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.SolarGold
import com.example.util.CountryCatalog
import com.example.util.CountryOption
import com.example.util.ExternalPlayerProfileData
import com.example.util.GamerscoreEngine
import com.example.util.PremadeProfileTag
import com.example.util.PremadeTagsCatalog
import com.example.util.TopFiveSeasonBadge

private fun Int.scaledSp(scale: Float): TextUnit = (this * scale).sp
private fun Double.scaledSp(scale: Float): TextUnit = (this.toFloat() * scale).sp
private fun Int.scaledDp(scale: Float): Dp = (this * scale).dp

/**
 * Copies text to the system clipboard and displays a confirmation toast.
 */
fun copyTextToClipboard(context: android.content.Context, label: String, text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, trimmed)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(
            context,
            com.example.util.AppStrings.trText("$label copied to clipboard", "$label copiado al portapapeles"),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    } catch (_: Exception) {}
}

/**
 * Renders a user's avatar image supporting Base64 data URIs (synced via Firestore),
 * local file:// paths, content:// URIs, and remote https:// URLs with a clean fallback icon.
 */
@Composable
fun PlayerAvatarImage(
    photoUrl: String,
    contentDescription: String,
    defaultIconSize: Dp = 64.dp,
    defaultIconTint: Color = HubBlue,
    fallbackIconSize: Dp = defaultIconSize,
    fallbackTint: Color = defaultIconTint,
    modifier: Modifier = Modifier
) {
    val cleanUrl = photoUrl.trim()
    val decodedBase64Bitmap = remember(cleanUrl) {
        if (cleanUrl.startsWith("data:image", ignoreCase = true) && cleanUrl.contains("base64,")) {
            try {
                val base64Part = cleanUrl.substringAfter("base64,")
                val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    when {
        decodedBase64Bitmap != null -> {
            val imageBitmap = remember(decodedBase64Bitmap) {
                decodedBase64Bitmap.asImageBitmap()
            }
            androidx.compose.foundation.Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        cleanUrl.isNotBlank() -> {
            var loadFailed by remember(cleanUrl) { mutableStateOf(false) }
            if (!loadFailed) {
                AsyncImage(
                    model = cleanUrl,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = ContentScale.Crop,
                    onError = { loadFailed = true }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = contentDescription,
                    tint = fallbackTint,
                    modifier = Modifier.size(fallbackIconSize)
                )
            }
        }
        else -> {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = contentDescription,
                tint = fallbackTint,
                modifier = Modifier.size(fallbackIconSize)
            )
        }
    }
}

/**
 * Custom Crown Vector Icon used for Gamerscore pills and profile badges.
 */
@Composable
fun LeaderboardCrownIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val crownPath = Path().apply {
            moveTo(w * 0.10f, h * 0.78f)
            lineTo(w * 0.05f, h * 0.28f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.95f, h * 0.28f)
            lineTo(w * 0.90f, h * 0.78f)
            close()
        }
        drawPath(path = crownPath, color = tint)

        // Base bar below crown
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.83f),
            size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.11f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.05f, h * 0.05f)
        )
    }
}

/**
 * Seasonal badge pill component.
 */
@Composable
fun TopFiveSeasonBadgePill(
    rank: Int,
    daysRemaining: Int = 14,
    isGraceShieldActive: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (rank !in 1..5) return

    val badgeColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFF38BDF8) // Cyber Blue
        3 -> Color(0xFF00E676) // Launch Green
        else -> Color(0xFFFF2E93) // Cyber Magenta/Coral for Top 4 & Top 5
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        badgeColor.copy(alpha = 0.26f),
                        Color(0xFF0B1322).copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = if (compact) 1.dp else 1.4.dp,
                color = badgeColor.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 3.dp else 5.dp
            )
            .testTag("top_five_season_badge_$rank"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LeaderboardCrownIcon(
            tint = badgeColor,
            modifier = Modifier.size(if (compact) 11.dp else 14.dp)
        )
        Text(
            text = "Top $rank",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor,
                fontSize = if (compact) 10.sp else 11.sp,
                letterSpacing = 0.4.sp
            )
        )
        Text(
            text = if (isGraceShieldActive) {
                if (compact) "• Shield" else "• 1-Pass Shield • ${daysRemaining.coerceAtLeast(1)}d"
            } else {
                "• ${daysRemaining.coerceAtLeast(1)}d"
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (isGraceShieldActive) LaunchGreen else Color(0xFFE2E8F0),
                fontSize = if (compact) 9.sp else 10.sp
            )
        )
    }
}

/**
 * External User Profile Screen opened when searching a player by username on the Profile tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalUserProfileScreen(
    profile: ExternalPlayerProfileData,
    scale: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flagEmoji = remember(profile.countryCode) {
        CountryCatalog.codeToFlagEmoji(profile.countryCode)
    }
    val ringColor = when (profile.topFiveBadge?.rank ?: profile.currentLeaderboardPosition) {
        1 -> SolarGold
        2 -> HubBlue
        3 -> LaunchGreen
        4, 5 -> RecorderCoral
        else -> HubBlue
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070D17))
            .padding(horizontal = 20.scaledDp(scale), vertical = 14.scaledDp(scale))
            .testTag("external_user_profile_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar: "<" on left, centered "Profile" title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.scaledDp(scale))
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.scaledDp(scale))
                    .clip(CircleShape)
                    .background(Color(0xFF0E1726))
                    .border(1.dp, HubBlue.copy(alpha = 0.35f), CircleShape)
                    .testTag("external_profile_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Profile",
                    tint = Color.White,
                    modifier = Modifier.size(20.scaledDp(scale))
                )
            }

            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.scaledSp(scale),
                    color = Color.White
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Main Elevated Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("external_user_profile_card"),
            shape = RoundedCornerShape(32.scaledDp(scale)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0E1726)
            ),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ringColor.copy(alpha = 0.70f),
                        HubBlue.copy(alpha = 0.25f)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 28.scaledDp(scale),
                        vertical = 36.scaledDp(scale)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Large Circular Profile Picture (116.dp)
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(
                    modifier = Modifier
                        .size(116.scaledDp(scale))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ringColor.copy(alpha = 0.35f),
                                    Color(0xFF142238)
                                )
                            )
                        )
                        .border(3.dp, ringColor, CircleShape)
                        .testTag("external_profile_avatar"),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerAvatarImage(
                        photoUrl = profile.photoUrl,
                        contentDescription = profile.displayName,
                        fallbackTint = ringColor,
                        fallbackIconSize = 88.scaledDp(scale),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(22.scaledDp(scale)))

                // 2. Bold Real Player Name
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 25.scaledSp(scale),
                        color = Color.White,
                        letterSpacing = 0.2.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("external_profile_name")
                )

                Spacer(modifier = Modifier.height(4.scaledDp(scale)))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            copyTextToClipboard(context, "Nickname", "@${profile.username}")
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("external_profile_copy_nickname_button")
                ) {
                    Text(
                        text = "@${profile.username} • ${com.example.util.tr("Verified", "Verificado")}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.scaledSp(scale),
                            color = HubBlue
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = com.example.util.tr("COPY", "COPIAR"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 9.scaledSp(scale),
                            color = LaunchGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.scaledDp(scale)))

                // Location / Country Pin Subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Country",
                        tint = HubBlue,
                        modifier = Modifier.size(15.scaledDp(scale))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$flagEmoji ${profile.countryName} • ${profile.rankTitle}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.scaledSp(scale),
                            color = Color(0xFF94A3B8)
                        )
                    )
                }

                // 3. Seasonal Badge Pill (if active)
                if (profile.topFiveBadge != null) {
                    Spacer(modifier = Modifier.height(14.scaledDp(scale)))
                    TopFiveSeasonBadgePill(
                        rank = profile.topFiveBadge.rank,
                        daysRemaining = profile.topFiveBadge.daysRemaining,
                        isGraceShieldActive = profile.topFiveBadge.isGraceShieldActive,
                        compact = false
                    )
                }

                // 3b. Player Equipped Gamer Tags on External Profile
                val externalGamerTags = remember(profile.selectedTags, profile.isVerifiedDeveloper) {
                    val tagIds = profile.selectedTags.ifEmpty {
                        if (profile.isVerifiedDeveloper) {
                            com.example.util.PremadeTagsCatalog.OFFICIAL_DEVELOPER_DEFAULT_TAGS
                        } else {
                            listOf("fps_pro", "120hz_club")
                        }
                    }
                    com.example.util.PremadeTagsCatalog.resolveTags(tagIds)
                }
                if (externalGamerTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.scaledDp(scale)))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("external_profile_gamer_tags"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        externalGamerTags.forEach { tag ->
                            val tagColor = Color(tag.category.accentColorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(tagColor.copy(alpha = 0.18f))
                                    .border(1.dp, tagColor.copy(alpha = 0.65f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.5.scaledSp(scale),
                                        color = tagColor
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.scaledDp(scale)))

                // 4. Centered Bio Paragraph (Tap to copy bio)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            copyTextToClipboard(context, "Bio", profile.bio)
                        }
                        .padding(horizontal = 6.scaledDp(scale), vertical = 4.dp)
                        .testTag("external_profile_bio")
                ) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.scaledSp(scale),
                            color = Color(0xFFCBD5E1),
                            lineHeight = 20.scaledSp(scale)
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(28.scaledDp(scale)))

                // 5. Capsule Gamerscore Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.scaledDp(scale))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    HubBlue,
                                    LaunchGreen.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(50)
                        )
                        .testTag("external_profile_gamerscore_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
                    ) {
                        LeaderboardCrownIcon(
                            tint = Color(0xFF06101E),
                            modifier = Modifier.size(18.scaledDp(scale))
                        )
                        Text(
                            text = "${profile.gamerscore} Gamerscore",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 16.scaledSp(scale),
                                color = Color(0xFF06101E),
                                letterSpacing = 0.4.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Header + Compact Identity Card for the `PROFILE SETTING` screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingHeaderAndIdentityCard(
    displayName: String,
    username: String,
    emailText: String,
    bio: String = "",
    userAvatarUrl: String,
    countryOption: CountryOption,
    isVerifiedDeveloper: Boolean,
    activeTopFiveBadge: TopFiveSeasonBadge?,
    equippedTags: List<PremadeProfileTag>,
    scale: Float,
    onBack: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onOpenTagPicker: () -> Unit,
    isVipStreakUnlocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accentColor = if (isVerifiedDeveloper) SolarGold else HubBlue

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.scaledDp(scale))
    ) {
        // Top Header Bar: "<" Back + Centered "PROFILE SETTING"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.scaledDp(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.scaledDp(scale))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, HubBlue.copy(alpha = 0.35f), CircleShape)
                    .testTag("account_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.scaledDp(scale))
                )
            }

            Text(
                text = com.example.util.tr("PROFILE SETTINGS", "AJUSTES DE PERFIL"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.scaledSp(scale),
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.size(40.scaledDp(scale)))
        }

        // Top User Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenEditProfile() }
                .testTag("profile_hero_card"),
            shape = RoundedCornerShape(22.scaledDp(scale)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.scaledDp(scale)),
                verticalArrangement = Arrangement.spacedBy(12.scaledDp(scale))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Circular Avatar with Country Flag Badge
                    Box(
                        modifier = Modifier.size(66.scaledDp(scale)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.scaledDp(scale))
                                .clip(CircleShape)
                                .background(Color(0xFF162238))
                                .border(2.2.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerAvatarImage(
                                photoUrl = userAvatarUrl,
                                contentDescription = displayName,
                                fallbackTint = accentColor,
                                fallbackIconSize = 46.scaledDp(scale),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.scaledDp(scale))
                                .clip(CircleShape)
                                .background(Color(0xFF090F1A))
                                .border(1.2.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = countryOption.flagEmoji,
                                fontSize = 11.scaledSp(scale)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.scaledDp(scale)))

                    // Center Name, Handle & Email
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.scaledSp(scale),
                                    color = if (isVerifiedDeveloper) SolarGold else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVerifiedDeveloper) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Verified Developer",
                                    tint = SolarGold,
                                    modifier = Modifier.size(16.scaledDp(scale))
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "@$username • ${countryOption.name}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.scaledSp(scale),
                                    color = HubBlue
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                onClick = {
                                    copyTextToClipboard(context, "Nickname", "@$username")
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = HubBlue.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, HubBlue.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("copy_profile_nickname_button")
                            ) {
                                Text(
                                    text = com.example.util.tr("Copy @", "Copiar @"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HubBlue,
                                        fontSize = 9.5.scaledSp(scale)
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isVipStreakUnlocked) {
                                VipStreakUsernameBadge()
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = emailText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.scaledSp(scale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Edit Profile",
                        tint = HubBlue,
                        modifier = Modifier.size(22.scaledDp(scale))
                    )
                }

                if (bio.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.scaledDp(scale)))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable {
                                copyTextToClipboard(context, "Bio", bio)
                            }
                            .padding(horizontal = 10.scaledDp(scale), vertical = 7.scaledDp(scale))
                            .testTag("copy_profile_bio_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.scaledSp(scale)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                copyTextToClipboard(context, "Bio", bio)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = LaunchGreen.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, LaunchGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("copy_profile_bio_button")
                        ) {
                            Text(
                                text = com.example.util.tr("Copy Bio", "Copiar Bio"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LaunchGreen,
                                    fontSize = 9.5.scaledSp(scale)
                                ),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (activeTopFiveBadge != null) {
                    TopFiveSeasonBadgePill(
                        rank = activeTopFiveBadge.rank,
                        daysRemaining = activeTopFiveBadge.daysRemaining,
                        isGraceShieldActive = activeTopFiveBadge.isGraceShieldActive,
                        compact = false,
                        onClick = onOpenLeaderboard
                    )
                }
            }
        }
    }
}

/**
 * Reusable Settings Group Card styled in G-Max dark cyber colors.
 */
@Composable
fun ProfileSettingSectionCard(
    sectionTitle: String,
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.scaledDp(scale)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.scaledDp(scale), vertical = 14.scaledDp(scale)),
            verticalArrangement = Arrangement.spacedBy(4.scaledDp(scale))
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.scaledSp(scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(bottom = 6.scaledDp(scale))
            )
            content()
        }
    }
}

@Composable
fun ProfileSettingActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = Color.Unspecified,
    badgeText: String? = null,
    badgeColor: Color = LaunchGreen,
    scale: Float,
    testTag: String = "",
    onClick: () -> Unit
) {
    val resolvedTitleColor = if (titleColor == Color.Unspecified || titleColor == Color.White) {
        MaterialTheme.colorScheme.onSurface
    } else {
        titleColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.scaledDp(scale)))
            .clickable { onClick() }
            .padding(vertical = 10.scaledDp(scale), horizontal = 4.scaledDp(scale))
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.scaledDp(scale))
                    .clip(RoundedCornerShape(11.scaledDp(scale)))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.38f), RoundedCornerShape(11.scaledDp(scale))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(19.scaledDp(scale))
                )
            }

            Spacer(modifier = Modifier.width(12.scaledDp(scale)))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.scaledSp(scale),
                            color = resolvedTitleColor
                        )
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(badgeColor.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeColor,
                                    fontSize = 9.scaledSp(scale)
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.scaledSp(scale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.scaledDp(scale))
        )
    }
}

@Composable
fun ProfileSettingToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    scale: Float,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.scaledDp(scale)))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.scaledDp(scale), horizontal = 4.scaledDp(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.scaledDp(scale))
                    .clip(RoundedCornerShape(11.scaledDp(scale)))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.38f), RoundedCornerShape(11.scaledDp(scale))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(19.scaledDp(scale))
                )
            }

            Spacer(modifier = Modifier.width(12.scaledDp(scale)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.scaledSp(scale),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.scaledSp(scale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HubBlue,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

/**
 * Dedicated `EDIT PROFILE` Sub-Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileSubScreen(
    initialDisplayName: String,
    initialUsername: String,
    initialEmail: String,
    initialBio: String,
    initialDob: String,
    userAvatarUrl: String,
    countryOption: CountryOption,
    activeTopFiveBadge: TopFiveSeasonBadge?,
    isEmailVerified: Boolean,
    scale: Float,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
    onOpenCountryPicker: () -> Unit,
    onOpenDobPicker: () -> Unit,
    onVerifyEmailClicked: (String) -> Unit,
    onSaveProfileChanges: (name: String, nickname: String, email: String, bio: String, dob: String) -> Unit,
    equippedTags: List<PremadeProfileTag> = emptyList(),
    currentBanner: String = "cyber_neon_grid",
    onOpenTagPicker: () -> Unit = {},
    onOpenBannerPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var draftName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var draftNickname by remember(initialUsername) { mutableStateOf(initialUsername) }
    var draftEmail by remember(initialEmail) { mutableStateOf(initialEmail) }
    var draftBio by remember(initialBio) { mutableStateOf(initialBio) }
    var draftDob by remember(initialDob) { mutableStateOf(initialDob) }
    var emailVerifiedLocal by remember(isEmailVerified) { mutableStateOf(isEmailVerified) }

    val activeBannerPreset = remember(currentBanner) {
        GamerscoreEngine.CYBER_BANNER_PRESETS.find { it.id.equals(currentBanner, ignoreCase = true) }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF111C2E),
        unfocusedContainerColor = Color(0xFF111C2E),
        focusedBorderColor = HubBlue,
        unfocusedBorderColor = Color(0xFF1E2D45),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = HubBlue
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070D17))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.scaledDp(scale), vertical = 12.scaledDp(scale))
            .padding(bottom = 84.dp)
            .testTag("edit_profile_subscreen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: "<-" Back + Centered "EDIT PROFILE"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.scaledDp(scale))
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.scaledDp(scale))
                    .clip(CircleShape)
                    .background(Color(0xFF111C2D))
                    .border(1.dp, HubBlue.copy(alpha = 0.35f), CircleShape)
                    .testTag("edit_profile_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.scaledDp(scale))
                )
            }

            Text(
                text = com.example.util.tr("EDIT PROFILE", "EDITAR PERFIL"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.scaledSp(scale),
                    letterSpacing = 1.sp,
                    color = Color.White
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Centered Circular Avatar with Bottom-Right Camera Icon Badge
        Box(
            modifier = Modifier.size(116.scaledDp(scale)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(108.scaledDp(scale))
                    .clip(CircleShape)
                    .background(Color(0xFF132035))
                    .border(2.5.dp, HubBlue, CircleShape)
                    .clickable { onPickAvatar() }
                    .testTag("edit_profile_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                PlayerAvatarImage(
                    photoUrl = userAvatarUrl,
                    contentDescription = "Profile Picture",
                    defaultIconSize = 76.scaledDp(scale),
                    defaultIconTint = HubBlue,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }

            // Camera Badge on Bottom-Right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.scaledDp(scale))
                    .clip(CircleShape)
                    .background(Color(0xFF090F1A))
                    .border(1.8.dp, HubBlue, CircleShape)
                    .clickable { onPickAvatar() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Profile Picture",
                    tint = Color.White,
                    modifier = Modifier.size(17.scaledDp(scale))
                )
            }
        }

        if (activeTopFiveBadge != null) {
            Spacer(modifier = Modifier.height(10.scaledDp(scale)))
            TopFiveSeasonBadgePill(
                rank = activeTopFiveBadge.rank,
                daysRemaining = activeTopFiveBadge.daysRemaining,
                compact = false
            )
        }

        Spacer(modifier = Modifier.height(20.scaledDp(scale)))

        // 1. Name Field
        EditProfileLabeledField(label = com.example.util.tr("Name", "Nombre"), scale = scale) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it.take(60) },
                placeholder = { Text(com.example.util.tr("Enter your display name", "Ingresa tu nombre"), color = Color(0xFF64748B)) },
                singleLine = true,
                shape = RoundedCornerShape(14.scaledDp(scale)),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_name_input")
            )
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 2. Nick Name (@username) Field with Copy button
        val editContext = androidx.compose.ui.platform.LocalContext.current
        EditProfileLabeledField(label = com.example.util.tr("Nick Name", "Apodo"), scale = scale) {
            OutlinedTextField(
                value = draftNickname,
                onValueChange = { draftNickname = it.removePrefix("@").take(20) },
                placeholder = { Text(com.example.util.tr("Enter unique nick name", "Ingresa un apodo único"), color = Color(0xFF64748B)) },
                prefix = { Text("@", color = HubBlue, fontWeight = FontWeight.ExtraBold) },
                trailingIcon = {
                    if (draftNickname.isNotBlank()) {
                        TextButton(
                            onClick = {
                                copyTextToClipboard(editContext, "Nickname", "@${draftNickname.removePrefix("@")}")
                            },
                            modifier = Modifier.testTag("edit_profile_copy_nickname_button")
                        ) {
                            Text(
                                text = com.example.util.tr("Copy", "Copiar"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = HubBlue
                                )
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.scaledDp(scale)),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_nickname_input")
            )
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 3. Email Field with inline blue "Verify" pill button (Locked when verified)
        EditProfileLabeledField(label = com.example.util.tr("Email", "Correo electrónico"), scale = scale) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = draftEmail,
                    onValueChange = {
                        if (!emailVerifiedLocal) {
                            draftEmail = it.take(120)
                        }
                    },
                    readOnly = emailVerifiedLocal,
                    placeholder = { Text(com.example.util.tr("Enter your email", "Ingresa tu correo electrónico"), color = Color(0xFF64748B)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.scaledDp(scale)),
                    colors = fieldColors,
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (emailVerifiedLocal) LaunchGreen else Color(0xFF0052FF))
                                .then(
                                    if (!emailVerifiedLocal) {
                                        Modifier.clickable {
                                            emailVerifiedLocal = true
                                            onVerifyEmailClicked(draftEmail)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("edit_profile_verify_email_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (emailVerifiedLocal) com.example.util.tr("Verified", "Verificado") else com.example.util.tr("Verify", "Verificar"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (emailVerifiedLocal) Color(0xFF06130E) else Color.White,
                                    fontSize = 11.scaledSp(scale)
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_email_input")
                )
                if (emailVerifiedLocal) {
                    Text(
                        text = com.example.util.tr(
                            "Verified email is locked and cannot be edited.",
                            "El correo verificado está bloqueado y no se puede editar."
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.scaledSp(scale)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 4. Gamer Bio & Country Field with Copy Bio button
        EditProfileLabeledField(label = com.example.util.tr("Gamer Bio & Region", "Biografía Gamer y Región"), scale = scale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draftBio,
                    onValueChange = { draftBio = it.take(300) },
                    placeholder = { Text(com.example.util.tr("Write your gamer bio...", "Escribe tu biografía gamer..."), color = Color(0xFF64748B)) },
                    trailingIcon = {
                        if (draftBio.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    copyTextToClipboard(editContext, "Bio", draftBio)
                                },
                                modifier = Modifier.testTag("edit_profile_copy_bio_button")
                            ) {
                                Text(
                                    text = com.example.util.tr("Copy", "Copiar"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LaunchGreen
                                    )
                                )
                            }
                        }
                    },
                    maxLines = 2,
                    shape = RoundedCornerShape(14.scaledDp(scale)),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_bio_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.scaledDp(scale)))
                        .background(Color(0xFF111C2E))
                        .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(14.scaledDp(scale)))
                        .clickable { onOpenCountryPicker() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = countryOption.flagEmoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${countryOption.name} (${countryOption.code})",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Text(
                        text = com.example.util.tr("Change", "Cambiar"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = HubBlue
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 5. Date of birth Field
        EditProfileLabeledField(label = com.example.util.tr("Date of birth", "Fecha de nacimiento"), scale = scale) {
            OutlinedTextField(
                value = draftDob,
                onValueChange = { draftDob = it.take(30) },
                placeholder = { Text("18.May.2001", color = Color(0xFF64748B)) },
                singleLine = true,
                shape = RoundedCornerShape(14.scaledDp(scale)),
                colors = fieldColors,
                trailingIcon = {
                    TextButton(onClick = onOpenDobPicker) {
                        Text(
                            text = com.example.util.tr("Pick", "Elegir"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = HubBlue
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_dob_input")
            )
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 6. Profile Tags Section
        EditProfileLabeledField(
            label = com.example.util.tr(
                "Profile Tags (${equippedTags.size}/${PremadeTagsCatalog.MAX_EQUIPPED_TAGS})",
                "Etiquetas de Perfil (${equippedTags.size}/${PremadeTagsCatalog.MAX_EQUIPPED_TAGS})"
            ),
            scale = scale
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.scaledDp(scale)))
                    .background(Color(0xFF111C2E))
                    .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(14.scaledDp(scale)))
                    .clickable { onOpenTagPicker() }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = "Profile Tags",
                            tint = HubBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = com.example.util.tr("Equip up to 4 gamer tags", "Equipa hasta 4 etiquetas gamer"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenTagPicker,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("edit_profile_tags_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Choose Tags",
                            tint = HubBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = com.example.util.tr("Choose Tags", "Elegir Etiquetas"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = HubBlue
                            )
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (equippedTags.isEmpty()) {
                        Text(
                            text = com.example.util.tr(
                                "No tags equipped yet. Tap Choose Tags to customize.",
                                "Aún no tienes etiquetas equipadas. Toca Elegir Etiquetas para personalizar."
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8)
                            )
                        )
                    } else {
                        equippedTags.forEach { tag ->
                            val tagColor = Color(tag.category.accentColorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(tagColor.copy(alpha = 0.18f))
                                    .border(1.dp, tagColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = tagColor,
                                        fontSize = 10.scaledSp(scale)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

        // 7. Cyber Banner Section
        EditProfileLabeledField(label = com.example.util.tr("Cyber Banner", "Banner Cyber"), scale = scale) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.scaledDp(scale)))
                    .background(
                        if (activeBannerPreset != null) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(activeBannerPreset.startColorArgb),
                                    Color(activeBannerPreset.endColorArgb)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF111C2E), Color(0xFF1E293B))
                            )
                        }
                    )
                    .border(1.dp, SolarGold.copy(alpha = 0.55f), RoundedCornerShape(14.scaledDp(scale)))
                    .clickable { onOpenBannerPicker() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("edit_profile_banner_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = "Cyber Banner",
                        tint = SolarGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activeBannerPreset?.title ?: com.example.util.tr("Custom Photo Banner", "Banner de Foto Personalizado"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = activeBannerPreset?.subtitle ?: com.example.util.tr("Tap to choose preset or upload photo", "Toca para elegir diseño o subir foto"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.scaledSp(scale)
                            )
                        )
                    }
                }

                Text(
                    text = com.example.util.tr("Customize", "Personalizar"),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = SolarGold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.scaledDp(scale)))

        // Save Profile Button
        Button(
            onClick = {
                onSaveProfileChanges(
                    draftName.trim(),
                    draftNickname.trim(),
                    draftEmail.trim(),
                    draftBio.trim(),
                    draftDob.trim()
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_edit_profile_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HubBlue,
                contentColor = Color(0xFF071019)
            )
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = com.example.util.tr("Save Profile Changes", "Guardar Cambios del Perfil"),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.scaledSp(scale)
                )
            )
        }
    }
}

@Composable
private fun EditProfileLabeledField(
    label: String,
    scale: Float,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.scaledDp(scale))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.scaledSp(scale),
                color = Color(0xFFE2E8F0)
            )
        )
        content()
    }
}
