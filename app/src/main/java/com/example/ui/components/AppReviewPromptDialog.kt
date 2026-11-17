package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.util.AppStrings
import com.example.util.ConsoleHapticManager
import com.example.util.ReviewTriggerSource

/**
 * In-App Star Prompt Dialog that directs users to the Google Play Store review page.
 *
 * Triggered after:
 * - A Boost session completes
 * - 30 minutes of active Boost usage
 * - Stopping a Screen Recording (or tapping the review push notification)
 *
 * Once the user opens the Google Play Store review page and returns to the app,
 * `AppReviewManager` marks them verified reviewed and never shows this popup again.
 */
@Composable
fun AppReviewPromptDialog(
    triggerSource: ReviewTriggerSource,
    onRateOnPlayStore: (selectedStars: Int) -> Unit,
    onDismissLater: () -> Unit
) {
    val context = LocalContext.current
    var selectedStars by remember { mutableIntStateOf(5) }

    val badgeText = when (triggerSource) {
        ReviewTriggerSource.BOOST_30_MIN_MILESTONE -> AppStrings.trText(
            en = "30+ MIN FOCUS MILESTONE",
            es = "META DE 30+ MIN EN ENFOQUE",
            ptBr = "MARCO DE 30+ MIN DE FOCO",
            id = "MILESTONE FOKUS 30+ MENIT",
            vi = "CỘT MỐC 30+ PHÚT TẬP TRUNG",
            tl = "30+ MIN FOCUS MILESTONE"
        )
        ReviewTriggerSource.SCREEN_RECORDING_STOPPED -> AppStrings.trText(
            en = "RECORDING SAVED",
            es = "GRABACIÓN GUARDADA",
            ptBr = "GRAVAÇÃO SALVA",
            id = "REKAMAN TERSIMPAN",
            vi = "ĐÃ LƯU VIDEO QUAY",
            tl = "NA-SAVE ANG RECORDING"
        )
        else -> AppStrings.trText(
            en = "FOCUS SESSION COMPLETE",
            es = "SESIÓN DE ENFOQUE COMPLETADA",
            ptBr = "SESSÃO DE FOCO CONCLUÍDA",
            id = "SESI FOKUS SELESAI",
            vi = "HOÀN TẤT PHIÊN TẬP TRUNG",
            tl = "TAPOS NA ANG FOCUS SESSION"
        )
    }

    val titleText = AppStrings.trText(
        en = "Enjoying GMAX Gaming Hub?",
        es = "¿Disfrutas de GMAX Gaming Hub?",
        ptBr = "Curtindo o GMAX Gaming Hub?",
        id = "Menikmati GMAX Gaming Hub?",
        vi = "Bạn thích GMAX Gaming Hub chứ?",
        tl = "Nag-eenjoy ka ba sa GMAX Gaming Hub?"
    )

    val subtitleText = when (triggerSource) {
        ReviewTriggerSource.SCREEN_RECORDING_STOPPED -> AppStrings.trText(
            en = "Your gameplay recording is saved! Tap a star below to rate GMAX on the Google Play review page.",
            es = "¡Tu grabación de juego se ha guardado! Toca una estrella para calificar a GMAX en Google Play.",
            ptBr = "Sua gravação de jogo foi salva! Toque em uma estrela abaixo para avaliar o GMAX na Google Play.",
            id = "Rekaman gameplay Anda telah disimpan! Ketuk bintang di bawah untuk mengulas GMAX di Google Play.",
            vi = "Video quay màn hình của bạn đã được lưu! Chạm vào sao bên dưới để đánh giá GMAX trên Google Play.",
            tl = "Na-save na ang gameplay recording mo! Mag-tap ng star sa ibaba para i-rate ang GMAX sa Google Play."
        )
        ReviewTriggerSource.BOOST_30_MIN_MILESTONE -> AppStrings.trText(
            en = "You've gamed with GMAX Focus Mode for over 30 minutes! Tap a star below to leave a review on Google Play.",
            es = "¡Has jugado con GMAX Modo Enfoque por más de 30 minutos! Toca una estrella para dejar tu reseña en Google Play.",
            ptBr = "Você jogou com o Modo Foco do GMAX por mais de 30 minutos! Toque em uma estrela para deixar sua avaliação na Google Play.",
            id = "Anda telah bermain dengan Mode Fokus GMAX lebih dari 30 menit! Ketuk bintang untuk memberi ulasan di Google Play.",
            vi = "Bạn đã sử dụng Chế độ Tập trung GMAX hơn 30 phút! Chạm vào sao bên dưới để để lại đánh giá trên Google Play.",
            tl = "Mahigit 30 minuto ka nang gumagamit ng GMAX Focus Mode! Mag-tap ng star para mag-iwan ng review sa Google Play."
        )
        else -> AppStrings.trText(
            en = "Your focus session kept RAM clean and frame rates locked! Tap a star below to rate GMAX on Google Play.",
            es = "¡Tu sesión mantuvo la RAM limpia y los FPS estables! Toca una estrella para calificar a GMAX en Google Play.",
            ptBr = "Sua sessão manteve a RAM limpa e os FPS estáveis! Toque em uma estrela para avaliar o GMAX na Google Play.",
            id = "Sesi fokus menjaga RAM bersih dan FPS stabil! Ketuk bintang di bawah untuk mengulas GMAX di Google Play.",
            vi = "Phiên tập trung đã dọn sạch RAM và giữ FPS ổn định! Chạm vào sao bên dưới để đánh giá GMAX trên Google Play.",
            tl = "Napanatiling malinis ang RAM at stable ang FPS sa iyong focus session! I-rate ang GMAX sa Google Play."
        )
    }

    val rateNowText = AppStrings.trText(
        en = "Rate on Google Play ($selectedStars★)",
        es = "Calificar en Google Play ($selectedStars★)",
        ptBr = "Avaliar na Google Play ($selectedStars★)",
        id = "Beri Nilai di Google Play ($selectedStars★)",
        vi = "Đánh giá trên Google Play ($selectedStars★)",
        tl = "I-rate sa Google Play ($selectedStars★)"
    )

    val maybeLaterText = AppStrings.trText(
        en = "Maybe Later",
        es = "Quizás más tarde",
        ptBr = "Talvez mais tarde",
        id = "Nanti Saja",
        vi = "Để sau",
        tl = "Mamaya Na"
    )

    Dialog(
        onDismissRequest = onDismissLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(HubBlue, LaunchGreen, HubBlue.copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("in_app_review_prompt_dialog"),
            color = Color(0xFF0B101B),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Context Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HubBlue.copy(alpha = 0.16f))
                        .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (triggerSource == ReviewTriggerSource.SCREEN_RECORDING_STOPPED) {
                                Icons.Default.Videocam
                            } else {
                                Icons.Default.RocketLaunch
                            },
                            contentDescription = null,
                            tint = HubBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = HubBlue,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFB8C4D9),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive 5-Star Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("review_star_selector_row")
                ) {
                    for (star in 1..5) {
                        val isSelected = star <= selectedStars
                        val starScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "starScale_$star"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    ConsoleHapticManager.playSelectClick(context)
                                    selectedStars = star
                                }
                                .testTag("review_star_$star"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = if (isSelected) Color(0xFFFFD54F) else Color(0xFF7B8AA3),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary CTA -> Directs to Google Play Store Review Page
                Button(
                    onClick = {
                        ConsoleHapticManager.playLaunchPulse(context)
                        onRateOnPlayStore(selectedStars)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LaunchGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("review_popup_rate_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rateNowText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Dismissal Button -> 3-Day Cooldown
                TextButton(
                    onClick = {
                        ConsoleHapticManager.playSelectClick(context)
                        onDismissLater()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_popup_later_button")
                ) {
                    Text(
                        text = maybeLaterText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8C9BB5),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}
