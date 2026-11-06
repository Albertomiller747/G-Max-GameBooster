package com.example.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val englishSubtitle: String,
    val flagBadge: String
) {
    AUTO("auto", "Auto-Detect (System)", "Follows your device language automatically", "AUTO"),
    EN("en", "English", "English (US / Global)", "EN"),
    ES_MX("es-MX", "Español (Latinoamérica)", "Latin American Spanish • es-MX", "ES-MX"),
    PT_BR("pt-BR", "Português (Brasil)", "Brazilian Portuguese • pt-BR", "PT-BR"),
    ID("id", "Bahasa Indonesia", "Indonesian • id", "ID"),
    VI("vi", "Tiếng Việt", "Vietnamese • vi", "VI"),
    TL("tl", "Tagalog", "Tagalog / Filipino • tl", "TL");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrBlank()) return AUTO
            val normalized = code.trim().lowercase(Locale.ROOT)
            return when {
                normalized == "auto" -> AUTO
                normalized == "en" || normalized.startsWith("en-") || normalized.startsWith("en_") -> EN
                normalized == "es-mx" || normalized == "es_mx" || normalized.startsWith("es") -> ES_MX
                normalized == "pt-br" || normalized == "pt_br" || normalized.startsWith("pt") -> PT_BR
                normalized == "id" || normalized == "in" || normalized.startsWith("id-") || normalized.startsWith("in-") -> ID
                normalized == "vi" || normalized.startsWith("vi-") || normalized.startsWith("vi_") -> VI
                normalized == "tl" || normalized == "fil" || normalized.startsWith("tl-") || normalized.startsWith("fil-") -> TL
                else -> entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: AUTO
            }
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.EN }

data class MultiLangEntry(
    val ptBr: String,
    val id: String,
    val vi: String,
    val tl: String,
    val esMx: String? = null
)

/**
 * Multi-Language Localization Engine for G Max Gaming Hub.
 * Supports Automatic Device-Locale Detection + Manual Language Selection in Settings for:
 * - English (en)
 * - Latin American Spanish (es-MX)
 * - Brazilian Portuguese (pt-BR)
 * - Indonesian (id / in)
 * - Vietnamese (vi)
 * - Tagalog (tl / fil)
 */
object AppStrings {

    private const val PREFS_NAME = "gmax_hub_ui_prefs"
    private const val KEY_APP_LANGUAGE = "hub_app_language"

    private val _selectedLanguageMode = MutableStateFlow(AppLanguage.AUTO)
    val selectedLanguageMode: StateFlow<AppLanguage> = _selectedLanguageMode.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.AUTO.code)
            _selectedLanguageMode.value = AppLanguage.fromCode(saved)
            isInitialized = true
        } catch (_: Exception) {}
    }

    fun setLanguageMode(context: Context, mode: AppLanguage) {
        _selectedLanguageMode.value = mode
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_LANGUAGE, mode.code)
                .apply()
        } catch (_: Exception) {}
    }

    fun getLanguageMode(context: Context? = null): AppLanguage {
        if (!isInitialized && context != null) {
            init(context)
        }
        return _selectedLanguageMode.value
    }

    fun detectDeviceLanguage(configuration: Configuration? = null, localeOverride: Locale? = null): AppLanguage {
        val locale = localeOverride ?: if (configuration != null) {
            try {
                configuration.locales.get(0) ?: Locale.getDefault()
            } catch (_: Exception) {
                Locale.getDefault()
            }
        } else {
            Locale.getDefault()
        }
        val lang = locale.language.lowercase(Locale.ROOT)
        return when {
            lang.startsWith("pt") -> AppLanguage.PT_BR
            lang.startsWith("es") -> AppLanguage.ES_MX
            lang == "id" || lang == "in" -> AppLanguage.ID
            lang.startsWith("vi") -> AppLanguage.VI
            lang == "tl" || lang == "fil" -> AppLanguage.TL
            else -> AppLanguage.EN
        }
    }

    fun resolveActiveLanguage(configuration: Configuration? = null, localeOverride: Locale? = null): AppLanguage {
        val mode = _selectedLanguageMode.value
        return if (mode == AppLanguage.AUTO) {
            detectDeviceLanguage(configuration, localeOverride)
        } else {
            mode
        }
    }

    fun resolveActiveLanguageContext(context: Context): AppLanguage {
        if (!isInitialized) {
            init(context)
        }
        return resolveActiveLanguage(context.resources.configuration)
    }

    /**
     * Returns true when active language is non-English (verbose script/translations) so typography scaling can adapt.
     */
    fun isVerboseLanguage(lang: AppLanguage = resolveActiveLanguage()): Boolean {
        return lang != AppLanguage.EN
    }

    fun isSpanish(configuration: Configuration? = null): Boolean {
        return resolveActiveLanguage(configuration) == AppLanguage.ES_MX
    }

    fun isSpanishContext(context: Context): Boolean {
        return resolveActiveLanguageContext(context) == AppLanguage.ES_MX
    }

    fun trText(
        en: String,
        es: String,
        ptBr: String? = null,
        id: String? = null,
        vi: String? = null,
        tl: String? = null,
        languageOverride: AppLanguage? = null
    ): String {
        val active = languageOverride ?: resolveActiveLanguage()
        return translateForLanguage(en, es, ptBr, id, vi, tl, active)
    }

    fun translateForLanguage(
        en: String,
        es: String,
        ptBr: String? = null,
        id: String? = null,
        vi: String? = null,
        tl: String? = null,
        active: AppLanguage
    ): String {
        return when (active) {
            AppLanguage.EN, AppLanguage.AUTO -> en
            AppLanguage.ES_MX -> multiLangCatalog[en]?.esMx ?: es
            AppLanguage.PT_BR -> ptBr ?: multiLangCatalog[en]?.ptBr ?: autoTranslateFallback(en, AppLanguage.PT_BR)
            AppLanguage.ID -> id ?: multiLangCatalog[en]?.id ?: autoTranslateFallback(en, AppLanguage.ID)
            AppLanguage.VI -> vi ?: multiLangCatalog[en]?.vi ?: autoTranslateFallback(en, AppLanguage.VI)
            AppLanguage.TL -> tl ?: multiLangCatalog[en]?.tl ?: autoTranslateFallback(en, AppLanguage.TL)
        }
    }

    /**
     * Automatic phrase/word translator for dynamic messages or status logs.
     */
    fun autoTranslateDynamic(text: String, languageOverride: AppLanguage? = null): String {
        val active = languageOverride ?: resolveActiveLanguage()
        if (active == AppLanguage.EN || active == AppLanguage.AUTO) return text
        if (active == AppLanguage.ES_MX) {
            val exactEs = exactSpanishDictionary[text]
            if (exactEs != null) return exactEs
            var translated = text
            for ((enPhrase, esPhrase) in spanishPhraseReplacements) {
                if (translated.contains(enPhrase, ignoreCase = false)) {
                    translated = translated.replace(enPhrase, esPhrase)
                }
            }
            return translated
        }
        val catalogHit = multiLangCatalog[text]
        if (catalogHit != null) {
            return when (active) {
                AppLanguage.PT_BR -> catalogHit.ptBr
                AppLanguage.ID -> catalogHit.id
                AppLanguage.VI -> catalogHit.vi
                AppLanguage.TL -> catalogHit.tl
                else -> text
            }
        }
        return autoTranslateFallback(text, active)
    }

    private fun autoTranslateFallback(enText: String, target: AppLanguage): String {
        val direct = multiLangCatalog[enText]
        if (direct != null) {
            return when (target) {
                AppLanguage.PT_BR -> direct.ptBr
                AppLanguage.ID -> direct.id
                AppLanguage.VI -> direct.vi
                AppLanguage.TL -> direct.tl
                else -> enText
            }
        }

        var result = enText
        for (rule in multiLangPhraseReplacements) {
            if (result.contains(rule.en, ignoreCase = false)) {
                val rep = when (target) {
                    AppLanguage.PT_BR -> rule.ptBr
                    AppLanguage.ID -> rule.id
                    AppLanguage.VI -> rule.vi
                    AppLanguage.TL -> rule.tl
                    else -> rule.en
                }
                result = result.replace(rule.en, rep)
            }
        }
        return result
    }

    private data class PhraseRule(
        val en: String,
        val ptBr: String,
        val id: String,
        val vi: String,
        val tl: String
    )

    private val exactSpanishDictionary = mapOf(
        "Launcher" to "Lanzador",
        "Recorder" to "Grabadora",
        "Leaderboard" to "Clasificación",
        "Extras" to "Extras",
        "Settings" to "Ajustes",
        "Profile" to "Perfil",
        "Tweaks" to "Ajustes Pro",
        "Performance" to "Rendimiento",
        "Touch" to "Táctil",
        "Controller" to "Control",
        "Specs" to "Especificaciones",
        "Normal" to "Normal",
        "Beast" to "Bestia",
        "Normal Boost" to "Impulso Normal",
        "Beast Boost" to "Impulso Bestia",
        "Boost Memory" to "Optimizar RAM",
        "Boosting..." to "Optimizando...",
        "Launch" to "Jugar",
        "Add Games" to "Agregar Juegos",
        "Manage Library" to "Administrar Biblioteca",
        "Videos & Gallery" to "Videos y Galería",
        "No videos have been recorded" to "No se han grabado videos",
        "Record" to "Grabar",
        "Stop" to "Detener",
        "Top 100 Rankings" to "Top 100 Clasificación",
        "Tournament Rules" to "Reglas del Torneo",
        "Log In to Console" to "Entrar a la Consola",
        "Entering G Max Gaming Hub..." to "Entrando a G Max Gaming Hub...",
        "Sign In" to "Iniciar Sesión",
        "Sign Out" to "Cerrar Sesión",
        "Edit Profile" to "Editar Perfil",
        "Save Changes" to "Guardar Cambios",
        "Cancel" to "Cancelar",
        "Close" to "Cerrar",
        "Retry" to "Reintentar",
        "Connected" to "Conectado",
        "Disconnected" to "Desconectado",
        "Active" to "Activo",
        "Inactive" to "Inactivo",
        "Enabled" to "Activado",
        "Disabled" to "Desactivado",
        "Optimal" to "Óptimo",
        "Warm" to "Templado",
        "Hot" to "Caliente",
        "Critical" to "Crítico",
        "Charging" to "Cargando",
        "Discharging" to "Descargando",
        "Full" to "Lleno",
        "Unknown" to "Desconocido"
    )

    private val spanishPhraseReplacements = listOf(
        "No controller detected" to "No se detectó ningún control",
        "Controller Connected" to "Control Conectado",
        "Controller Overlay" to "Superposición de Control",
        "Custom RGB Lightbar Mixer" to "Mezclador de Barra de Luz RGB",
        "Controller Battery Level" to "Nivel de Batería del Control",
        "Real Rumble Tester" to "Probador de Vibración Real",
        "Touch Pipeline Latency Test" to "Prueba de Latencia Táctil",
        "Phone Hardware Specifications" to "Especificaciones de Hardware del Teléfono",
        "Device & Operating System" to "Dispositivo y Sistema Operativo",
        "Memory & Storage" to "Memoria y Almacenamiento",
        "Display & Graphics" to "Pantalla y Gráficos",
        "Battery & Thermal" to "Batería y Temperatura",
        "Total Gaming Time" to "Tiempo Total de Juego",
        "Most Played Games" to "Juegos Más Jugados",
        "Gamerscore" to "Puntuación Gamer",
        "Season Leaderboard" to "Clasificación de Temporada",
        "Tournament Rules" to "Reglas del Torneo",
        "Version 1.0" to "Versión 1.0",
        "available" to "disponibles",
        "freed" to "liberados",
        "Optimized" to "Optimizado"
    )

    private val multiLangCatalog: Map<String, MultiLangEntry> = mapOf(
        // Top Navigation & Core Tabs
        "Launcher" to MultiLangEntry("Iniciador", "Peluncur", "Trình khởi chạy", "Launcher", "Lanzador"),
        "Recorder" to MultiLangEntry("Gravador", "Perekam", "Trình ghi hình", "Recorder", "Grabadora"),
        "Top Gear" to MultiLangEntry("Equipamentos", "Gear Terbaik", "Thiết bị Pro", "Top Gear", "Top Gear"),
        "Extras" to MultiLangEntry("Extras", "Ekstra", "Tiện ích", "Extras", "Extras"),
        "Settings" to MultiLangEntry("Configurações", "Pengaturan", "Cài đặt", "Settings", "Ajustes"),
        "Profile" to MultiLangEntry("Perfil", "Profil", "Hồ sơ", "Profile", "Perfil"),
        "Leaderboard" to MultiLangEntry("Classificação", "Papan Skor", "Bảng xếp hạng", "Leaderboard", "Clasificación"),

        // Extras Sub-Tabs
        "Tweaks" to MultiLangEntry("Ajustes Pro", "Tweak Pro", "Tinh chỉnh", "Tweaks", "Ajustes Pro"),
        "Performance" to MultiLangEntry("Desempenho", "Performa", "Hiệu năng", "Performance", "Rendimiento"),
        "Touch" to MultiLangEntry("Toque", "Sentuhan", "Cảm ứng", "Touch", "Táctil"),
        "Controller" to MultiLangEntry("Controle", "Stik Kontrol", "Tay cầm", "Controller", "Control"),
        "Specs" to MultiLangEntry("Hardware", "Spesifikasi", "Cấu hình", "Specs", "Especificaciones"),
        "FPS & Diag" to MultiLangEntry("FPS e Diag", "FPS & Diag", "FPS & Chẩn đoán", "FPS at Diag", "FPS y Diag."),

        // Launcher & Boost Actions
        "Normal" to MultiLangEntry("Normal", "Normal", "Thường", "Normal", "Normal"),
        "Beast" to MultiLangEntry("Modo Fera", "Mode Buas", "Quái thú", "Beast", "Bestia"),
        "Normal Boost" to MultiLangEntry("Impulso Normal", "Boost Normal", "Tăng tốc Thường", "Normal Boost", "Impulso Normal"),
        "Beast Boost" to MultiLangEntry("Impulso Fera", "Boost Buas", "Tăng tốc Quái thú", "Beast Boost", "Impulso Bestia"),
        "Boost Memory" to MultiLangEntry("Otimizar RAM", "Optimalkan RAM", "Giải phóng RAM", "I-boost ang RAM", "Optimizar RAM"),
        "Boosting..." to MultiLangEntry("Otimizando...", "Mengoptimalkan...", "Đang tối ưu...", "Nagbo-boost...", "Optimizando..."),
        "Launch" to MultiLangEntry("Jogar", "Mainkan", "Chơi ngay", "Ilunsad", "Jugar"),
        "Add Games" to MultiLangEntry("Adicionar Jogos", "Tambah Game", "Thêm trò chơi", "Magdagdag ng Laro", "Agregar Juegos"),
        "Manage Library" to MultiLangEntry("Gerenciar Biblioteca", "Kelola Pustaka", "Quản lý thư viện", "Pamahalaan ang Library", "Administrar Biblioteca"),
        "Search games..." to MultiLangEntry("Buscar jogos...", "Cari game...", "Tìm trò chơi...", "Maghanap ng laro...", "Buscar juegos..."),
        "Recently Played" to MultiLangEntry("Jogados Recentemente", "Baru Dimainkan", "Chơi gần đây", "Kamakailang Nilaro", "Jugados Recientemente"),
        "Most Played" to MultiLangEntry("Mais Jogados", "Paling Sering Dimainkan", "Chơi nhiều nhất", "Pinakamaraming Nilaro", "Más Jugados"),
        "Most Played Games" to MultiLangEntry("Jogos Mais Jogados", "Game Paling Sering Dimainkan", "Trò chơi hay chơi nhất", "Pinakamadalas Laruin", "Juegos Más Jugados"),
        "Total Gaming Time" to MultiLangEntry("Tempo Total de Jogo", "Total Waktu Bermain", "Tổng thời gian chơi", "Kabuuang Oras ng Laro", "Tiempo Total de Juego"),
        "Game Library" to MultiLangEntry("Biblioteca de Jogos", "Pustaka Game", "Thư viện trò chơi", "Library ng Laro", "Biblioteca de Juegos"),
        "Selected Game" to MultiLangEntry("Jogo Selecionado", "Game Terpilih", "Trò chơi đã chọn", "Napiling Laro", "Juego Seleccionado"),

        // Recorder Screen
        "Videos & Gallery" to MultiLangEntry("Vídeos e Galeria", "Video & Galeri", "Video & Thư viện", "Video at Gallery", "Videos y Galería"),
        "No videos have been recorded" to MultiLangEntry("Nenhum vídeo foi gravado", "Belum ada video yang direkam", "Chưa có video nào được ghi", "Walang na-record na video", "No se han grabado videos"),
        "Record" to MultiLangEntry("Gravar", "Rekam", "Ghi hình", "I-record", "Grabar"),
        "Stop" to MultiLangEntry("Parar", "Berhenti", "Dừng", "Ihinto", "Detener"),
        "Screenshot" to MultiLangEntry("Captura de Tela", "Tangkapan Layar", "Chụp màn hình", "Screenshot", "Captura de Pantalla"),
        "Screen Recorder" to MultiLangEntry("Gravador de Tela", "Perekam Layar", "Trình ghi màn hình", "Screen Recorder", "Grabadora de Pantalla"),
        "Recording Settings" to MultiLangEntry("Ajustes de Gravação", "Pengaturan Rekaman", "Cài đặt ghi hình", "Settings ng Recording", "Ajustes de Grabación"),
        "Resolution" to MultiLangEntry("Resolução", "Resolusi", "Độ phân giải", "Resolusyon", "Resolución"),
        "Frame Rate" to MultiLangEntry("Taxa de Quadros", "Frame Rate", "Tốc độ khung hình", "Frame Rate", "Cuadros por Segundo"),
        "Bitrate" to MultiLangEntry("Taxa de Bits", "Bitrate", "Tốc độ bit", "Bitrate", "Tasa de Bits"),
        "Audio Source" to MultiLangEntry("Fonte de Áudio", "Sumber Audio", "Nguồn âm thanh", "Pinagmulan ng Audio", "Fuente de Audio"),
        "Recording saved to Videos!" to MultiLangEntry("Gravação salva em Vídeos!", "Rekaman disimpan ke Video!", "Đã lưu bản ghi vào Video!", "Na-save ang recording sa Videos!", "¡Grabación guardada en Videos!"),

        // Settings Screen
        "Hub Appearance & Settings" to MultiLangEntry(
            "Aparência e Configurações do Hub",
            "Tampilan & Pengaturan Hub",
            "Giao diện & Cài đặt Hub",
            "Hitsura at Settings ng Hub",
            "Apariencia y Ajustes del Hub"
        ),
        "Automatic Light/Dark display sync, custom backgrounds, and cloud account backup" to MultiLangEntry(
            "Sincronização automática Claro/Escuro, fundos personalizados e backup na nuvem",
            "Sinkronisasi layar Terang/Gelap otomatis, latar kustom, dan cadangan cloud",
            "Tự động đồng bộ Sáng/Tối, hình nền tùy chỉnh và sao lưu đám mây",
            "Auto Light/Dark sync, custom na background, at cloud account backup",
            "Sincronización automática Claro/Oscuro, fondos personalizados y respaldo en la nube"
        ),
        "App Language & Region" to MultiLangEntry(
            "Idioma e Região do App",
            "Bahasa & Wilayah Aplikasi",
            "Ngôn ngữ & Khu vực",
            "Wika at Rehiyon ng App",
            "Idioma y Región de la App"
        ),
        "Auto-detects device language or choose English, Español (es-MX), Português (pt-BR), Indonesian, Vietnamese, or Tagalog" to MultiLangEntry(
            "Detecta automaticamente o idioma do aparelho ou escolha Português (Brasil), Español (es-MX), Inglês, Indonésio, Vietnamita ou Tagalog",
            "Deteksi otomatis bahasa perangkat atau pilih Bahasa Indonesia, Inggris, Español (es-MX), Português (pt-BR), Vietnam, atau Tagalog",
            "Tự động nhận diện ngôn ngữ thiết bị hoặc chọn Tiếng Việt, Tiếng Anh, Español (es-MX), Português (pt-BR), Indonesia, Tagalog",
            "Auto-detect ang wika ng device o pumili ng Tagalog, English, Español (es-MX), Português (pt-BR), Indonesian, o Vietnamese",
            "Detecta automáticamente el idioma del dispositivo o elige Español (es-MX), Inglés, Portugués (Brasil), Indonesio, Vietnamita o Tagalo"
        ),
        "Display Mode & Background" to MultiLangEntry(
            "Modo de Exibição e Fundo",
            "Mode Tampilan & Latar Belakang",
            "Chế độ hiển thị & Hình nền",
            "Display Mode at Background",
            "Modo de Pantalla y Fondo"
        ),
        "Automatically follows your device display mode (Light / Dark) or use a custom color" to MultiLangEntry(
            "Segue automaticamente o modo de tela do dispositivo (Claro / Escuro) ou use uma cor personalizada",
            "Otomatis mengikuti mode layar perangkat (Terang / Gelap) atau gunakan warna kustom",
            "Tự động theo chế độ màn hình thiết bị (Sáng / Tối) hoặc dùng màu tùy chỉnh",
            "Sumusunod sa display mode ng device (Light / Dark) o gumamit ng custom na kulay",
            "Sigue automáticamente el modo de pantalla de tu dispositivo (Claro / Oscuro) o usa un color personalizado"
        ),
        "Signed-In Account" to MultiLangEntry("Conta Conectada", "Akun Masuk", "Tài khoản đã đăng nhập", "Naka-sign In na Account", "Cuenta Conectada"),
        "Guest Profile (Local Settings Only)" to MultiLangEntry(
            "Perfil Convidado (Apenas Local)",
            "Profil Tamu (Hanya Pengaturan Lokal)",
            "Hồ sơ Khách (Chỉ lưu cục bộ)",
            "Guest Profile (Local Settings Lang)",
            "Perfil de Invitado (Solo Ajustes Locales)"
        ),
        "Account" to MultiLangEntry("Conta", "Akun", "Tài khoản", "Account", "Cuenta"),
        "Sign In" to MultiLangEntry("Entrar", "Masuk", "Đăng nhập", "Mag-sign In", "Iniciar Sesión"),
        "Sign Out" to MultiLangEntry("Sair", "Keluar", "Đăng xuất", "Mag-sign Out", "Cerrar Sesión"),
        "Edit Profile" to MultiLangEntry("Editar Perfil", "Edit Profil", "Chỉnh sửa hồ sơ", "I-edit ang Profile", "Editar Perfil"),
        "Save Changes" to MultiLangEntry("Salvar Alterações", "Simpan Perubahan", "Lưu thay đổi", "I-save ang Pagbabago", "Guardar Cambios"),
        "Cancel" to MultiLangEntry("Cancelar", "Batal", "Hủy", "Kanselahin", "Cancelar"),
        "Close" to MultiLangEntry("Fechar", "Tutup", "Đóng", "Isara", "Cerrar"),
        "Retry" to MultiLangEntry("Tentar Novamente", "Coba Lagi", "Thử lại", "Subukan Muli", "Reintentar"),
        "Permissions & Feature Access" to MultiLangEntry(
            "Permissões e Acesso a Recursos",
            "Izin & Akses Fitur",
            "Quyền & Truy cập tính năng",
            "Mga Pahintulot at Access sa Feature",
            "Permisos y Acceso a Funciones"
        ),
        "Review what each permission grants and why features need it" to MultiLangEntry(
            "Veja o que cada permissão concede e por que os recursos precisam dela",
            "Tinjau fungsi setiap izin dan mengapa fitur membutuhkannya",
            "Xem chi tiết từng quyền và lý do tính năng cần quyền đó",
            "Suriin kung ano ang binibigay ng bawat pahintulot at bakit ito kailangan",
            "Revisa qué otorga cada permiso y por qué lo necesitan las funciones"
        ),
        "Open Unified Permissions Page" to MultiLangEntry(
            "Abrir Página de Permissões",
            "Buka Halaman Izin Terpadu",
            "Mở Trang Quản Lý Quyền",
            "Buksan ang Pahina ng Pahintulot",
            "Abrir Página de Permisos Unificada"
        ),
        "Tweak Safety Warnings" to MultiLangEntry(
            "Avisos de Segurança de Ajustes",
            "Peringatan Keamanan Tweak",
            "Cảnh báo an toàn tinh chỉnh",
            "Babala sa Kaligtasan ng Tweaks",
            "Advertencias de Seguridad de Ajustes"
        ),
        "Disclosure before applying performance tweaks" to MultiLangEntry(
            "Aviso antes de aplicar ajustes de desempenho",
            "Pemberitahuan sebelum menerapkan tweak performa",
            "Thông báo trước khi áp dụng tinh chỉnh hiệu năng",
            "Paalala bago gamitin ang performance tweaks",
            "Aviso antes de aplicar ajustes de rendimiento"
        ),
        "Show Warnings Before Activating" to MultiLangEntry(
            "Mostrar Avisos Antes de Ativar",
            "Tampilkan Peringatan Sebelum Aktif",
            "Hiển thị cảnh báo trước khi bật",
            "Ipakita ang Babala Bago I-activate",
            "Mostrar Advertencias Antes de Activar"
        ),
        "Alerts about online game anti-cheat notices, heat buildup, and animation changes" to MultiLangEntry(
            "Alertas sobre avisos anti-cheat em jogos online, aquecimento e alterações de animação",
            "Peringatan tentang anti-cheat game online, suhu panas, dan perubahan animasi",
            "Cảnh báo về chống gian lận game online, nhiệt độ máy và thay đổi hiệu ứng",
            "Mga alerto tungkol sa anti-cheat sa online games, pag-init, at pagbabago sa animation",
            "Alertas sobre avisos antitrampas en juegos en línea, temperatura y cambios de animación"
        ),
        "Automatic Language Sync • Device Locale Active" to MultiLangEntry(
            "Sincronização de Idioma • Multilíngue Ativo",
            "Sinkronisasi Bahasa Otomatis • Lokal Aktif",
            "Đồng bộ ngôn ngữ tự động • Đa ngôn ngữ",
            "Automatic Language Sync • Aktibo ang Wika",
            "Sincronización Automática de Idioma • Idioma Activo"
        ),
        "Version 1.0" to MultiLangEntry("Versão 1.0", "Versi 1.0", "Phiên bản 1.0", "Bersyon 1.0", "Versión 1.0"),

        // Hardware Specs & Diagnostics
        "Phone Hardware Specifications" to MultiLangEntry(
            "Especificações de Hardware do Celular",
            "Spesifikasi Perangkat Keras Ponsel",
            "Thông số phần cứng điện thoại",
            "Hardware Specs ng Telepono",
            "Especificaciones de Hardware del Teléfono"
        ),
        "Genuine detected hardware parameters" to MultiLangEntry(
            "Parâmetros reais de hardware detectados",
            "Parameter perangkat keras asli yang terdeteksi",
            "Thông số phần cứng thực tế được phát hiện",
            "Totoong na-detect na hardware parameters",
            "Parámetros de hardware genuinos detectados"
        ),
        "Processor / SoC" to MultiLangEntry("Processador / SoC", "Prosesor / SoC", "Vi xử lý / SoC", "Processor / SoC", "Procesador / SoC"),
        "Cores" to MultiLangEntry("Núcleos", "Inti", "Nhân", "Cores", "Núcleos"),
        "Graphics & 3D Engine" to MultiLangEntry("Gráficos e Motor 3D", "Grafis & Mesin 3D", "Đồ họa & Engine 3D", "Graphics at 3D Engine", "Motor Gráfico y 3D"),
        "Hardware Battery Thermals" to MultiLangEntry("Temperatura da Bateria", "Suhu Baterai Perangkat", "Nhiệt độ pin phần cứng", "Temperatura ng Baterya", "Temperatura de Batería de Hardware"),
        "Level" to MultiLangEntry("Nível", "Level", "Mức", "Antas", "Nivel"),
        "System RAM Headroom" to MultiLangEntry("Memória RAM do Sistema", "Kapasitas RAM Sistem", "Bộ nhớ RAM hệ thống", "System RAM Headroom", "Memoria RAM del Sistema"),
        "Available of" to MultiLangEntry("Disponíveis de", "Tersedia dari", "Khả dụng trên", "Available sa", "Disponibles de"),
        "Internal Flash Storage" to MultiLangEntry("Armazenamento Interno", "Penyimpanan Internal", "Bộ nhớ trong", "Internal Storage", "Almacenamiento Flash Interno"),
        "Free of" to MultiLangEntry("Livres de", "Kosong dari", "Trống trên", "Libre sa", "Libres de"),
        "Total" to MultiLangEntry("Total", "Total", "Tổng", "Kabuuan", "Total"),
        "Display & Refresh Rate" to MultiLangEntry("Tela e Taxa de Atualização", "Layar & Refresh Rate", "Màn hình & Tần số quét", "Display at Refresh Rate", "Pantalla y Tasa de Refresco"),
        "Device & Operating System" to MultiLangEntry("Dispositivo e Sistema Operacional", "Perangkat & Sistem Operasi", "Thiết bị & Hệ điều hành", "Device at Operating System", "Dispositivo y Sistema Operativo"),

        // Controller & Overlay Strings
        "Controller Layout & Button Icons" to MultiLangEntry(
            "Layout do Controle e Ícones de Botões",
            "Tata Letak Stik & Ikon Tombol",
            "Bố cục tay cầm & Biểu tượng nút",
            "Layout ng Controller at Icon ng Button",
            "Diseño del Control e Iconos de Botones"
        ),
        "Real-Time Controller Input Screen Overlay" to MultiLangEntry(
            "Sobreposição de Controle em Tempo Real",
            "Overlay Layar Input Stik Real-Time",
            "Lớp phủ tay cầm thời gian thực",
            "Real-Time Controller Input Overlay",
            "Superposición de Control en Tiempo Real"
        ),
        "Wired USB-C / OTG Connection Required" to MultiLangEntry(
            "Conexão USB-C / OTG com Fio Necessária",
            "Wajib Koneksi Kabel USB-C / OTG",
            "Yêu cầu kết nối cáp USB-C / OTG",
            "Kailangan ng Wired USB-C / OTG Connection",
            "Se Requiere Conexión por Cable USB-C / OTG"
        ),
        "Big Screen Console Mode Activated" to MultiLangEntry(
            "Modo Console em Tela Grande Ativado",
            "Mode Konsol Layar Besar Diaktifkan",
            "Đã bật Chế độ Console màn hình lớn",
            "Aktibo na ang Big Screen Console Mode",
            "Modo Consola en Pantalla Grande Activado"
        ),
        "Rotate phone to Landscape to activate Console Mode" to MultiLangEntry(
            "Gire o celular na horizontal para ativar o Modo Console",
            "Putar ponsel ke Lanskap untuk mengaktifkan Mode Konsol",
            "Xoay ngang điện thoại để bật Chế độ Console",
            "I-rotate ang telepono nang Pahiga para sa Console Mode",
            "Gira el teléfono en horizontal para activar el Modo Consola"
        ),
        "Console Mode UI Sounds (Controller Connected Only)" to MultiLangEntry(
            "Sons de Interface no Modo Console (Apenas com Controle)",
            "Suara UI Mode Konsol (Saat Stik Terhubung)",
            "Âm thanh giao diện Console (Khi kết nối tay cầm)",
            "Tunog ng Console Mode UI (Kapag May Controller)",
            "Sonidos de Interfaz en Modo Consola (Solo con Control)"
        ),
        "Plays tactile console navigation ticks, button confirmation chimes, and launch chords only when a controller is connected" to MultiLangEntry(
            "Reproduz sons táteis de navegação, confirmação de botões e acordes ao iniciar somente quando um controle está conectado",
            "Memutar suara navigasi konsol, konfirmasi tombol, dan efek peluncuran hanya saat stik terhubung",
            "Phát âm thanh điều hướng console, xác nhận nút và hiệu ứng khởi chạy khi có tay cầm kết nối",
            "Nagpapatugtog ng console navigation ticks at button chimes kapag may nakakonektang controller",
            "Reproduce sonidos táctiles de navegación, confirmación y lanzamiento solo cuando hay un control conectado"
        ),

        // Statuses
        "Connected" to MultiLangEntry("Conectado", "Terhubung", "Đã kết nối", "Nakakonekta", "Conectado"),
        "Disconnected" to MultiLangEntry("Desconectado", "Terputus", "Đã ngắt kết nối", "Hindi Nakakonekta", "Desconectado"),
        "Active" to MultiLangEntry("Ativo", "Aktif", "Đang bật", "Aktibo", "Activo"),
        "Inactive" to MultiLangEntry("Inativo", "Tidak Aktif", "Đang tắt", "Hindi Aktibo", "Inactivo"),
        "Enabled" to MultiLangEntry("Ativado", "Diaktifkan", "Đã bật", "Naka-enable", "Activado"),
        "Disabled" to MultiLangEntry("Desativado", "Dinonaktifkan", "Đã tắt", "Naka-disable", "Desactivado"),
        "Optimal" to MultiLangEntry("Ideal", "Optimal", "Tối ưu", "Optimal", "Óptimo"),
        "Warm" to MultiLangEntry("Morno", "Hangat", "Ấm", "Medyo Mainit", "Templado"),
        "Hot" to MultiLangEntry("Quente", "Panas", "Nóng", "Mainit", "Caliente"),
        "Critical" to MultiLangEntry("Crítico", "Kritis", "Nguy kịch", "Kritikal", "Crítico"),

        // Smart Push Notifications Titles & UI Labels
        "Smart Gaming Notifications (Max 4/Week)" to MultiLangEntry(
            "Notificações Inteligentes (Máx. 4/Semana)",
            "Notifikasi Gaming Pintar (Maks 4/Minggu)",
            "Thông báo Chơi game Thông minh (Tối đa 4/Tuần)",
            "Smart Gaming Notifications (Max 4/Linggo)",
            "Notificaciones Inteligentes (Máx. 4/Semana)"
        ),
        "Intelligent reminders for Clock Overlay, 4K 120fps Recorder, Peak Play-Time Modeling, Post-Session Wrap-Up, and Weekly Stats" to MultiLangEntry(
            "Lembretes inteligentes para Relógio na Tela, Gravador 4K 120fps, Horário de Jogo, Resumo Pós-Sessão e Estatísticas Semanais",
            "Pengingat cerdas untuk Overlay Jam, Perekam 4K 120fps, Waktu Main Puncak, Ringkasan Sesi, dan Statistik Mingguan",
            "Nhắc nhở thông minh cho Đồng hồ nổi, Ghi hình 4K 120fps, Giờ chơi cao điểm, Tổng kết phiên và Thống kê tuần",
            "Matalinong paalala para sa Clock Overlay, 4K 120fps Recorder, Peak Play-Time, Post-Session Wrap-Up, at Weekly Stats",
            "Recordatorios inteligentes de Reloj en Pantalla, Grabadora 4K 120fps, Hora Pico de Juego, Resumen de Sesión y Estadísticas Semanales"
        ),
        "Forgetting the Time after a long gaming session? Turn on your clock overlay to keep up with the time" to MultiLangEntry(
            "Perdendo a noção da hora após uma longa sessão de jogo? Ative o relógio na sobreposição para acompanhar o tempo",
            "Lupa waktu setelah sesi gaming panjang? Nyalakan overlay jam untuk tetap memantau waktu",
            "Quên thời gian sau phiên chơi game dài? Hãy bật đồng hồ trên lớp phủ để theo dõi thời gian",
            "Nakakalimutan ang oras pagkatapos ng mahabang laro? Buksan ang clock overlay para mabantayan ang oras",
            "¿Olvidas la hora después de una larga sesión de juego? Activa el reloj en pantalla para llevar el control del tiempo"
        ),
        "Don't miss a clutch moment. Enable the up to 4K 120fps screen recorder before you launch." to MultiLangEntry(
            "Não perca uma jogada decisiva. Ative o gravador de tela em até 4K 120fps antes de iniciar.",
            "Jangan lewatkan momen clutch. Aktifkan perekam layar hingga 4K 120fps sebelum memulai game.",
            "Đừng bỏ lỡ khoảnh khắc xuất thần. Hãy bật trình ghi màn hình lên đến 4K 120fps trước khi vào trận.",
            "Huwag palampasin ang clutch moment. I-enable ang hanggang 4K 120fps screen recorder bago maglaro.",
            "No te pierdas un momento épico. Activa la grabadora de pantalla de hasta 4K 120fps antes de jugar."
        ),
        "Time to play? Launch through GMAX to clear background RAM and lock in your frame rate." to MultiLangEntry(
            "Hora de jogar? Inicie pelo GMAX para limpar a RAM em segundo plano e estabilizar sua taxa de quadros.",
            "Waktunya main? Mulai lewat GMAX untuk membersihkan RAM latar belakang dan mengunci frame rate Anda.",
            "Đến giờ chiến game? Khởi chạy qua GMAX để dọn RAM nền và khóa tốc độ khung hình ổn định.",
            "Oras na para maglaro? Ilunsad sa GMAX para linisin ang background RAM at i-lock ang iyong frame rate.",
            "¿Hora de jugar? Inicia desde GMAX para liberar RAM en segundo plano y asegurar tus FPS."
        ),
        "Your games are missing the boost. Jump back in for a lag free session." to MultiLangEntry(
            "Seus jogos estão sentindo falta do impulso. Volte a jogar para uma sessão sem travamentos.",
            "Game Anda merindukan boost. Kembali bermain untuk sesi bebas lag.",
            "Các trò chơi đang chờ bạn tăng tốc. Quay lại ngay để trải nghiệm phiên chơi mượt mà không giật lag.",
            "Na-miss na ng mga laro mo ang boost. Bumalik na para sa lag-free na gaming session.",
            "Tus juegos extrañan el impulso. Vuelve a jugar para una sesión sin lag."
        ),
        "Gameplay stuttering lately? Run a quick diagnostic to optimize your device right now." to MultiLangEntry(
            "O jogo está engasgando ultimamente? Execute um diagnóstico rápido para otimizar seu aparelho agora mesmo.",
            "Gameplay sering patah-patah belakangan ini? Jalankan diagnostik cepat untuk mengoptimalkan perangkat Anda sekarang.",
            "Chơi game gần đây bị giật khung hình? Chạy chẩn đoán nhanh để tối ưu hóa thiết bị của bạn ngay bây giờ.",
            "Nagla-lag ba ang laro mo kamakailan? Magpatakbo ng mabilis na diagnostic para i-optimize ang device mo ngayon.",
            "¿Tu juego tiene tirones últimamente? Ejecuta un diagnóstico rápido para optimizar tu dispositivo ahora mismo."
        )
    )

    private val multiLangPhraseReplacements = listOf(
        PhraseRule("No controller detected", "Nenhum controle detectado", "Tidak ada stik terdeteksi", "Không phát hiện tay cầm", "Walang na-detect na controller"),
        PhraseRule("Controller Connected", "Controle Conectado", "Stik Terhubung", "Đã kết nối tay cầm", "Nakakonekta ang Controller"),
        PhraseRule("Controller Overlay", "Sobreposição de Controle", "Overlay Stik", "Lớp phủ tay cầm", "Controller Overlay"),
        PhraseRule("Custom RGB Lightbar Mixer", "Mixer de Luz RGB Personalizado", "Mixer Lampu RGB Kustom", "Bộ trộn đèn RGB tùy chỉnh", "Custom RGB Lightbar Mixer"),
        PhraseRule("Controller Battery Level", "Nível de Batería do Controle", "Level Baterai Stik", "Mức pin tay cầm", "Antas ng Baterya ng Controller"),
        PhraseRule("Real Rumble Tester", "Testador de Vibração Real", "Penguji Getar Asli", "Kiểm tra rung thực tế", "Real Rumble Tester"),
        PhraseRule("Touch Pipeline Latency Test", "Teste de Latência de Toque", "Tes Latensi Sentuhan", "Kiểm tra độ trễ cảm ứng", "Touch Latency Test"),
        PhraseRule("Phone Hardware Specifications", "Especificações de Hardware", "Spesifikasi Perangkat Keras", "Thông số phần cứng", "Hardware Specifications"),
        PhraseRule("Device & Operating System", "Dispositivo e Sistema", "Perangkat & OS", "Thiết bị & Hệ điều hành", "Device at OS"),
        PhraseRule("Memory & Storage", "Memória e Armazenamento", "Memori & Penyimpanan", "Bộ nhớ & Lưu trữ", "Memory at Storage"),
        PhraseRule("Display & Graphics", "Tela e Gráficos", "Layar & Grafis", "Màn hình & Đồ họa", "Display at Graphics"),
        PhraseRule("Battery & Thermal", "Bateria e Temperatura", "Baterai & Suhu", "Pin & Nhiệt độ", "Baterya at Temperatura"),
        PhraseRule("Total Gaming Time", "Tempo Total de Jogo", "Total Waktu Main", "Tổng thời gian chơi", "Kabuuang Oras ng Laro"),
        PhraseRule("Most Played Games", "Jogos Mais Jogados", "Game Paling Sering Dimainkan", "Trò chơi chơi nhiều nhất", "Pinakamadalas Laruin"),
        PhraseRule("Gamerscore", "Pontuação Gamer", "Skor Gamer", "Điểm Gamer", "Gamerscore"),
        PhraseRule("Season Leaderboard", "Classificação da Temporada", "Papan Skor Musim", "Bảng xếp hạng mùa", "Season Leaderboard"),
        PhraseRule("Tournament Rules", "Regras do Torneio", "Aturan Turnamen", "Luật giải đấu", "Mga Patakaran sa Tournament"),
        PhraseRule("Boosting & Launching", "Otimizando e Iniciando", "Mengoptimalkan & Memulai", "Đang tối ưu & Khởi chạy", "Nagbo-boost at Naglulunsad"),
        PhraseRule("Screen Overlay HUD", "HUD de Sobreposição na Tela", "HUD Overlay Layar", "HUD Lớp phủ màn hình", "Screen Overlay HUD"),
        PhraseRule("Clock Overlay", "Relógio na Sobreposição", "Overlay Jam", "Đồng hồ lớp phủ", "Clock Overlay"),
        PhraseRule("Screen Recorder", "Gravador de Tela", "Perekam Layar", "Trình ghi màn hình", "Screen Recorder"),
        PhraseRule("Permissions", "Permissões", "Izin", "Quyền", "Mga Pahintulot"),
        PhraseRule("Settings", "Configurações", "Pengaturan", "Cài đặt", "Settings"),
        PhraseRule("available", "disponíveis", "tersedia", "khả dụng", "available"),
        PhraseRule("freed", "liberados", "dibebaskan", "đã giải phóng", "napalaya"),
        PhraseRule("Optimized", "Otimizado", "Dioptimalkan", "Đã tối ưu", "Na-optimize")
    )
}

@Composable
@ReadOnlyComposable
fun currentAppLanguage(): AppLanguage {
    val config = LocalConfiguration.current
    val localLang = LocalAppLanguage.current
    return if (localLang != AppLanguage.AUTO) {
        localLang
    } else {
        AppStrings.resolveActiveLanguage(config)
    }
}

@Composable
@ReadOnlyComposable
fun isSpanishLanguage(): Boolean {
    return currentAppLanguage() == AppLanguage.ES_MX
}

@Composable
@ReadOnlyComposable
fun tr(
    en: String,
    es: String,
    ptBr: String? = null,
    id: String? = null,
    vi: String? = null,
    tl: String? = null
): String {
    val active = currentAppLanguage()
    return AppStrings.translateForLanguage(
        en = en,
        es = es,
        ptBr = ptBr,
        id = id,
        vi = vi,
        tl = tl,
        active = active
    )
}
