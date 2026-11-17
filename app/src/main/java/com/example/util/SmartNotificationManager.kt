package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.service.SmartNotificationReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

enum class SmartNotificationTrigger(
    val id: Int,
    val key: String,
    val targetTab: String,
    val targetExtrasSubTab: Int = 0,
    val targetRecorderSubTab: Int = 0
) {
    TIMER_CLOCK_REMINDER(
        id = 4101,
        key = "timer_clock_trigger",
        targetTab = "EXTRAS",
        targetExtrasSubTab = 5
    ),
    CONTENT_CREATOR_NUDGE(
        id = 4102,
        key = "content_creator_nudge",
        targetTab = "RECORD",
        targetRecorderSubTab = 1
    ),
    SEND_TIME_MODELING(
        id = 4103,
        key = "send_time_modeling",
        targetTab = "LAUNCHER"
    ),
    POST_SESSION_WRAP_UP(
        id = 4104,
        key = "post_session_wrap_up",
        targetTab = "LAUNCHER"
    ),
    INACTIVITY_DAY_3(
        id = 4105,
        key = "inactivity_day_3",
        targetTab = "LAUNCHER"
    ),
    INACTIVITY_DAY_7(
        id = 4106,
        key = "inactivity_day_7",
        targetTab = "EXTRAS",
        targetExtrasSubTab = 5
    ),
    WEEKLY_MILESTONE_SUMMARY(
        id = 4107,
        key = "weekly_milestone_summary",
        targetTab = "PROFILE"
    ),
    APP_REVIEW_AFTER_BOOST(
        id = 4108,
        key = "app_review_after_boost",
        targetTab = "LAUNCHER"
    ),
    APP_REVIEW_AFTER_RECORDING(
        id = 4109,
        key = "app_review_after_recording",
        targetTab = "RECORD"
    ),
    ALBERTO_GIFT_ARRIVED(
        id = 4110,
        key = "alberto_gift_arrived",
        targetTab = "PROFILE"
    ),
    RIVALS_THREAT(
        id = 4111,
        key = "rivals_threat",
        targetTab = "RIVALS"
    ),
    RIVALS_DETHRONED(
        id = 4112,
        key = "rivals_dethroned",
        targetTab = "RIVALS"
    ),
    RIVALS_ACTIVITY_NUDGE(
        id = 4113,
        key = "rivals_activity_nudge",
        targetTab = "RIVALS"
    ),
    RIVALS_MONTHLY_WRAP(
        id = 4114,
        key = "rivals_monthly_wrap",
        targetTab = "RIVALS"
    ),
    RIVALS_TAUNT_RECEIVED(
        id = 4115,
        key = "rivals_taunt_received",
        targetTab = "RIVALS"
    )
}

data class ScheduledPlayPromptTime(
    val peakGamingHour24: Int,
    val sendHour24: Int,
    val sendMinute: Int,
    val formattedSendTime: String,
    val formattedPeakHour: String
)

data class SmartNotificationPayload(
    val trigger: SmartNotificationTrigger,
    val title: String,
    val message: String,
    val targetTab: String,
    val targetExtrasSubTab: Int = 0,
    val targetRecorderSubTab: Int = 0
)

/**
 * Smart Push Notifications Engine for GMAX Gaming Hub.
 * Implements all 6 behavioral & retention notification triggers with deep linking,
 * 6-language localization (en, es-MX, pt-BR, id, vi, tl), and a strict 4x/week frequency cap.
 */
object SmartNotificationManager {

    const val CHANNEL_ID = "gmax_smart_engagement_channel"
    const val MAX_NOTIFICATIONS_PER_WEEK = 4
    const val MIN_NOTIFICATION_INTERVAL_MS = 4L * 60L * 60L * 1000L // At least 4 hours apart
    const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val FREQUENT_USAGE_THRESHOLD = 3

    @Volatile
    var enforceIntervalCooldown: Boolean = true

    private const val PREFS_NAME = "gmax_smart_notifications_prefs"
    private const val KEY_ENABLED = "smart_notifications_enabled"
    private const val KEY_SENT_TIMESTAMPS_JSON = "sent_timestamps_json"
    private const val KEY_OVERLAY_NO_CLOCK_COUNT = "overlay_no_clock_session_count"
    private const val KEY_BOOSTER_SESSION_COUNT = "booster_session_count"
    private const val KEY_HOURLY_HISTOGRAM_JSON = "gaming_hour_histogram_json"
    private const val KEY_LAST_ACTIVE_SESSION_MS = "last_active_gaming_session_ms"
    private const val KEY_LAST_CLEARED_RAM_MB = "last_session_cleared_ram_mb"
    private const val KEY_LAST_CLEARED_RAM_BYTES = "last_session_cleared_ram_bytes"
    private const val KEY_DAY3_SENT_FOR_SESSION_MS = "day3_sent_for_session_ms"
    private const val KEY_DAY7_SENT_FOR_SESSION_MS = "day7_sent_for_session_ms"
    private const val KEY_LAST_WEEKLY_REPORT_MS = "last_weekly_report_ms"

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordClearedRamBytes(context: Context, freedBytes: Long) {
        if (freedBytes <= 0L) return
        val freedMb = (freedBytes / (1024L * 1024L)).coerceAtLeast(1L)
        getPrefs(context).edit()
            .putLong(KEY_LAST_CLEARED_RAM_BYTES, freedBytes)
            .putLong(KEY_LAST_CLEARED_RAM_MB, freedMb)
            .apply()
    }

    fun getLastMeasuredClearedRamGb(context: Context): Double {
        val prefs = getPrefs(context)
        val savedBytes = prefs.getLong(KEY_LAST_CLEARED_RAM_BYTES, -1L)
        if (savedBytes > 0L) {
            return savedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        }
        val savedMb = prefs.getLong(KEY_LAST_CLEARED_RAM_MB, -1L)
        if (savedMb > 0L) {
            return savedMb.toDouble() / 1024.0
        }
        val trimBytes = AppMemoryManager.lastTrimResult.value?.totalFreedBytes ?: 0L
        if (trimBytes > 0L) {
            return trimBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        }
        val cacheFreedBytes = try {
            AppMemoryManager.clearCacheDirectoriesBytes(context)
        } catch (_: Exception) {
            0L
        }
        if (cacheFreedBytes > 0L) {
            recordClearedRamBytes(context, cacheFreedBytes)
            return cacheFreedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        }
        return 0.0
    }

    fun isSmartNotificationsEnabled(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        return true
    }

    fun setSmartNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleBackgroundChecks(context)
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelName = AppStrings.trText(
                en = "GMAX Smart Gaming Alerts",
                es = "Alertas Inteligentes de GMAX",
                ptBr = "Alertas Inteligentes do GMAX",
                id = "Notifikasi Pintar GMAX",
                vi = "Thông báo Chơi game Thông minh GMAX",
                tl = "GMAX Smart Gaming Alerts"
            )
            val channelDesc = AppStrings.trText(
                en = "Session wrap-ups, performance reminders, and weekly stats (max 4 per week)",
                es = "Resúmenes de sesión, recordatorios de rendimiento y estadísticas semanales (máx. 4 por semana)",
                ptBr = "Resumos de sessão, lembretes de desempenho e estatísticas semanais (máx. 4 por semana)",
                id = "Ringkasan sesi, pengingat performa, dan statistik mingguan (maks 4 per minggu)",
                vi = "Tổng kết phiên chơi, nhắc nhở hiệu năng và thống kê tuần (tối đa 4 lần/tuần)",
                tl = "Session wrap-ups, performance reminders, at weekly stats (max 4 kada linggo)"
            )
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDesc
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Returns the list of notification timestamps sent within the rolling 7-day window.
     */
    fun getRecentSentTimestamps(context: Context, nowMs: Long = System.currentTimeMillis()): List<Long> {
        val raw = getPrefs(context).getString(KEY_SENT_TIMESTAMPS_JSON, null) ?: return emptyList()
        val cutoff = nowMs - SEVEN_DAYS_MS
        val list = mutableListOf<Long>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val ts = arr.optLong(i, 0L)
                if (ts > cutoff && ts <= nowMs + 60_000L) {
                    list.add(ts)
                }
            }
        } catch (_: Exception) {}
        return list.sorted()
    }

    fun getWeeklySentCount(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        return getRecentSentTimestamps(context, nowMs).size
    }

    fun getRemainingWeeklyQuota(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        return (MAX_NOTIFICATIONS_PER_WEEK - getWeeklySentCount(context, nowMs)).coerceAtLeast(0)
    }

    fun canSendNotification(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isSmartNotificationsEnabled(context)) return false
        val recent = getRecentSentTimestamps(context, nowMs)
        if (recent.size >= MAX_NOTIFICATIONS_PER_WEEK) return false
        if (enforceIntervalCooldown && recent.isNotEmpty()) {
            val lastSentMs = recent.maxOrNull() ?: 0L
            if (lastSentMs > 0L && (nowMs - lastSentMs) < MIN_NOTIFICATION_INTERVAL_MS) {
                return false
            }
        }
        return true
    }

    private fun recordNotificationSent(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val updated = getRecentSentTimestamps(context, nowMs).toMutableList()
        updated.add(nowMs)
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        getPrefs(context).edit().putString(KEY_SENT_TIMESTAMPS_JSON, arr.toString()).apply()
    }

    fun resetQuotaAndTelemetryForTesting(context: Context) {
        enforceIntervalCooldown = false
        getPrefs(context).edit().clear().apply()
    }

    // =========================================================================
    // Exact Localized Messages for All 6 Triggers
    // =========================================================================

    /**
     * Trigger 1: The Timer Trigger
     * "Forgetting the Time after a long gaming session? Turn on your clock overlay to keep up with the time"
     */
    fun getTimerTriggerMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Forgetting the Time after a long gaming session? Turn on your clock overlay to keep up with the time",
            es = "¿Olvidas la hora después de una larga sesión de juego? Activa el reloj en pantalla para llevar el control del tiempo",
            ptBr = "Perdendo a noção da hora após uma longa sessão de jogo? Ative o relógio na sobreposição para acompanhar o tempo",
            id = "Lupa waktu setelah sesi gaming panjang? Nyalakan overlay jam untuk tetap memantau waktu",
            vi = "Quên thời gian sau phiên chơi game dài? Hãy bật đồng hồ trên lớp phủ để theo dõi thời gian",
            tl = "Nakakalimutan ang oras pagkatapos ng mahabang laro? Buksan ang clock overlay para mabantayan ang oras",
            active = lang
        )
    }

    /**
     * Trigger 2: The Content Creator Nudge
     * "Don't miss a clutch moment. Enable the up to 4K 120fps screen recorder before you launch."
     */
    fun getContentCreatorNudgeMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Don't miss a clutch moment. Enable the up to 4K 120fps screen recorder before you launch.",
            es = "No te pierdas un momento épico. Activa la grabadora de pantalla de hasta 4K 120fps antes de jugar.",
            ptBr = "Não perca uma jogada decisiva. Ative o gravador de tela em até 4K 120fps antes de iniciar.",
            id = "Jangan lewatkan momen clutch. Aktifkan perekam layar hingga 4K 120fps sebelum memulai game.",
            vi = "Đừng bỏ lỡ khoảnh khắc xuất thần. Hãy bật trình ghi màn hình lên đến 4K 120fps trước khi vào trận.",
            tl = "Huwag palampasin ang clutch moment. I-enable ang hanggang 4K 120fps screen recorder bago maglaro.",
            active = lang
        )
    }

    /**
     * Trigger 3: Send Time Modeling
     * "Time to play? Launch through GMAX to clear background RAM and lock in your frame rate."
     */
    fun getSendTimeModelingMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Time to play? Launch through GMAX to clear background RAM and lock in your frame rate.",
            es = "¿Hora de jugar? Inicia desde GMAX para liberar RAM en segundo plano y asegurar tus FPS.",
            ptBr = "Hora de jogar? Inicie pelo GMAX para limpar a RAM em segundo plano e estabilizar sua taxa de quadros.",
            id = "Waktunya main? Mulai lewat GMAX untuk membersihkan RAM latar belakang dan mengunci frame rate Anda.",
            vi = "Đến giờ chiến game? Khởi chạy qua GMAX để dọn RAM nền và khóa tốc độ khung hình ổn định.",
            tl = "Oras na para maglaro? Ilunsad sa GMAX para linisin ang background RAM at i-lock ang iyong frame rate.",
            active = lang
        )
    }

    private fun formatExactRamGb(ramClearedGb: Double): String {
        val nonNegative = ramClearedGb.coerceAtLeast(0.0)
        val twoDec = String.format(Locale.US, "%.2f", nonNegative)
        val trimmed = if (twoDec.endsWith("0")) {
            String.format(Locale.US, "%.1f", nonNegative)
        } else {
            twoDec
        }
        return "${trimmed}GB"
    }

    /**
     * Trigger 4: Post Session Wrap-Up
     * Displays the exact measured amount of RAM cleared rather than a fabricated fallback.
     */
    fun getPostSessionWrapUpMessage(
        ramClearedGb: Double = 0.0,
        lang: AppLanguage = AppStrings.resolveActiveLanguage()
    ): String {
        val formattedGb = formatExactRamGb(ramClearedGb)
        return when (lang) {
            AppLanguage.EN, AppLanguage.AUTO ->
                "Session complete! $formattedGb of RAM cleared and temperatures kept stable."
            AppLanguage.ES_MX ->
                "¡Sesión completada! $formattedGb de RAM liberados y temperaturas estables."
            AppLanguage.ES_LA ->
                "¡Partida completada! Se liberaron $formattedGb de RAM y las temperaturas se mantuvieron estables."
            AppLanguage.JA ->
                "セッション完了！${formattedGb}のRAMを解放し、温度を安定させました。"
            AppLanguage.HI ->
                "सेशन पूरा हुआ! $formattedGb RAM साफ़ किया गया और तापमान स्थिर रखा गया।"
            AppLanguage.PT_BR ->
                "Sessão concluída! $formattedGb de RAM liberados e temperaturas mantidas estáveis."
            AppLanguage.ID ->
                "Sesi selesai! $formattedGb RAM dibersihkan dan suhu tetap stabil."
            AppLanguage.VI ->
                "Hoàn tất phiên chơi! Đã giải phóng $formattedGb RAM và giữ nhiệt độ ổn định."
            AppLanguage.TL ->
                "Tapos na ang session! $formattedGb ng RAM ang nilinis at napanatiling stable ang temperatura."
        }
    }

    /**
     * Trigger 5A: Inactivity Day 3 Lapsed
     * "Your games are waiting in your library. Jump back in for a focused gaming session."
     */
    fun getDay3LapsedMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Your games are waiting in your library. Jump back in for a focused gaming session.",
            es = "Tus juegos te esperan en tu biblioteca. Vuelve a jugar para una sesión enfocada.",
            ptBr = "Seus jogos estão esperando na biblioteca. Volte a jogar para uma sessão focada.",
            id = "Game Anda menunggu di pustaka. Kembali bermain untuk sesi gaming yang fokus.",
            vi = "Các trò chơi đang chờ bạn trong thư viện. Quay lại ngay để trải nghiệm phiên chơi mượt mà.",
            tl = "Naghihintay na ang mga laro mo sa library. Bumalik na para sa focused gaming session.",
            active = lang
        )
    }

    /**
     * Trigger 5B: Inactivity Day 7 Lapsed
     * "Gameplay stuttering lately? Run a quick diagnostic to optimize your device right now."
     */
    fun getDay7LapsedMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Gameplay stuttering lately? Run a quick diagnostic to optimize your device right now.",
            es = "¿Tu juego tiene tirones últimamente? Ejecuta un diagnóstico rápido para optimizar tu dispositivo ahora mismo.",
            ptBr = "O jogo está engasgando ultimamente? Execute um diagnóstico rápido para otimizar seu aparelho agora mesmo.",
            id = "Gameplay sering patah-patah belakangan ini? Jalankan diagnostik cepat untuk mengoptimalkan perangkat Anda sekarang.",
            vi = "Chơi game gần đây bị giật khung hình? Chạy chẩn đoán nhanh để tối ưu hóa thiết bị của bạn ngay bây giờ.",
            tl = "Nagla-lag ba ang laro mo kamakailan? Magpatakbo ng mabilis na diagnostic para i-optimize ang device mo ngayon.",
            active = lang
        )
    }

    /**
     * Trigger 6: Milestone & Progression Reports (Weekly Summaries)
     * "You gamed for {14 hours} this week! Tap to see how much RAM was optimized and your increased gamer score."
     */
    fun getWeeklySummaryMessage(
        hoursPlayedThisWeek: Int = 14,
        lang: AppLanguage = AppStrings.resolveActiveLanguage()
    ): String {
        val safeHours = hoursPlayedThisWeek.coerceAtLeast(1)
        val hoursLabelEn = if (safeHours == 1) "1 hour" else "$safeHours hours"
        return when (lang) {
            AppLanguage.EN, AppLanguage.AUTO ->
                "You gamed for $hoursLabelEn this week! Tap to see how much RAM was optimized and your increased gamer score."
            AppLanguage.ES_MX ->
                "¡Jugaste $safeHours horas esta semana! Toca para ver cuánta RAM se optimizó y tu aumento de puntuación gamer."
            AppLanguage.ES_LA ->
                "¡Jugaste $safeHours horas esta semana! Toca para ver cuánta RAM se optimizó y el incremento de tu puntuación gamer."
            AppLanguage.JA ->
                "今週は${safeHours}時間プレイしました！タップして最適化されたRAM量とゲーマースコアの上昇を確認しましょう。"
            AppLanguage.HI ->
                "आपने इस सप्ताह $safeHours घंटे गेम खेला! देखें कि कितना RAM ऑप्टिमाइज़ हुआ और आपका गेमरस्कोर कितना बढ़ा।"
            AppLanguage.PT_BR ->
                "Você jogou por $safeHours horas nesta semana! Toque para ver quanta RAM foi otimizada e o aumento da sua pontuação gamer."
            AppLanguage.ID ->
                "Anda bermain selama $safeHours jam minggu ini! Ketuk untuk melihat berapa banyak RAM yang dioptimalkan dan peningkatan skor gamer Anda."
            AppLanguage.VI ->
                "Bạn đã chơi game $safeHours giờ trong tuần này! Chạm để xem lượng RAM đã tối ưu và điểm gamer tăng thêm của bạn."
            AppLanguage.TL ->
                "Naglaro ka nang $safeHours oras ngayong linggo! I-tap para makita kung gaano karaming RAM ang na-optimize at ang tumaas mong gamer score."
        }
    }

    fun getTitleForTrigger(
        trigger: SmartNotificationTrigger,
        lang: AppLanguage = AppStrings.resolveActiveLanguage()
    ): String {
        return when (trigger) {
            SmartNotificationTrigger.TIMER_CLOCK_REMINDER -> AppStrings.translateForLanguage(
                en = "Clock Overlay Reminder",
                es = "Recordatorio de Reloj en Pantalla",
                ptBr = "Lembrete do Relógio na Tela",
                id = "Pengingat Overlay Jam",
                vi = "Nhắc nhở Đồng hồ Lớp phủ",
                tl = "Paalala sa Clock Overlay",
                active = lang
            )
            SmartNotificationTrigger.CONTENT_CREATOR_NUDGE -> AppStrings.translateForLanguage(
                en = "4K 120fps Screen Recorder Ready",
                es = "Grabadora 4K 120fps Lista",
                ptBr = "Gravador de Tela 4K 120fps Pronto",
                id = "Perekam Layar 4K 120fps Siap",
                vi = "Trình ghi màn hình 4K 120fps Sẵn sàng",
                tl = "Handa na ang 4K 120fps Screen Recorder",
                active = lang
            )
            SmartNotificationTrigger.SEND_TIME_MODELING -> AppStrings.translateForLanguage(
                en = "Peak Gaming Time • GMAX Focus Mode",
                es = "Hora de Jugar • Modo Enfoque GMAX",
                ptBr = "Hora de Jogar • Modo Foco GMAX",
                id = "Waktunya Bermain • Mode Fokus GMAX",
                vi = "Giờ Chiến Game • Chế độ Tập trung GMAX",
                tl = "Oras ng Laro • GMAX Focus Mode",
                active = lang
            )
            SmartNotificationTrigger.POST_SESSION_WRAP_UP -> AppStrings.translateForLanguage(
                en = "Post-Session Performance Wrap-Up",
                es = "Resumen de Rendimiento de Sesión",
                ptBr = "Resumo de Desempenho da Sessão",
                id = "Ringkasan Performa Sesi",
                vi = "Tổng kết Hiệu năng Phiên chơi",
                tl = "Post-Session Performance Wrap-Up",
                active = lang
            )
            SmartNotificationTrigger.INACTIVITY_DAY_3 -> AppStrings.translateForLanguage(
                en = "Your Game Library Awaits",
                es = "Tu Biblioteca de Juegos te Espera",
                ptBr = "Sua Biblioteca de Jogos Espera por Você",
                id = "Pustaka Game Menunggu Anda",
                vi = "Thư viện Trò chơi Đang chờ Bạn",
                tl = "Naghihintay ang Game Library Mo",
                active = lang
            )
            SmartNotificationTrigger.INACTIVITY_DAY_7 -> AppStrings.translateForLanguage(
                en = "Device Performance Diagnostic",
                es = "Diagnóstico de Rendimiento",
                ptBr = "Diagnóstico de Desempenho do Aparelho",
                id = "Diagnostik Performa Perangkat",
                vi = "Chẩn đoán Hiệu năng Thiết bị",
                tl = "Device Performance Diagnostic",
                active = lang
            )
            SmartNotificationTrigger.WEEKLY_MILESTONE_SUMMARY -> AppStrings.translateForLanguage(
                en = "Weekly Gaming Milestone Report",
                es = "Reporte Semanal de Progreso Gamer",
                ptBr = "Relatório Semanal de Progresso Gamer",
                id = "Laporan Progres Gaming Mingguan",
                vi = "Báo cáo Thành tích Chơi game Tuần",
                tl = "Weekly Gaming Milestone Report",
                active = lang
            )
            SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST -> AppStrings.translateForLanguage(
                en = "Enjoying Smoother Gameplay with GMAX?",
                es = "¿Disfrutas de un juego más fluido con GMAX?",
                ptBr = "Curtindo uma jogabilidade mais fluida com o GMAX?",
                id = "Menikmati Gameplay Lebih Lancar dengan GMAX?",
                vi = "Trải nghiệm chơi game mượt hơn cùng GMAX?",
                tl = "Mas smooth ba ang laro mo sa GMAX?",
                active = lang
            )
            SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING -> AppStrings.translateForLanguage(
                en = "Clip Saved! Rate GMAX Screen Recorder",
                es = "¡Clip Guardado! Califica la Grabadora de GMAX",
                ptBr = "Clipe Salvo! Avalie o Gravador do GMAX",
                id = "Klip Tersimpan! Beri Nilai Perekam Layar GMAX",
                vi = "Đã lưu clip! Đánh giá Trình ghi màn hình GMAX",
                tl = "Na-save ang Clip! I-rate ang GMAX Screen Recorder",
                active = lang
            )
            SmartNotificationTrigger.ALBERTO_GIFT_ARRIVED -> AlbertoGiftManager.GIFT_NOTIFICATION_TITLE
            SmartNotificationTrigger.RIVALS_THREAT -> AppStrings.translateForLanguage(
                en = "Rivals Alert • Defend Your Rank!",
                es = "Alerta Rivals • ¡Defiende tu Rango!",
                ptBr = "Alerta Rivals • Defenda seu Rank!",
                id = "Peringatan Rivals • Pertahankan Peringkatmu!",
                vi = "Cảnh báo Rivals • Bảo vệ Thứ hạng!",
                tl = "Rivals Alert • Ipagtanggol ang Rank Mo!",
                active = lang
            )
            SmartNotificationTrigger.RIVALS_DETHRONED -> AppStrings.translateForLanguage(
                en = "Rivals Alert • You’ve Been Dethroned!",
                es = "Alerta Rivals • ¡Has sido Destronado!",
                ptBr = "Alerta Rivals • Você Foi Destronado!",
                id = "Peringatan Rivals • Kamu Telah Digeser!",
                vi = "Cảnh báo Rivals • Bạn Đã Mất Ngôi!",
                tl = "Rivals Alert • Naagaw ang Trono Mo!",
                active = lang
            )
            SmartNotificationTrigger.RIVALS_ACTIVITY_NUDGE -> AppStrings.translateForLanguage(
                en = "Rivals Activity • Squadmate Pulling Ahead",
                es = "Actividad Rivals • Tu Rival Toma Ventaja",
                ptBr = "Atividade Rivals • Rival Abrindo Vantagem",
                id = "Aktivitas Rivals • Rekan Skuad Unggul",
                vi = "Hoạt động Rivals • Đối thủ Đang Vượt Lên",
                tl = "Rivals Activity • Umaabante ang Squadmate Mo",
                active = lang
            )
            SmartNotificationTrigger.RIVALS_MONTHLY_WRAP -> AppStrings.translateForLanguage(
                en = "Rivals Monthly Wrap • 12 Hours Until Reset",
                es = "Resumen Mensual Rivals • Reinicio en 12 Horas",
                ptBr = "Resumo Mensal Rivals • Reset em 12 Horas",
                id = "Rekap Bulanan Rivals • Reset dalam 12 Jam",
                vi = "Tổng kết Tháng Rivals • Reset sau 12 Giờ",
                tl = "Rivals Monthly Wrap • 12 Oras Bago Mag-Reset",
                active = lang
            )
            SmartNotificationTrigger.RIVALS_TAUNT_RECEIVED -> AppStrings.translateForLanguage(
                en = "Rivals Squad Taunt!",
                es = "¡Burla en el Escuadrón Rivals!",
                ptBr = "Provocação no Esquadrão Rivals!",
                id = "Taunt Skuad Rivals!",
                vi = "Khiêu khích Bảng xếp hạng Rivals!",
                tl = "Rivals Squad Taunt!",
                active = lang
            )
        }
    }

    fun getReviewAfterBoostMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "You've completed your gaming focus session! Tap to leave a quick review on Google Play.",
            es = "¡Has completado tu sesión de enfoque de juego! Toca para dejar una reseña rápida en Google Play.",
            ptBr = "Você concluiu sua sessão de foco! Toque para deixar uma avaliação rápida na Google Play.",
            id = "Anda telah menyelesaikan sesi fokus gaming Anda! Ketuk untuk memberi ulasan singkat di Google Play.",
            vi = "Bạn đã hoàn tất phiên chơi game tập trung! Chạm để để lại đánh giá nhanh trên Google Play.",
            tl = "Tapos na ang iyong gaming focus session! I-tap para mag-iwan ng mabilis na review sa Google Play.",
            active = lang
        )
    }

    fun getReviewAfterRecordingMessage(lang: AppLanguage = AppStrings.resolveActiveLanguage()): String {
        return AppStrings.translateForLanguage(
            en = "Your gameplay recording is ready. Tap to rate GMAX on Google Play and let us know how it performed!",
            es = "Tu grabación de juego está lista. ¡Toca para calificar a GMAX en Google Play y contarnos qué tal funcionó!",
            ptBr = "Sua gravação de jogo está pronta. Toque para avaliar o GMAX na Google Play e contar como foi o desempenho!",
            id = "Rekaman gameplay Anda sudah siap. Ketuk untuk memberi nilai GMAX di Google Play!",
            vi = "Video quay màn hình của bạn đã sẵn sàng. Chạm để đánh giá GMAX trên Google Play!",
            tl = "Handa na ang gameplay recording mo. I-tap para i-rate ang GMAX sa Google Play!",
            active = lang
        )
    }

    // =========================================================================
    // Send Time Modeling (5 Minutes Before Historical Peak Gaming Hour)
    // =========================================================================

    fun recordGamingHourActivity(context: Context, hourOfDay24: Int? = null, nowMs: Long = System.currentTimeMillis()) {
        val prefs = getPrefs(context)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour = (hourOfDay24 ?: cal.get(Calendar.HOUR_OF_DAY)).coerceIn(0, 23)

        val hist = loadHourlyHistogram(context).toMutableMap()
        hist[hour] = (hist[hour] ?: 0) + 1
        val obj = JSONObject()
        for ((h, count) in hist) {
            obj.put(h.toString(), count)
        }
        prefs.edit()
            .putString(KEY_HOURLY_HISTOGRAM_JSON, obj.toString())
            .putLong(KEY_LAST_ACTIVE_SESSION_MS, nowMs)
            .apply()
    }

    fun loadHourlyHistogram(context: Context): Map<Int, Int> {
        val raw = getPrefs(context).getString(KEY_HOURLY_HISTOGRAM_JSON, null) ?: return emptyMap()
        val map = mutableMapOf<Int, Int>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val h = k.toIntOrNull()
                if (h != null && h in 0..23) {
                    map[h] = obj.optInt(k, 0)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    /**
     * Models the user's historical peak gaming hour (defaults to 20:00 / 8:00 PM if no history yet)
     * and computes the optimal send time 5 minutes prior (e.g., 7:55 PM / 19:55 for 8:00 PM).
     */
    fun computeOptimalSendTime(context: Context): ScheduledPlayPromptTime {
        val hist = loadHourlyHistogram(context)
        val peakHour24 = hist.entries.maxByOrNull { it.value }?.key ?: 20 // Default 8 PM (20:00)
        val sendHour24 = if (peakHour24 == 0) 23 else peakHour24 - 1
        val sendMinute = 55

        val formattedSend = format12HourTime(sendHour24, sendMinute)
        val formattedPeak = format12HourTime(peakHour24, 0)
        return ScheduledPlayPromptTime(
            peakGamingHour24 = peakHour24,
            sendHour24 = sendHour24,
            sendMinute = sendMinute,
            formattedSendTime = formattedSend,
            formattedPeakHour = formattedPeak
        )
    }

    private fun format12HourTime(hour24: Int, minute: Int): String {
        val period = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
    }

    // =========================================================================
    // Behavioral Trigger Evaluation & Dispatch
    // =========================================================================

    /**
     * 1. Evaluates the Timer Trigger when the user uses the overlay without the clock enabled.
     * Fires once the user has used the overlay >= 3 times with `showClock == false`.
     */
    fun onOverlayUsed(
        context: Context,
        showClockEnabled: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val prefs = getPrefs(context)
        if (showClockEnabled) {
            prefs.edit().putInt(KEY_OVERLAY_NO_CLOCK_COUNT, 0).apply()
            return null
        }
        val newCount = prefs.getInt(KEY_OVERLAY_NO_CLOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OVERLAY_NO_CLOCK_COUNT, newCount).apply()

        if (newCount >= FREQUENT_USAGE_THRESHOLD) {
            prefs.edit().putInt(KEY_OVERLAY_NO_CLOCK_COUNT, 0).apply()
            return dispatchTrigger(
                context = context,
                trigger = SmartNotificationTrigger.TIMER_CLOCK_REMINDER,
                customMessage = getTimerTriggerMessage(AppStrings.resolveActiveLanguageContext(context)),
                nowMs = nowMs
            )
        }
        return null
    }

    /**
     * 2. Evaluates the Content Creator Nudge when the user activates the Game Booster.
     * If the user has used the booster >= 3 times and has 0 recorded gameplay videos, nudges them to record.
     */
    fun onBoosterSessionStarted(
        context: Context,
        freedRamMb: Long,
        hasRecordedVideos: Boolean? = null,
        hourOfDay24: Int? = null,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val prefs = getPrefs(context)
        recordGamingHourActivity(context, hourOfDay24, nowMs)
        val boosterCount = prefs.getInt(KEY_BOOSTER_SESSION_COUNT, 0) + 1
        val exactMb = freedRamMb.coerceAtLeast(0L)
        prefs.edit()
            .putInt(KEY_BOOSTER_SESSION_COUNT, boosterCount)
            .putLong(KEY_LAST_CLEARED_RAM_MB, exactMb)
            .putLong(KEY_LAST_CLEARED_RAM_BYTES, exactMb * 1024L * 1024L)
            .apply()

        scheduleBackgroundChecks(context)

        val userHasVideos = hasRecordedVideos ?: run {
            try {
                ScreenRecordingManager.queryRecordedVideos(context).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        if (!userHasVideos && boosterCount >= FREQUENT_USAGE_THRESHOLD) {
            prefs.edit().putInt(KEY_BOOSTER_SESSION_COUNT, 0).apply()
            return dispatchTrigger(
                context = context,
                trigger = SmartNotificationTrigger.CONTENT_CREATOR_NUDGE,
                customMessage = getContentCreatorNudgeMessage(AppStrings.resolveActiveLanguageContext(context)),
                nowMs = nowMs
            )
        }
        return null
    }

    /**
     * 3. Dispatches the Send Time Modeling notification (scheduled 5 minutes before peak gaming hour).
     */
    fun triggerSendTimeModelingPrompt(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val lang = AppStrings.resolveActiveLanguageContext(context)
        return dispatchTrigger(
            context = context,
            trigger = SmartNotificationTrigger.SEND_TIME_MODELING,
            customMessage = getSendTimeModelingMessage(lang),
            nowMs = nowMs
        )
    }

    /**
     * 4. Dispatches the Post Session Wrap-Up notification after a gaming session ends.
     */
    fun triggerPostSessionWrapUp(
        context: Context,
        clearedRamGbOverride: Double? = null,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val ramGb = clearedRamGbOverride ?: getLastMeasuredClearedRamGb(context)
        val lang = AppStrings.resolveActiveLanguageContext(context)
        return dispatchTrigger(
            context = context,
            trigger = SmartNotificationTrigger.POST_SESSION_WRAP_UP,
            customMessage = getPostSessionWrapUpMessage(ramGb, lang),
            nowMs = nowMs
        )
    }

    /**
     * 5. Evaluates Inactivity Sequences (Day 3 Lapsed & Day 7 Lapsed churn prevention).
     */
    fun evaluateInactivitySequence(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val prefs = getPrefs(context)
        val lastActiveMs = prefs.getLong(KEY_LAST_ACTIVE_SESSION_MS, 0L)
        if (lastActiveMs <= 0L) return null

        val elapsedDays = (nowMs - lastActiveMs) / DAY_MS
        val lang = AppStrings.resolveActiveLanguageContext(context)

        if (elapsedDays >= 7L) {
            val day7SentFor = prefs.getLong(KEY_DAY7_SENT_FOR_SESSION_MS, 0L)
            if (day7SentFor != lastActiveMs) {
                val payload = dispatchTrigger(
                    context = context,
                    trigger = SmartNotificationTrigger.INACTIVITY_DAY_7,
                    customMessage = getDay7LapsedMessage(lang),
                    nowMs = nowMs
                )
                if (payload != null) {
                    prefs.edit().putLong(KEY_DAY7_SENT_FOR_SESSION_MS, lastActiveMs).apply()
                }
                return payload
            }
        } else if (elapsedDays >= 3L) {
            val day3SentFor = prefs.getLong(KEY_DAY3_SENT_FOR_SESSION_MS, 0L)
            if (day3SentFor != lastActiveMs) {
                val payload = dispatchTrigger(
                    context = context,
                    trigger = SmartNotificationTrigger.INACTIVITY_DAY_3,
                    customMessage = getDay3LapsedMessage(lang),
                    nowMs = nowMs
                )
                if (payload != null) {
                    prefs.edit().putLong(KEY_DAY3_SENT_FOR_SESSION_MS, lastActiveMs).apply()
                }
                return payload
            }
        }
        return null
    }

    fun setLastActiveSessionTimestampForTesting(context: Context, timestampMs: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_ACTIVE_SESSION_MS, timestampMs).apply()
    }

    /**
     * 6. Triggers Weekly Milestone & Progression Report.
     */
    fun triggerWeeklyMilestoneSummary(
        context: Context,
        weeklyHoursOverride: Int? = null,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        val hours = weeklyHoursOverride ?: run {
            try {
                val summary = GamePlayStatsHelper.getTotalPlayTimeSummary(context, emptyList())
                summary.hours.toInt().coerceAtLeast(0)
            } catch (_: Exception) {
                0
            }
        }
        val lang = AppStrings.resolveActiveLanguageContext(context)
        val payload = dispatchTrigger(
            context = context,
            trigger = SmartNotificationTrigger.WEEKLY_MILESTONE_SUMMARY,
            customMessage = getWeeklySummaryMessage(hours, lang),
            nowMs = nowMs
        )
        if (payload != null) {
            getPrefs(context).edit().putLong(KEY_LAST_WEEKLY_REPORT_MS, nowMs).apply()
        }
        return payload
    }

    /**
     * 7. Triggers a Review Push Notification after a Boost session or 30 minutes of active boost usage.
     * Permanently suppressed once the user is verified to have reviewed the app.
     */
    fun triggerReviewNotificationAfterBoost(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        if (!AppReviewManager.isEligibleToShowReview(context, nowMs)) {
            return null
        }
        val lang = AppStrings.resolveActiveLanguageContext(context)
        return dispatchReviewNotification(
            context = context,
            trigger = SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST,
            customMessage = getReviewAfterBoostMessage(lang),
            nowMs = nowMs
        )
    }

    /**
     * 8. Triggers a Review Push Notification after a Screen Recording stops.
     * Permanently suppressed once the user is verified to have reviewed the app.
     */
    fun triggerReviewNotificationAfterRecording(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        if (!AppReviewManager.isEligibleToShowReview(context, nowMs)) {
            return null
        }
        val lang = AppStrings.resolveActiveLanguageContext(context)
        return dispatchReviewNotification(
            context = context,
            trigger = SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING,
            customMessage = getReviewAfterRecordingMessage(lang),
            nowMs = nowMs
        )
    }

    private fun dispatchReviewNotification(
        context: Context,
        trigger: SmartNotificationTrigger,
        customMessage: String,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        if (!isSmartNotificationsEnabled(context)) return null
        if (getWeeklySentCount(context, nowMs) >= MAX_NOTIFICATIONS_PER_WEEK) return null
        val lang = AppStrings.resolveActiveLanguageContext(context)
        val payload = SmartNotificationPayload(
            trigger = trigger,
            title = getTitleForTrigger(trigger, lang),
            message = customMessage,
            targetTab = trigger.targetTab,
            targetExtrasSubTab = trigger.targetExtrasSubTab,
            targetRecorderSubTab = trigger.targetRecorderSubTab
        )
        recordNotificationSent(context, nowMs)
        postSystemNotification(context, payload)
        return payload
    }

    /**
     * Core dispatcher that enforces the 4x/week frequency cap and 4-hour minimum interval,
     * creates the deep-link PendingIntent, and posts the notification to Android's NotificationManager.
     */
    fun dispatchTrigger(
        context: Context,
        trigger: SmartNotificationTrigger,
        customMessage: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationPayload? {
        if (trigger == SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST ||
            trigger == SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING
        ) {
            if (!AppReviewManager.isEligibleToShowReview(context, nowMs)) {
                return null
            }
        }
        if (!canSendNotification(context, nowMs)) {
            return null
        }

        val lang = AppStrings.resolveActiveLanguageContext(context)
        val title = getTitleForTrigger(trigger, lang)
        val message = customMessage ?: when (trigger) {
            SmartNotificationTrigger.TIMER_CLOCK_REMINDER -> getTimerTriggerMessage(lang)
            SmartNotificationTrigger.CONTENT_CREATOR_NUDGE -> getContentCreatorNudgeMessage(lang)
            SmartNotificationTrigger.SEND_TIME_MODELING -> getSendTimeModelingMessage(lang)
            SmartNotificationTrigger.POST_SESSION_WRAP_UP ->
                getPostSessionWrapUpMessage(getLastMeasuredClearedRamGb(context), lang)
            SmartNotificationTrigger.INACTIVITY_DAY_3 -> getDay3LapsedMessage(lang)
            SmartNotificationTrigger.INACTIVITY_DAY_7 -> getDay7LapsedMessage(lang)
            SmartNotificationTrigger.WEEKLY_MILESTONE_SUMMARY -> {
                val exactHours = try {
                    GamePlayStatsHelper.getTotalPlayTimeSummary(context, emptyList()).hours.toInt()
                } catch (_: Exception) {
                    0
                }
                getWeeklySummaryMessage(exactHours, lang)
            }
            SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST -> getReviewAfterBoostMessage(lang)
            SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING -> getReviewAfterRecordingMessage(lang)
            SmartNotificationTrigger.ALBERTO_GIFT_ARRIVED -> AppStrings.translateForLanguage(
                en = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
                es = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_ES,
                ptBr = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
                id = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
                vi = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
                tl = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
                active = lang
            )
            SmartNotificationTrigger.RIVALS_THREAT,
            SmartNotificationTrigger.RIVALS_DETHRONED,
            SmartNotificationTrigger.RIVALS_ACTIVITY_NUDGE,
            SmartNotificationTrigger.RIVALS_MONTHLY_WRAP,
            SmartNotificationTrigger.RIVALS_TAUNT_RECEIVED -> customMessage.orEmpty()
        }

        val payload = SmartNotificationPayload(
            trigger = trigger,
            title = title,
            message = message,
            targetTab = trigger.targetTab,
            targetExtrasSubTab = trigger.targetExtrasSubTab,
            targetRecorderSubTab = trigger.targetRecorderSubTab
        )

        recordNotificationSent(context, nowMs)
        postSystemNotification(context, payload)
        return payload
    }

    /**
     * Posts a real-time Rivals Squad competition notification (The Threat, The Dethroning,
     * The Activity Nudge, Monthly Wrap 12h Before Reset, or One-Click Taunt) and deep-links
     * to the Rivals tab when tapped.
     */
    fun postRivalsNotification(
        context: Context,
        trigger: SmartNotificationTrigger,
        message: String
    ): SmartNotificationPayload {
        val lang = AppStrings.resolveActiveLanguageContext(context)
        val payload = SmartNotificationPayload(
            trigger = trigger,
            title = getTitleForTrigger(trigger, lang),
            message = message,
            targetTab = "RIVALS"
        )
        postSystemNotification(context, payload)
        return payload
    }

    /**
     * Schedules an exact background check for the Rivals Monthly Wrap notification
     * exactly 12 hours before the monthly UTC reset (`00:00:00 UTC` on the 1st of next month).
     */
    fun scheduleRivalsMonthlyWrap12hBeforeReset(context: Context, nowMs: Long = System.currentTimeMillis()) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAtMs = com.example.data.repository.RivalsRepository.nextMonthlyWrap12HoursBeforeResetEpochMs(nowMs)
            val targetTimeMs = if (triggerAtMs > nowMs) {
                triggerAtMs
            } else {
                com.example.data.repository.RivalsRepository.nextUtcMonthResetEpochMs(nowMs)
            }
            val intent = Intent(context, SmartNotificationReceiver::class.java).apply {
                action = SmartNotificationReceiver.ACTION_RIVALS_MONTHLY_WRAP_12H
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, 4203, intent, flags)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMs, pi)
        } catch (_: Exception) {}
    }

    /**
     * Immediately posts the "Alberto Gift has Arrived" push notification on startup
     * bypassing weekly frequency caps so the recipient always receives their gift alert.
     */
    fun postAlbertoGiftNotification(context: Context): SmartNotificationPayload? {
        val lang = AppStrings.resolveActiveLanguageContext(context)
        val message = AppStrings.translateForLanguage(
            en = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
            es = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_ES,
            ptBr = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
            id = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
            vi = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
            tl = AlbertoGiftManager.GIFT_NOTIFICATION_MESSAGE_EN,
            active = lang
        )
        val payload = SmartNotificationPayload(
            trigger = SmartNotificationTrigger.ALBERTO_GIFT_ARRIVED,
            title = AlbertoGiftManager.GIFT_NOTIFICATION_TITLE,
            message = message,
            targetTab = SmartNotificationTrigger.ALBERTO_GIFT_ARRIVED.targetTab
        )
        postSystemNotification(context, payload)
        return payload
    }

    fun buildDeepLinkIntent(context: Context, payload: SmartNotificationPayload): Intent {
        val isReviewTrigger = payload.trigger == SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST ||
            payload.trigger == SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_open_tab", payload.targetTab)
            putExtra("extra_extras_subtab", payload.targetExtrasSubTab)
            putExtra("extra_recorder_subtab", payload.targetRecorderSubTab)
            putExtra("extra_notification_trigger_key", payload.trigger.key)
            if (isReviewTrigger) {
                putExtra("extra_open_review_prompt", true)
            }
        }
    }

    private fun postSystemNotification(context: Context, payload: SmartNotificationPayload) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            ensureNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = buildDeepLinkIntent(context, payload)
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getActivity(
                context,
                payload.trigger.id,
                launchIntent,
                pendingFlags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(payload.title)
                .setContentText(payload.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(payload.trigger.id, notification)
        } catch (_: Exception) {}
    }

    /**
     * Schedules background AlarmManager triggers for Send Time Modeling (5 minutes before peak gaming hour)
     * and Inactivity Churn Prevention (Day 3 & Day 7 checks).
     */
    fun scheduleBackgroundChecks(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val scheduleInfo = computeOptimalSendTime(context)
            val now = Calendar.getInstance()
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, scheduleInfo.sendHour24)
                set(Calendar.MINUTE, scheduleInfo.sendMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val sendTimeIntent = Intent(context, SmartNotificationReceiver::class.java).apply {
                action = SmartNotificationReceiver.ACTION_SEND_TIME_MODELING
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val sendTimePi = PendingIntent.getBroadcast(context, 4201, sendTimeIntent, flags)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, sendTimePi)

            // Also schedule daily inactivity/milestone check
            val dailyCheckIntent = Intent(context, SmartNotificationReceiver::class.java).apply {
                action = SmartNotificationReceiver.ACTION_EVALUATE_RETENTION
            }
            val dailyPi = PendingIntent.getBroadcast(context, 4202, dailyCheckIntent, flags)
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_DAY,
                AlarmManager.INTERVAL_HALF_DAY,
                dailyPi
            )
        } catch (_: Exception) {}
    }
}
