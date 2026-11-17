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
    AUTO("auto", "System Language", "Follows your device language automatically", "AUTO"),
    EN("en", "English", "English (US / Global)", "EN"),
    ES_MX("es-MX", "Español (México)", "Spanish (Mexico) • es MX", "ES MX"),
    ES_LA("es-419", "Español (Latinoamérica)", "Spanish (Latin America) • es 419", "ES LA"),
    JA("ja", "日本語", "Japanese • ja", "JA"),
    HI("hi", "हिन्दी", "Hindi • hi", "HI"),
    PT_BR("pt-BR", "Português (Brasil)", "Brazilian Portuguese • pt BR", "PT BR"),
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
                normalized == "es-mx" || normalized == "es_mx" -> ES_MX
                normalized == "es-419" || normalized == "es_419" ||
                    normalized == "es-la" || normalized == "es_la" ||
                    normalized == "es-ar" || normalized == "es-co" ||
                    normalized == "es-cl" || normalized == "es-pe" -> ES_LA
                normalized.startsWith("es") -> ES_MX
                normalized == "ja" || normalized == "jp" ||
                    normalized.startsWith("ja-") || normalized.startsWith("ja_") -> JA
                normalized == "hi" || normalized.startsWith("hi-") || normalized.startsWith("hi_") -> HI
                normalized == "pt-br" || normalized == "pt_br" || normalized.startsWith("pt") -> PT_BR
                normalized == "id" || normalized == "in" || normalized.startsWith("id-") || normalized.startsWith("in-") -> ID
                normalized == "vi" || normalized.startsWith("vi-") || normalized.startsWith("vi_") -> VI
                normalized == "tl" || normalized == "fil" || normalized.startsWith("tl-") || normalized.startsWith("fil-") -> TL
                else -> entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: AUTO
            }
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.AUTO }

data class MultiLangEntry(
    val ptBr: String,
    val id: String,
    val vi: String,
    val tl: String,
    val esMx: String? = null,
    val esLa: String? = null,
    val ja: String? = null,
    val hi: String? = null
)

/**
 * Multi-Language Localization Engine for G Max Gaming Hub.
 * Supports Automatic Device-Locale Detection + Manual Language Selection in Settings for:
 * - English (en)
 * - Spanish - Mexico (es-MX)
 * - Spanish - Latin America (es-419)
 * - Japanese (ja)
 * - Hindi (hi)
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
        val country = locale.country.uppercase(Locale.ROOT)
        return when {
            lang.startsWith("pt") -> AppLanguage.PT_BR
            lang.startsWith("es") -> {
                if (country.isNotBlank() && country != "MX" && country != "US") {
                    AppLanguage.ES_LA
                } else {
                    AppLanguage.ES_MX
                }
            }
            lang.startsWith("ja") || lang == "jp" -> AppLanguage.JA
            lang.startsWith("hi") -> AppLanguage.HI
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
        val effective = if (lang == AppLanguage.AUTO) resolveActiveLanguage() else lang
        return effective != AppLanguage.EN
    }

    fun isSpanish(configuration: Configuration? = null): Boolean {
        val active = resolveActiveLanguage(configuration)
        return active == AppLanguage.ES_MX || active == AppLanguage.ES_LA
    }

    fun isSpanishContext(context: Context): Boolean {
        val active = resolveActiveLanguageContext(context)
        return active == AppLanguage.ES_MX || active == AppLanguage.ES_LA
    }

    fun trText(
        en: String,
        es: String,
        ptBr: String? = null,
        id: String? = null,
        vi: String? = null,
        tl: String? = null,
        esLa: String? = null,
        ja: String? = null,
        hi: String? = null,
        languageOverride: AppLanguage? = null
    ): String {
        val active = languageOverride ?: resolveActiveLanguage()
        return translateForLanguage(en, es, ptBr, id, vi, tl, active, esLa, ja, hi)
    }

    fun translateForLanguage(
        en: String,
        es: String,
        ptBr: String? = null,
        id: String? = null,
        vi: String? = null,
        tl: String? = null,
        active: AppLanguage,
        esLa: String? = null,
        ja: String? = null,
        hi: String? = null
    ): String {
        val effective = if (active == AppLanguage.AUTO) resolveActiveLanguage() else active
        val raw = when (effective) {
            AppLanguage.EN, AppLanguage.AUTO -> en
            AppLanguage.ES_MX -> multiLangCatalog[en]?.esMx
                ?: es.takeIf { it.isNotBlank() && it != en }
                ?: exactSpanishDictionary[en]
                ?: autoTranslateFallback(en, AppLanguage.ES_MX)
            AppLanguage.ES_LA -> esLa
                ?: multiLangCatalog[en]?.esLa
                ?: latinSpanishOverrides[en]
                ?: adaptMexicanToLatinSpanish(
                    multiLangCatalog[en]?.esMx
                        ?: es.takeIf { it.isNotBlank() && it != en }
                        ?: exactSpanishDictionary[en]
                        ?: autoTranslateFallback(en, AppLanguage.ES_LA)
                )
            AppLanguage.JA -> ja
                ?: multiLangCatalog[en]?.ja
                ?: exactJapaneseDictionary[en]
                ?: autoTranslateFallback(en, AppLanguage.JA)
            AppLanguage.HI -> hi
                ?: multiLangCatalog[en]?.hi
                ?: exactHindiDictionary[en]
                ?: autoTranslateFallback(en, AppLanguage.HI)
            AppLanguage.PT_BR -> ptBr ?: multiLangCatalog[en]?.ptBr ?: autoTranslateFallback(en, AppLanguage.PT_BR)
            AppLanguage.ID -> id ?: multiLangCatalog[en]?.id ?: autoTranslateFallback(en, AppLanguage.ID)
            AppLanguage.VI -> vi ?: multiLangCatalog[en]?.vi ?: autoTranslateFallback(en, AppLanguage.VI)
            AppLanguage.TL -> tl ?: multiLangCatalog[en]?.tl ?: autoTranslateFallback(en, AppLanguage.TL)
        }
        val expanded = if (effective != AppLanguage.EN && effective != AppLanguage.AUTO) {
            expandTechnicalAcronyms(raw, effective)
        } else {
            raw
        }
        return sanitizeDisplayedText(expanded)
    }

    fun sanitizeDisplayedText(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(" — ", " • ")
            .replace(" – ", " • ")
            .replace(" - ", " • ")
            .replace("—", " ")
            .replace("–", " to ")
            .replace("‒", " ")
            .replace("―", " ")
    }

    private fun adaptMexicanToLatinSpanish(esMxText: String): String {
        var result = esMxText
        if (result.contains("Español (es-MX)")) {
            result = result.replace("Español (es-MX)", "Español (Latinoamérica)")
        }
        return result
    }

    /**
     * Automatic phrase/word translator for dynamic messages, section titles, gauges, and status logs.
     */
    fun autoTranslateDynamic(text: String, languageOverride: AppLanguage? = null): String {
        if (text.isBlank()) return text
        val rawActive = languageOverride ?: resolveActiveLanguage()
        val active = if (rawActive == AppLanguage.AUTO) resolveActiveLanguage() else rawActive
        if (active == AppLanguage.EN || active == AppLanguage.AUTO) return sanitizeDisplayedText(text)

        if (active == AppLanguage.ES_MX || active == AppLanguage.ES_LA) {
            if (active == AppLanguage.ES_LA) {
                latinSpanishOverrides[text]?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
                multiLangCatalog[text]?.esLa?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
            }
            multiLangCatalog[text]?.esMx?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
            exactSpanishDictionary[text]?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
            var translated = text
            for ((enPhrase, esPhrase) in spanishPhraseReplacements) {
                if (translated.contains(enPhrase, ignoreCase = false)) {
                    translated = translated.replace(enPhrase, esPhrase)
                }
            }
            translated = autoTranslateFallback(translated, active)
            return sanitizeDisplayedText(expandTechnicalAcronyms(translated, active))
        }
        if (active == AppLanguage.JA) {
            exactJapaneseDictionary[text]?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
        }
        if (active == AppLanguage.HI) {
            exactHindiDictionary[text]?.let { return sanitizeDisplayedText(expandTechnicalAcronyms(it, active)) }
        }
        val catalogHit = multiLangCatalog[text]
        if (catalogHit != null) {
            val resolved = when (active) {
                AppLanguage.JA -> catalogHit.ja ?: exactJapaneseDictionary[text] ?: autoTranslateFallback(text, AppLanguage.JA)
                AppLanguage.HI -> catalogHit.hi ?: exactHindiDictionary[text] ?: autoTranslateFallback(text, AppLanguage.HI)
                AppLanguage.PT_BR -> catalogHit.ptBr
                AppLanguage.ID -> catalogHit.id
                AppLanguage.VI -> catalogHit.vi
                AppLanguage.TL -> catalogHit.tl
                else -> text
            }
            return sanitizeDisplayedText(expandTechnicalAcronyms(resolved, active))
        }
        return sanitizeDisplayedText(expandTechnicalAcronyms(autoTranslateFallback(text, active), active))
    }

    private val preservedTechnicalTokens = setOf(
        "GMAX", "PNG", "MP4", "DISCORD", "WHATSAPP"
    )

    private data class AcronymExpansion(
        val regex: Regex,
        val esMx: String,
        val esLa: String,
        val ja: String,
        val hi: String,
        val ptBr: String,
        val id: String,
        val vi: String,
        val tl: String
    ) {
        fun forLanguage(lang: AppLanguage): String = when (lang) {
            AppLanguage.ES_MX -> esMx
            AppLanguage.ES_LA -> esLa
            AppLanguage.JA -> ja
            AppLanguage.HI -> hi
            AppLanguage.PT_BR -> ptBr
            AppLanguage.ID -> id
            AppLanguage.VI -> vi
            AppLanguage.TL -> tl
            else -> ""
        }
    }

    private val technicalAcronymExpansions = listOf(
        AcronymExpansion(
            Regex("""\bEFPS\b"""),
            esMx = "Cuadros Efectivos por Segundo",
            esLa = "Fotogramas Efectivos por Segundo",
            ja = "実効フレーム毎秒",
            hi = "प्रभावी फ़्रेम प्रति सेकंड",
            ptBr = "Quadros Efetivos por Segundo",
            id = "Bingkai Efektif per Detik",
            vi = "Khung Hình Hiệu Dụng Mỗi Giây",
            tl = "Epektibong Kuwadro Bawat Segundo"
        ),
        AcronymExpansion(
            Regex("""\bFPS\b"""),
            esMx = "Cuadros por Segundo",
            esLa = "Fotogramas por Segundo",
            ja = "毎秒フレーム数",
            hi = "फ़्रेम प्रति सेकंड",
            ptBr = "Quadros por Segundo",
            id = "Bingkai per Detik",
            vi = "Khung Hình Mỗi Giây",
            tl = "Kuwadro Bawat Segundo"
        ),
        AcronymExpansion(
            Regex("""\bRAM\b"""),
            esMx = "Memoria de Acceso Aleatorio",
            esLa = "Memoria de Acceso Aleatorio",
            ja = "ランダムアクセスメモリ",
            hi = "रैंडम एक्सेस मेमोरी",
            ptBr = "Memória de Acesso Aleatório",
            id = "Memori Akses Acak",
            vi = "Bộ Nhớ Truy Cập Ngẫu Nhiên",
            tl = "Memorya ng Random na Access"
        ),
        AcronymExpansion(
            Regex("""\bCPU\b"""),
            esMx = "Procesador Central",
            esLa = "Unidad Central de Procesamiento",
            ja = "中央演算処理装置",
            hi = "केंद्रीय प्रोसेसर",
            ptBr = "Processador Central",
            id = "Unit Pemrosesan Pusat",
            vi = "Bộ Vi Xử Lý Trung Tâm",
            tl = "Sentral na Prosesor"
        ),
        AcronymExpansion(
            Regex("""\bGPU\b"""),
            esMx = "Procesador Gráfico",
            esLa = "Unidad de Procesamiento Gráfico",
            ja = "画像処理装置",
            hi = "ग्राफ़िक्स प्रोसेसर",
            ptBr = "Processador Gráfico",
            id = "Unit Pemrosesan Grafis",
            vi = "Bộ Xử Lý Đồ Họa",
            tl = "Prosesor ng Grapiko"
        ),
        AcronymExpansion(
            Regex("""\bDNS\b"""),
            esMx = "Sistema de Nombres de Dominio",
            esLa = "Sistema de Nombres de Dominio",
            ja = "ドメインネームシステム",
            hi = "डोमेन नाम प्रणाली",
            ptBr = "Sistema de Nomes de Domínio",
            id = "Sistem Nama Domain",
            vi = "Hệ Thống Phân Giải Tên Miền",
            tl = "Sistema ng Pangalan ng Domain"
        ),
        AcronymExpansion(
            Regex("""\bADB\b"""),
            esMx = "Puente de Depuración de Android",
            esLa = "Puente de Depuración de Android",
            ja = "Androidデバッグブリッジ",
            hi = "एंड्रॉइड डीबग ब्रिज",
            ptBr = "Ponte de Depuração do Android",
            id = "Jembatan Debug Android",
            vi = "Cầu Gỡ Lỗi Android",
            tl = "Tulay ng Pag-debug sa Android"
        ),
        AcronymExpansion(
            Regex("""\bHUD\b"""),
            esMx = "Panel Visual en Pantalla",
            esLa = "Indicador Visual en Pantalla",
            ja = "画面ヘッドアップ表示",
            hi = "स्क्रीन सूचना प्रदर्शन",
            ptBr = "Painel Visual na Tela",
            id = "Tampilan Informasi Layar",
            vi = "Bảng Hiển Thị Trên Màn Hình",
            tl = "impormasyon sa Ibabaw ng Screen"
        ),
        AcronymExpansion(
            Regex("""\bRGB\b"""),
            esMx = "Luz Roja Verde y Azul",
            esLa = "Luz Roja Verde y Azul",
            ja = "赤緑青フルカラー照明",
            hi = "लाल हरा नीला प्रकाश",
            ptBr = "Luz Vermelha Verde e Azul",
            id = "Cahaya Merah Hijau Biru",
            vi = "Ánh Sáng Đỏ Lục Lam",
            tl = "Ilaw na Pula Berde at Asul"
        ),
        AcronymExpansion(
            Regex("""\bDND\b"""),
            esMx = "No Molestar",
            esLa = "No Molestar",
            ja = "おやすみモード",
            hi = "डू नॉट डिस्टर्ब मोड",
            ptBr = "Não Perturbe",
            id = "Jangan Ganggu",
            vi = "Không Làm Phiền",
            tl = "Huwag Istorbohin"
        ),
        AcronymExpansion(
            Regex("""\bSoC\b"""),
            esMx = "Sistema en Chip",
            esLa = "Sistema en Chip",
            ja = "システムオンチップ",
            hi = "सिस्टम ऑन चिप",
            ptBr = "Sistema em Chip",
            id = "Sistem pada Chip",
            vi = "Hệ Thống Trên Vi Mạch",
            tl = "Sistema sa Chip"
        ),
        AcronymExpansion(
            Regex("""\bVRR\b"""),
            esMx = "Tasa de Refresco Variable",
            esLa = "Tasa de Refresco Variable",
            ja = "可変リフレッシュレート",
            hi = "परिवर्तनीय रिफ़्रेश दर",
            ptBr = "Taxa de Atualização Variável",
            id = "Laju Penyegaran Variabel",
            vi = "Tần Số Quét Biến Thiên",
            tl = "Pabago-bagong Bilis ng Pag-refresh"
        ),
        AcronymExpansion(
            Regex("""\bVSYNC\b"""),
            esMx = "Sincronización Vertical",
            esLa = "Sincronización Vertical",
            ja = "垂直同期",
            hi = "वर्टिकल सिंक्रोनाइज़ेशन",
            ptBr = "Sincronização Vertical",
            id = "Sinkronisasi Vertikal",
            vi = "Đồng Bộ Dọc",
            tl = "Patayong Pag-sync"
        )
    )

    fun expandTechnicalAcronyms(text: String, target: AppLanguage): String {
        if (target == AppLanguage.EN || target == AppLanguage.AUTO || text.isBlank()) return text
        val savedCodes = mutableListOf<String>()
        var result = referralCodeRegex.replace(text) { match ->
            val idx = savedCodes.size
            savedCodes.add(match.value)
            "__REF_CODE_${idx}__"
        }
        for (rule in technicalAcronymExpansions) {
            val replacement = rule.forLanguage(target)
            if (replacement.isNotEmpty() && rule.regex.containsMatchIn(result)) {
                result = rule.regex.replace(result, replacement)
            }
        }
        savedCodes.forEachIndexed { idx, code ->
            result = result.replace("__REF_CODE_${idx}__", code)
        }
        return result
    }

    private val englishWordTokenRegex = Regex("""\b[A-Za-z][A-Za-z'-]*\b""")
    private val referralCodeRegex = Regex("""GMAX-[A-Z0-9_-]{3,25}""")

    private fun autoTranslateFallback(enText: String, target: AppLanguage): String {
        val direct = multiLangCatalog[enText]
        if (direct != null) {
            return when (target) {
                AppLanguage.JA -> direct.ja ?: exactJapaneseDictionary[enText] ?: enText
                AppLanguage.HI -> direct.hi ?: exactHindiDictionary[enText] ?: enText
                AppLanguage.ES_LA -> direct.esLa ?: direct.esMx ?: enText
                AppLanguage.ES_MX -> direct.esMx ?: enText
                AppLanguage.PT_BR -> direct.ptBr
                AppLanguage.ID -> direct.id
                AppLanguage.VI -> direct.vi
                AppLanguage.TL -> direct.tl
                else -> enText
            }
        }
        if (target == AppLanguage.JA) {
            exactJapaneseDictionary[enText]?.let { return it }
        }
        if (target == AppLanguage.HI) {
            exactHindiDictionary[enText]?.let { return it }
        }
        if (target == AppLanguage.ES_MX || target == AppLanguage.ES_LA) {
            exactSpanishDictionary[enText]?.let { return it }
        }

        // Preserve any GMAX-XXXX-YYYY referral codes intact during translation
        val savedCodes = mutableListOf<String>()
        var result = referralCodeRegex.replace(enText) { match ->
            val idx = savedCodes.size
            savedCodes.add(match.value)
            "__REF_CODE_${idx}__"
        }

        // Step 1: Multi-word phrase replacements (longest phrases first)
        for (rule in multiLangPhraseReplacements) {
            if (result.contains(rule.en, ignoreCase = true)) {
                val rep = rule.forLanguage(target)
                result = Regex(Regex.escape(rule.en), RegexOption.IGNORE_CASE).replace(result) { rep }
            }
        }

        // Step 2: Token-by-token vocabulary translation for any remaining English words
        result = englishWordTokenRegex.replace(result) { match ->
            val token = match.value
            val upper = token.uppercase(Locale.ROOT)
            if (preservedTechnicalTokens.contains(upper) || token.startsWith("__REF_CODE_")) {
                token
            } else {
                val lower = token.lowercase(Locale.ROOT)
                val vocabRule = multiLangWordVocabulary[lower]
                    ?: if (lower.endsWith("s") && lower.length > 3) multiLangWordVocabulary[lower.dropLast(1)] else null
                if (vocabRule != null) {
                    vocabRule.forLanguage(target)
                } else {
                    token
                }
            }
        }

        // Restore preserved referral codes
        savedCodes.forEachIndexed { idx, code ->
            result = result.replace("__REF_CODE_${idx}__", code)
        }
        return result
    }

    private data class PhraseRule(
        val en: String,
        val ptBr: String,
        val id: String,
        val vi: String,
        val tl: String,
        val ja: String,
        val hi: String,
        val esLa: String
    ) {
        fun forLanguage(target: AppLanguage): String = when (target) {
            AppLanguage.JA -> ja
            AppLanguage.HI -> hi
            AppLanguage.ES_LA, AppLanguage.ES_MX -> esLa
            AppLanguage.PT_BR -> ptBr
            AppLanguage.ID -> id
            AppLanguage.VI -> vi
            AppLanguage.TL -> tl
            else -> en
        }
    }

    private val latinSpanishOverrides = mapOf(
        "Controller" to "Mando / Control",
        "Controller Overlay" to "Superposición de Mando",
        "No controller detected" to "No se detectó ningún mando",
        "Controller Connected" to "Mando Conectado",
        "Controller Disconnected" to "Mando Desconectado",
        "Controller Battery Level" to "Nivel de Batería del Mando",
        "Controller Layout & Button Icons" to "Diseño del Mando e Iconos de Botones",
        "Real-Time Controller Input Screen Overlay" to "Superposición de Mando en Tiempo Real",
        "Console Mode UI Sounds (Controller Connected Only)" to "Sonidos de Interfaz en Modo Consola (Solo con Mando)"
    )

    private val exactSpanishDictionary = mapOf(
        "Launcher" to "Lanzador",
        "Recorder" to "Grabadora",
        "Top Gear" to "Top Gear",
        "Leaderboard" to "Clasificación",
        "Extras" to "Extras",
        "Settings" to "Ajustes",
        "Profile" to "Perfil",
        "Tweaks" to "Ajustes Pro",
        "Performance" to "Rendimiento",
        "Touch" to "Táctil",
        "Controller" to "Control",
        "Specs" to "Especificaciones",
        "Squad Pass" to "Pase de Escuadrón",
        "Normal" to "Normal",
        "Focus Mode" to "Modo Enfoque",
        "Overlay and Focus Mode Timer" to "Temporizador de Superposición y Modo Enfoque",
        "Optimize Memory" to "Optimizar RAM",
        "Starting Focus Mode..." to "Iniciando Modo Enfoque...",
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
        "Share" to "Compartir",
        "Copy" to "Copiar",
        "COPY CODE" to "COPIAR CÓDIGO",
        "CLAIM PASS" to "CANJEAR PASE",
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
        "Unknown" to "Desconocido",
        "High Impact" to "Impacto Alto",
        "Medium Impact" to "Impacto Medio",
        "Low Impact" to "Impacto Bajo"
    )

    private val exactJapaneseDictionary = mapOf(
        "Launcher" to "ランチャー",
        "Recorder" to "レコーダー",
        "Top Gear" to "トップギア",
        "Leaderboard" to "ランキング",
        "Extras" to "エクストラ",
        "Settings" to "設定",
        "Profile" to "プロフィール",
        "Tweaks" to "プロ調整",
        "Performance" to "パフォーマンス",
        "Touch" to "タッチ",
        "Controller" to "コントローラー",
        "Specs" to "スペック",
        "Squad Pass" to "スクワッドパス",
        "FPS & Diag" to "FPS・診断",
        "Normal" to "ノーマル",
        "Focus Mode" to "フォーカスモード",
        "Overlay and Focus Mode Timer" to "オーバーレイ＆フォーカスモードタイマー",
        "Optimize Memory" to "RAM最適化",
        "Starting Focus Mode..." to "フォーカスモード起動中...",
        "Launch" to "起動",
        "Focus Mode & Launch" to "フォーカスモードで起動",
        "Add Games" to "ゲーム追加",
        "Manage Library" to "ライブラリ管理",
        "Search games..." to "ゲームを検索...",
        "Search game or app..." to "ゲームやアプリを検索...",
        "SELECT TARGET GAME" to "対象ゲームを選択",
        "Auto-launches with Focus Mode & Overlay" to "フォーカスモードとオーバーレイで自動起動します",
        "No apps found matching search." to "一致するアプリが見つかりません。",
        "GAME" to "ゲーム",
        "Selected" to "選択済み",
        "Recently Played" to "最近プレイしたゲーム",
        "Most Played" to "最もプレイしたゲーム",
        "Most Played Games" to "プレイ時間トップのゲーム",
        "Total Gaming Time" to "合計プレイ時間",
        "Game Library" to "ゲームライブラリ",
        "Selected Game" to "選択中のゲーム",
        "Videos & Gallery" to "動画＆ギャラリー",
        "No videos have been recorded" to "録画された動画はありません",
        "Record" to "録画",
        "Stop" to "停止",
        "Screenshot" to "スクリーンショット",
        "Screen Recorder" to "画面レコーダー",
        "Recording Settings" to "録画設定",
        "Resolution" to "解像度",
        "Frame Rate" to "フレームレート",
        "Bitrate" to "ビットレート",
        "Audio Source" to "音声ソース",
        "Recording saved to Videos!" to "動画を保存しました！",
        "Hub Appearance & Settings" to "Hubの外観と設定",
        "App Language & Region" to "アプリの言語と地域",
        "Auto-Detect (System)" to "自動検出（システム設定）",
        "System Language" to "システム言語（自動）",
        "Display Mode & Background" to "表示モードと背景",
        "Signed-In Account" to "サインイン済みアカウント",
        "Signed In Account" to "サインイン済みアカウント",
        "Guest Profile (Local Settings Only)" to "ゲストプロフィール（ローカル設定のみ）",
        "Account" to "アカウント",
        "Sign In" to "サインイン",
        "Sign Out" to "サインアウト",
        "Edit Profile" to "プロフィール編集",
        "Save Changes" to "変更を保存",
        "Cancel" to "キャンセル",
        "Close" to "閉じる",
        "Retry" to "再試行",
        "Share" to "共有",
        "Copy" to "コピー",
        "COPY" to "コピー",
        "COPY CODE" to "コードコピー",
        "CLAIM PASS" to "パスを受け取る",
        "Ladder" to "ティア報酬",
        "SQUAD" to "スクワッド",
        "VERIFIED" to "認証済み",
        "PENDING" to "保留中",
        "UNLOCKED" to "解除済み",
        "LOCKED" to "未解除",
        "LEFT" to "残り",
        "CODE" to "コード",
        "Connected" to "接続済み",
        "Disconnected" to "未接続",
        "Controller Connected" to "コントローラー接続済み",
        "Controller Disconnected" to "コントローラー切断",
        "is ready for navigation & gaming" to "が操作とゲームに利用可能です",
        "was disconnected" to "が切断されました",
        "Active" to "有効",
        "Inactive" to "無効",
        "Enabled" to "オン",
        "Disabled" to "オフ",
        "Optimal" to "最適",
        "Warm" to "やや高温",
        "Hot" to "高温",
        "Critical" to "危険温度",
        "High Impact" to "効果：大",
        "Medium Impact" to "効果：中",
        "Low Impact" to "効果：小",
        "ADB Command Copied!" to "ADBコマンドをコピーしました！",
        "Copy Command" to "コマンドをコピー",
        "Base64 HUD & Crosshair Config Codes" to "Base64 HUD＆クロスヘア設定コード",
        "Copy Code" to "コードをコピー",
        "Share Code" to "コードを共有",
        "Decode & Apply Layout Instantly" to "デコードして設定を即時適用",
        "Tactical Screen Crosshair" to "タクティカル画面クロスヘア",
        "Hand-picked gear for mobile gamers" to "モバイルゲーマー向けに厳選されたギア",
        "As an Amazon Associate I earn from qualifying purchases." to "Amazonアソシエイトとして、適格販売により収入を得ています。",
        "View on Amazon" to "Amazonで見る",
        "Key Benefits" to "主なメリット",
        "BEST FOR" to "おすすめ用途",
        "TRADE-OFF" to "注意点",
        "Best For" to "おすすめ用途",
        "Trade-off" to "注意点",
        "Main Benefit" to "主なメリット",
        "Price Level" to "価格帯",
        "Compare" to "比較",
        "Hide Compare" to "比較を閉じる",
        "Side-by-Side Tier Comparison" to "ティア別徹底比較",
        "Show All Gear" to "すべてのギアを表示",
        "Anonymous Gear Tap Logging" to "匿名ギアタップ統計",
        "INTEGRITY VIOLATION" to "整合性エラー検出",
        "Unauthorized Modification Detected" to "不正な改ざんが検出されました",
        "SECURITY DIAGNOSTIC" to "セキュリティ診断",
        "Close Application" to "アプリを終了"
    )

    private val exactHindiDictionary = mapOf(
        "Launcher" to "लॉन्चर",
        "Recorder" to "रिकॉर्डर",
        "Top Gear" to "टॉप गियर",
        "Leaderboard" to "लीडरबोर्ड",
        "Extras" to "एक्स्ट्रा",
        "Settings" to "सेटिंग्स",
        "Profile" to "प्रोफ़ाइल",
        "Tweaks" to "प्रो ट्वीक्स",
        "Performance" to "परफॉर्मेंस",
        "Touch" to "टच",
        "Controller" to "कंट्रोलर",
        "Specs" to "स्पेक्स",
        "Squad Pass" to "स्क्वाड पास",
        "FPS & Diag" to "FPS और जांच",
        "Normal" to "नॉर्मल",
        "Focus Mode" to "फ़ोकस मोड",
        "Overlay and Focus Mode Timer" to "ओवरले और फ़ोकस मोड टाइमर",
        "Optimize Memory" to "RAM ऑप्टिमाइज़ करें",
        "Starting Focus Mode..." to "फ़ोकस मोड शुरू हो रहा है...",
        "Launch" to "खेलें",
        "Focus Mode & Launch" to "फ़ोकस मोड और लॉन्च करें",
        "Add Games" to "गेम जोड़ें",
        "Manage Library" to "लाइब्रेरी प्रबंधित करें",
        "Search games..." to "गेम खोजें...",
        "Search game or app..." to "गेम या ऐप खोजें...",
        "SELECT TARGET GAME" to "टारगेट गेम चुनें",
        "Auto-launches with Focus Mode & Overlay" to "फ़ोकस मोड और ओवरले के साथ अपने आप शुरू होता है",
        "No apps found matching search." to "खोज से मेल खाने वाला कोई ऐप नहीं मिला।",
        "GAME" to "गेम",
        "Selected" to "चुना गया",
        "Recently Played" to "हाल ही में खेले गए",
        "Most Played" to "सबसे ज़्यादा खेले गए",
        "Most Played Games" to "सबसे ज़्यादा खेले गए गेम्स",
        "Total Gaming Time" to "कुल गेमिंग समय",
        "Game Library" to "गेम लाइब्रेरी",
        "Selected Game" to "चुना गया गेम",
        "Videos & Gallery" to "वीडियो और गैलरी",
        "No videos have been recorded" to "कोई वीडियो रिकॉर्ड नहीं किया गया है",
        "Record" to "रिकॉर्ड करें",
        "Stop" to "रोकें",
        "Screenshot" to "स्क्रीनशॉट",
        "Screen Recorder" to "स्क्रीन रिकॉर्डर",
        "Recording Settings" to "रिकॉर्डिंग सेटिंग्स",
        "Resolution" to "रेज़ोल्यूशन",
        "Frame Rate" to "फ़्रेम रेट",
        "Bitrate" to "बिटरेट",
        "Audio Source" to "ऑडियो स्रोत",
        "Recording saved to Videos!" to "रिकॉर्डिंग वीडियो में सेव हो गई!",
        "Hub Appearance & Settings" to "हब दिखावट और सेटिंग्स",
        "App Language & Region" to "ऐप की भाषा और क्षेत्र",
        "Auto-Detect (System)" to "ऑटो-डिटेक्ट (सिस्टम भाषा)",
        "System Language" to "सिस्टम भाषा (ऑटो)",
        "Display Mode & Background" to "डिस्प्ले मोड और बैकग्राउंड",
        "Signed-In Account" to "साइन-इन किया गया खाता",
        "Signed In Account" to "साइन-इन किया गया खाता",
        "Guest Profile (Local Settings Only)" to "गेस्ट प्रोफ़ाइल (केवल लोकल सेटिंग्स)",
        "Account" to "खाता",
        "Sign In" to "साइन इन करें",
        "Sign Out" to "साइन आउट करें",
        "Edit Profile" to "प्रोफ़ाइल संपादित करें",
        "Save Changes" to "बदलाव सेव करें",
        "Cancel" to "रद्द करें",
        "Close" to "बंद करें",
        "Retry" to "पुनः प्रयास करें",
        "Share" to "शेयर करें",
        "Copy" to "कॉपी करें",
        "COPY" to "कॉपी",
        "COPY CODE" to "कोड कॉपी करें",
        "CLAIM PASS" to "पास क्लेम करें",
        "Ladder" to "लेवल",
        "SQUAD" to "स्क्वाड",
        "VERIFIED" to "सत्यापित",
        "PENDING" to "लंबित",
        "UNLOCKED" to "अनलॉक",
        "LOCKED" to "लॉक",
        "LEFT" to "बाकी",
        "CODE" to "कोड",
        "Connected" to "कनेक्टेड",
        "Disconnected" to "डिस्कनेक्टेड",
        "Controller Connected" to "कंट्रोलर कनेक्टेड",
        "Controller Disconnected" to "कंट्रोलर डिस्कनेक्टेड",
        "is ready for navigation & gaming" to "नेविगेशन और गेमिंग के लिए तैयार है",
        "was disconnected" to "डिस्कनेक्ट हो गया",
        "Active" to "सक्रिय",
        "Inactive" to "निष्क्रिय",
        "Enabled" to "चालू",
        "Disabled" to "बंद",
        "Optimal" to "उत्तम",
        "Warm" to "सामान्य गर्म",
        "Hot" to "गर्म",
        "Critical" to "अत्यधिक गर्म",
        "High Impact" to "उच्च प्रभाव",
        "Medium Impact" to "मध्यम प्रभाव",
        "Low Impact" to "कम प्रभाव",
        "ADB Command Copied!" to "ADB कमांड कॉपी हो गया!",
        "Copy Command" to "कमांड कॉपी करें",
        "Base64 HUD & Crosshair Config Codes" to "Base64 HUD और क्रॉसहेयर कॉन्फ़िग कोड",
        "Copy Code" to "कोड कॉपी करें",
        "Share Code" to "कोड शेयर करें",
        "Decode & Apply Layout Instantly" to "कोड डिकोड करें और तुरंत लागू करें",
        "Tactical Screen Crosshair" to "टैक्टिकल स्क्रीन क्रॉसहेयर",
        "Hand-picked gear for mobile gamers" to "मोबाइल गेमर्स के लिए चुने गए बेहतरीन गियर",
        "As an Amazon Associate I earn from qualifying purchases." to "Amazon एसोसिएट के रूप में, मैं योग्य खरीद से कमाता हूँ।",
        "View on Amazon" to "Amazon पर देखें",
        "Key Benefits" to "मुख्य लाभ",
        "BEST FOR" to "किसके लिए सर्वोत्तम",
        "TRADE-OFF" to "ध्यान दें",
        "Best For" to "किसके लिए सर्वोत्तम",
        "Trade-off" to "ध्यान दें",
        "Main Benefit" to "मुख्य लाभ",
        "Price Level" to "मूल्य स्तर",
        "Compare" to "तुलना करें",
        "Hide Compare" to "तुलना छुपाएँ",
        "Side-by-Side Tier Comparison" to "टियर की आमने-सामने तुलना",
        "Show All Gear" to "सभी गियर दिखाएँ",
        "Anonymous Gear Tap Logging" to "अनाम गियर टैप लॉगिंग",
        "INTEGRITY VIOLATION" to "सुरक्षा उल्लंघन",
        "Unauthorized Modification Detected" to "अनधिकृत बदलाव का पता चला",
        "SECURITY DIAGNOSTIC" to "सुरक्षा जांच",
        "Close Application" to "ऐप बंद करें"
    )

    private val spanishPhraseReplacements = listOf(
        "No controller detected" to "No se detectó ningún control",
        "Controller Connected" to "Control Conectado",
        "Controller Disconnected" to "Control Desconectado",
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
        "Launcher" to MultiLangEntry("Iniciador", "Peluncur", "Trình khởi chạy", "Launcher", "Lanzador", "Lanzador", "ランチャー", "लॉन्चर"),
        "Recorder" to MultiLangEntry("Gravador", "Perekam", "Trình ghi hình", "Recorder", "Grabadora", "Grabadora", "レコーダー", "रिकॉर्डर"),
        "Top Gear" to MultiLangEntry("Equipamentos", "Gear Terbaik", "Thiết bị Pro", "Top Gear", "Top Gear", "Top Gear", "トップギア", "टॉप गियर"),
        "Extras" to MultiLangEntry("Extras", "Ekstra", "Tiện ích", "Extras", "Extras", "Extras", "エクストラ", "एक्स्ट्रा"),
        "Settings" to MultiLangEntry("Configurações", "Pengaturan", "Cài đặt", "Settings", "Ajustes", "Configuración", "設定", "सेटिंग्स"),
        "Profile" to MultiLangEntry("Perfil", "Profil", "Hồ sơ", "Profile", "Perfil", "Perfil", "プロフィール", "प्रोफ़ाइल"),
        "Leaderboard" to MultiLangEntry("Classificação", "Papan Skor", "Bảng xếp hạng", "Leaderboard", "Clasificación", "Tabla de Posiciones", "ランキング", "लीडरबोर्ड"),

        // Extras Sub-Tabs
        "Tweaks" to MultiLangEntry("Ajustes Pro", "Tweak Pro", "Tinh chỉnh", "Tweaks", "Ajustes Pro", "Ajustes Pro", "プロ調整", "प्रो ट्वीक्स"),
        "Performance" to MultiLangEntry("Desempenho", "Performa", "Hiệu năng", "Performance", "Rendimiento", "Rendimiento", "パフォーマンス", "परफॉर्मेंस"),
        "Squad Pass" to MultiLangEntry("Passe de Esquadrão", "Pass Skuad", "Thẻ Đội", "Squad Pass", "Pase de Escuadrón", "Pase de Escuadrón", "スクワッドパス", "स्क्वाड पास"),
        "Touch" to MultiLangEntry("Toque", "Sentuhan", "Cảm ứng", "Touch", "Táctil", "Táctil", "タッチ", "टच"),
        "Controller" to MultiLangEntry("Controle", "Stik Kontrol", "Tay cầm", "Controller", "Control", "Mando", "コントローラー", "कंट्रोलर"),
        "Specs" to MultiLangEntry("Hardware", "Spesifikasi", "Cấu hình", "Specs", "Especificaciones", "Especificaciones", "スペック", "स्पेक्स"),
        "FPS & Diag" to MultiLangEntry("FPS e Diag", "FPS & Diag", "FPS & Chẩn đoán", "FPS at Diag", "FPS y Diag.", "FPS y Diag.", "FPS・診断", "FPS और जांच"),

        // Launcher & Focus Mode Actions
        "Normal" to MultiLangEntry("Normal", "Normal", "Thường", "Normal", "Normal", "Normal", "ノーマル", "नॉर्मल"),
        "Focus Mode" to MultiLangEntry("Modo Foco", "Mode Fokus", "Chế độ Tập trung", "Focus Mode", "Modo Enfoque", "Modo Enfoque", "フォーカスモード", "फ़ोकस मोड"),
        "Overlay and Focus Mode Timer" to MultiLangEntry("Temporizador de Sobreposição e Modo Foco", "Timer Overlay dan Mode Fokus", "Bộ Hẹn Giờ Lớp Phủ & Tập Trung", "Overlay at Focus Mode Timer", "Temporizador de Superposición y Modo Enfoque", "Temporizador de Superposición y Modo Enfoque", "オーバーレイ＆フォーカスモードタイマー", "ओवरले और फ़ोकस मोड टाइमर"),
        "Optimize Memory" to MultiLangEntry("Otimizar RAM", "Optimalkan RAM", "Giải phóng RAM", "I-optimize ang RAM", "Optimizar RAM", "Liberar RAM", "RAM最適化", "RAM ऑप्टिमाइज़ करें"),
        "Starting Focus Mode..." to MultiLangEntry("Iniciando Modo Foco...", "Memulai Mode Fokus...", "Đang bật Chế độ Tập trung...", "Sinisimulan ang Focus Mode...", "Iniciando Modo Enfoque...", "Iniciando Modo Enfoque...", "フォーカスモード起動中...", "फ़ोकस मोड शुरू हो रहा है..."),
        "Launch" to MultiLangEntry("Jogar", "Mainkan", "Chơi ngay", "Ilunsad", "Jugar", "Iniciar", "起動", "खेलें"),
        "Focus Mode & Launch" to MultiLangEntry("Modo Foco e Jogar", "Mode Fokus & Mainkan", "Chế độ Tập trung & Chơi", "Focus Mode at Ilunsad", "Modo Enfoque y Jugar", "Modo Enfoque e Iniciar", "フォーカスモードで起動", "फ़ोकस मोड और लॉन्च करें"),
        "Add Games" to MultiLangEntry("Adicionar Jogos", "Tambah Game", "Thêm trò chơi", "Magdagdag ng Laro", "Agregar Juegos", "Añadir Juegos", "ゲーム追加", "गेम जोड़ें"),
        "Manage Library" to MultiLangEntry("Gerenciar Biblioteca", "Kelola Pustaka", "Quản lý thư viện", "Pamahalaan ang Library", "Administrar Biblioteca", "Gestionar Biblioteca", "ライブラリ管理", "लाइब्रेरी प्रबंधित करें"),
        "Search games..." to MultiLangEntry("Buscar jogos...", "Cari game...", "Tìm trò chơi...", "Maghanap ng laro...", "Buscar juegos...", "Buscar juegos...", "ゲームを検索...", "गेम खोजें..."),
        "Search game or app..." to MultiLangEntry("Buscar jogo ou app...", "Cari game atau aplikasi...", "Tìm trò chơi hoặc ứng dụng...", "Maghanap ng laro o app...", "Buscar juego o aplicación...", "Buscar juego o aplicación...", "ゲームやアプリを検索...", "गेम या ऐप खोजें..."),
        "SELECT TARGET GAME" to MultiLangEntry("SELECIONAR JOGO ALVO", "PILIH GAME TARGET", "CHỌN TRÒ CHƠI MỤC TIÊU", "PILIIN ANG LARO", "SELECCIONAR JUEGO OBJETIVO", "SELECCIONAR JUEGO OBJETIVO", "対象ゲームを選択", "टारगेट गेम चुनें"),
        "Auto-launches with Focus Mode & Overlay" to MultiLangEntry("Inicia automaticamente com Modo Foco e Sobreposição", "Otomatis mulai dengan Mode Fokus & Overlay", "Tự động mở với Chế độ Tập trung & Lớp phủ", "Auto-launch gamit ang Focus Mode at Overlay", "Se inicia automáticamente con Modo Enfoque y Superposición", "Se inicia automáticamente con Modo Enfoque y Superposición", "フォーカスモードとオーバーレイで自動起動します", "फ़ोकस मोड और ओवरले के साथ अपने आप शुरू होता है"),
        "No apps found matching search." to MultiLangEntry("Nenhum app encontrado.", "Tidak ada aplikasi yang cocok.", "Không tìm thấy ứng dụng phù hợp.", "Walang nahanap na app.", "No se encontraron aplicaciones.", "No se encontraron aplicaciones.", "一致するアプリが見つかりません。", "खोज से मेल खाने वाला कोई ऐप नहीं मिला।"),
        "Recently Played" to MultiLangEntry("Jogados Recentemente", "Baru Dimainkan", "Chơi gần đây", "Kamakailang Nilaro", "Jugados Recientemente", "Jugados Recientemente", "最近プレイしたゲーム", "हाल ही में खेले गए"),
        "Most Played" to MultiLangEntry("Mais Jogados", "Paling Sering Dimainkan", "Chơi nhiều nhất", "Pinakamaraming Nilaro", "Más Jugados", "Más Jugados", "最もプレイしたゲーム", "सबसे ज़्यादा खेले गए"),
        "Most Played Games" to MultiLangEntry("Jogos Mais Jogados", "Game Paling Sering Dimainkan", "Trò chơi hay chơi nhất", "Pinakamadalas Laruin", "Juegos Más Jugados", "Juegos Más Jugados", "プレイ時間トップのゲーム", "सबसे ज़्यादा खेले गए गेम्स"),
        "Total Gaming Time" to MultiLangEntry("Tempo Total de Jogo", "Total Waktu Bermain", "Tổng thời gian chơi", "Kabuuang Oras ng Laro", "Tiempo Total de Juego", "Tiempo Total de Juego", "合計プレイ時間", "कुल गेमिंग समय"),
        "Game Library" to MultiLangEntry("Biblioteca de Jogos", "Pustaka Game", "Thư viện trò chơi", "Library ng Laro", "Biblioteca de Juegos", "Biblioteca de Juegos", "ゲームライブラリ", "गेम लाइब्रेरी"),
        "Selected Game" to MultiLangEntry("Jogo Selecionado", "Game Terpilih", "Trò chơi đã chọn", "Napiling Laro", "Juego Seleccionado", "Juego Seleccionado", "選択中のゲーム", "चुना गया गेम"),

        // Squad Pass & Referral Code System (Code-Only, Anti-Cheat)
        "SQUAD CODE ACTIVATED • REWARD UNLOCKED" to MultiLangEntry(
            "CÓDIGO DO ESQUADRÃO ATIVADO • RECOMPENSA DESBLOQUEADA",
            "KODE SKUAD AKTIF • HADIAH TERBUKA",
            "ĐÃ KÍCH HOẠT MÃ ĐỘI • MỞ KHÓA PHẦN THƯỞNG",
            "AKTIBO NA ANG SQUAD CODE • BUKAS ANG REWARD",
            "CÓDIGO DE ESCUADRÓN ACTIVADO • RECOMPENSA DESBLOQUEADA",
            "CÓDIGO DE ESCUADRÓN ACTIVADO • RECOMPENSA DESBLOQUEADA",
            "スクワッドコード有効化 • 報酬アンロック",
            "स्क्वाड कोड सक्रिय • रिवॉर्ड अनलॉक"
        ),
        "SQUAD INVITE • PRO GUEST PASS VAULT" to MultiLangEntry(
            "CONVITE DE ESQUADRÃO • COFRE DE PASSES PRO",
            "UNDANGAN SKUAD • BRANKAS PASS TAMU PRO",
            "MỜI ĐỘI • KHO THẺ KHÁCH PRO",
            "SQUAD INVITE • PRO GUEST PASS VAULT",
            "INVITACIÓN DE ESCUADRÓN • BÓVEDA DE PASES PRO",
            "INVITACIÓN DE ESCUADRÓN • BÓVEDA DE PASES PRO",
            "スクワッド招待 • PROゲストパス保管庫",
            "स्क्वाड आमंत्रण • प्रो गेस्ट पास वॉल्ट"
        ),
        "Every claimed pass gives your friend 14 Days of Full Pro Access + Starter Crosshair, and unlocks a permanent HUD module for you." to MultiLangEntry(
            "Cada passe resgatado dá ao seu amigo 14 Dias de Acesso Pro + Mira Inicial, e desbloqueia um módulo HUD permanente para você.",
            "Setiap pass yang diklaim memberi teman Anda 14 Hari Akses Pro Penuh + Crosshair Pemula, dan membuka modul HUD permanen untuk Anda.",
            "Mỗi thẻ được nhận tặng bạn bè 14 Ngày Pro + Tâm ngắm Khởi đầu, và mở khóa mô-đun HUD vĩnh viễn cho bạn.",
            "Bawat pass ay nagbibigay sa kaibigan mo ng 14 Araw na Pro Access + Starter Crosshair, at permanenteng HUD module para sa iyo.",
            "Cada pase canjeado le da a tu amigo 14 Días de Acceso Pro + Mira Inicial, y desbloquea un módulo HUD permanente para ti.",
            "Cada pase canjeado le da a tu amigo 14 Días de Acceso Pro + Mira Inicial, y desbloquea un módulo HUD permanente para ti.",
            "パスを引き換えるとフレンドに14日間のProアクセス＋初期クロスヘアが付与され、あなたには永久HUDモジュールが解放されます。",
            "प्रत्येक क्लेम किया गया पास आपके दोस्त को 14 दिन का फुल प्रो एक्सेस + स्टार्टर क्रॉसहेयर देता है, और आपके लिए स्थायी HUD मॉड्यूल अनलॉक करता है।"
        ),
        "SQUAD INVITE REWARDS" to MultiLangEntry(
            "RECOMPENSAS DE CONVITE DO ESQUADRÃO",
            "HADIAH UNDANGAN SKUAD",
            "PHẦN THƯỞNG MỜI ĐỘI",
            "MGA REWARD SA SQUAD INVITE",
            "RECOMPENSAS DE INVITACIÓN DE ESCUADRÓN",
            "RECOMPENSAS DE INVITACIÓN DE ESCUADRÓN",
            "スクワッド招待報酬",
            "स्क्वाड आमंत्रण पुरस्कार"
        ),
        "14 Day Pro Guest Pass Vault" to MultiLangEntry(
            "Cofre de Passes Pro de 14 Dias",
            "Brankas Pass Tamu Pro 14 Hari",
            "Kho Thẻ Khách Pro 14 Ngày",
            "14 Araw na Pro Guest Pass Vault",
            "Bóveda de Pases de Invitado Pro (14 Días)",
            "Bóveda de Pases de Invitado Pro (14 Días)",
            "14日間 Proゲストパス保管庫",
            "14 दिन का प्रो गेस्ट पास वॉल्ट"
        ),
        "SQUAD REFERRAL CODE (ENTER IN EXTRAS → SQUAD PASS)" to MultiLangEntry(
            "CÓDIGO DE INDICAÇÃO (DIGITE EM EXTRAS → PASSE)",
            "KODE REFERAL SKUAD (MASUKKAN DI EKSTRA → PASS SKUAD)",
            "MÃ GIỚI THIỆU ĐỘI (NHẬP TẠI TIỆN ÍCH → THẺ ĐỘI)",
            "SQUAD REFERRAL CODE (ILAGAY SA EXTRAS → SQUAD PASS)",
            "CÓDIGO DE ESCUADRÓN (INGRESA EN EXTRAS → PASE)",
            "CÓDIGO DE ESCUADRÓN (INGRESA EN EXTRAS → PASE)",
            "スクワッド紹介コード（エクストラ → スクワッドパスで入力）",
            "स्क्वाड रेफ़रल कोड (एक्स्ट्रा → स्क्वाड पास में दर्ज करें)"
        ),
        "COPY CODE" to MultiLangEntry("COPIAR", "SALIN KODE", "SAO CHÉP MÃ", "KOPYAHIN", "COPIAR", "COPIAR", "コードコピー", "कोड कॉपी करें"),
        "CLAIM PASS" to MultiLangEntry("RESGATAR", "KLAIM PASS", "NHẬN THẺ", "I-CLAIM", "CANJEAR", "CANJEAR", "パス受取", "पास क्लेम करें"),
        "Enter Squad Referral Code (e.g. GMAX-SQUAD-1234)" to MultiLangEntry(
            "Digite o Código do Esquadrão (ex: GMAX-SQUAD-1234)",
            "Masukkan Kode Referal Skuad (mis. GMAX-SQUAD-1234)",
            "Nhập Mã Giới Thiệu Đội (VD: GMAX-SQUAD-1234)",
            "Ilagay ang Squad Referral Code (hal. GMAX-SQUAD-1234)",
            "Ingresa el Código de Escuadrón (ej. GMAX-SQUAD-1234)",
            "Ingresa el Código de Escuadrón (ej. GMAX-SQUAD-1234)",
            "スクワッド紹介コードを入力（例: GMAX-SQUAD-1234）",
            "स्क्वाड रेफ़रल कोड दर्ज करें (जैसे GMAX-SQUAD-1234)"
        ),
        "Squad Reward Breakdown" to MultiLangEntry(
            "Detalhes das Recompensas do Esquadrão",
            "Rincian Hadiah Skuad",
            "Chi Tiết Phần Thưởng Đội",
            "Breakdown ng Squad Rewards",
            "Desglose de Recompensas de Escuadrón",
            "Desglose de Recompensas de Escuadrón",
            "スクワッド報酬の内訳",
            "स्क्वाड रिवॉर्ड विवरण"
        ),
        "Rewards for both you and the friends you invite" to MultiLangEntry(
            "Recompensas para você e para os amigos que convidar",
            "Hadiah untuk Anda dan teman yang Anda undang",
            "Phần thưởng cho cả bạn và bạn bè được mời",
            "Mga reward para sa iyo at sa mga kaibigang inimbita mo",
            "Recompensas tanto para ti como para los amigos que invites",
            "Recompensas tanto para ti como para los amigos que invites",
            "あなたと招待されたフレンドの双方に付与される特典",
            "आपके और आपके द्वारा आमंत्रित दोस्तों दोनों के लिए पुरस्कार"
        ),
        "THE INVITEE (FRIEND)" to MultiLangEntry("O CONVIDADO (AMIGO)", "TEMAN YANG DIUNDANG", "NGƯỜI ĐƯỢC MỜI (BẠN BÈ)", "ANG INIMBITA (KAIBIGAN)", "EL INVITADO (AMIGO)", "EL INVITADO (AMIGO)", "招待されたフレンド", "आमंत्रित मित्र"),
        "14 Days Full Pro + Starter Crosshair/HUD" to MultiLangEntry("14 Dias Pro Completo + Mira/HUD Inicial", "14 Hari Pro Penuh + Crosshair/HUD Pemula", "14 Ngày Pro Đầy Đủ + Tâm ngắm/HUD", "14 Araw na Full Pro + Starter Crosshair/HUD", "14 Días Pro Completo + Mira/HUD Inicial", "14 Días Pro Completo + Mira/HUD Inicial", "14日間フルPro＋初期クロスヘア/HUD", "14 दिन फुल प्रो + स्टार्टर क्रॉसहेयर/HUD"),
        "Unlocks full Pro features for two weeks so your duo can play with GMax." to MultiLangEntry(
            "Desbloqueia todos os recursos Pro por duas semanas para seu duo jogar com o GMax.",
            "Membuka fitur Pro penuh selama dua minggu agar duo Anda bisa bermain dengan GMax.",
            "Mở khóa toàn bộ tính năng Pro trong 2 tuần để đồng đội chơi cùng GMax.",
            "Binubuksan ang buong Pro features sa loob ng 2 linggo para makapaglaro ang duo mo sa GMax.",
            "Desbloquea todas las funciones Pro por dos semanas para que tu dúo juegue con GMax.",
            "Desbloquea todas las funciones Pro por dos semanas para que tu dúo juegue con GMax.",
            "2週間のフルPro機能を解放し、デュオでGMaxを利用できます。",
            "दो सप्ताह के लिए पूर्ण प्रो फ़ीचर्स अनलॉक करता है ताकि आपका दोस्त GMax के साथ खेल सके।"
        ),
        "THE REFERRER (YOU)" to MultiLangEntry("QUEM CONVIDA (VOCÊ)", "PENGUNDANG (ANDA)", "NGƯỜI GIỚI THIỆU (BẠN)", "ANG NAG-IMBITA (IKAW)", "QUIEN INVITA (TÚ)", "QUIEN INVITA (TÚ)", "招待者（あなた）", "आमंत्रणकर्ता (आप)"),
        "Permanent Modules + Stacking Pro Time" to MultiLangEntry("Módulos Permanentes + Tempo Pro Acumulável", "Modul Permanen + Waktu Pro Bertumpuk", "Mô-đun Vĩnh Viễn + Cộng Dồn Ngày Pro", "Permanenteng Module + Stacking Pro Time", "Módulos Permanentes + Tiempo Pro Acumulable", "Módulos Permanentes + Tiempo Pro Acumulable", "永久モジュール＋Pro期間加算", "स्थायी मॉड्यूल + जुड़ने वाला प्रो समय"),
        "4 Step Squad Milestone Ladder" to MultiLangEntry("Escada de 4 Níveis do Esquadrão", "Tangga 4 Tahap Milestone Skuad", "4 Cấp Độ Cột Mốc Đội", "4-Step Squad Milestone Ladder", "Escalera de 4 Niveles de Escuadrón", "Escalera de 4 Niveles de Escuadrón", "4段階スクワッド達成ティア", "4-स्टेप स्क्वाड माइलस्टोन लैडर"),
        "Verified Core Action required: 15m focus play OR 1 full HUD match" to MultiLangEntry(
            "Ação verificada necessária: 15m de jogo focado OU 1 partida completa com HUD",
            "Tindakan terverifikasi diperlukan: 15 mnt main fokus ATAU 1 laga penuh dengan HUD",
            "Yêu cầu xác minh: 15 phút chơi tập trung HOẶC 1 trận đầy đủ bật HUD",
            "Kailangan ng Verified Action: 15m focus play O 1 buong HUD match",
            "Acción verificada requerida: 15 min de juego en enfoque O 1 partida completa con HUD",
            "Acción verificada requerida: 15 min de juego en enfoque O 1 partida completa con HUD",
            "認証条件：15分間のフォーカスプレイ または フローティングHUD有効で1マッチ完了",
            "सत्यापित एक्शन आवश्यक: 15 मिनट फ़ोकस गेमप्ले या 1 पूरा HUD मैच"
        ),
        "Guest Pass Claim & Verification" to MultiLangEntry("Resgate e Verificação de Passe", "Klaim & Verifikasi Pass Tamu", "Nhận & Xác Minh Thẻ Khách", "Pag-claim at Verification ng Guest Pass", "Canje y Verificación de Pase de Invitado", "Canje y Verificación de Pase de Invitado", "ゲストパスの受取と認証", "गेस्ट पास क्लेम और सत्यापन"),
        "Google Sign-In + Device bound (1 claim per physical device) • Unlocks on 15m focus play or 1 HUD match" to MultiLangEntry(
            "Login Google + Vinculado ao aparelho (1 resgate por dispositivo físico) • Desbloqueia com 15m de jogo ou 1 partida HUD",
            "Login Google + Terikat perangkat (1 klaim per perangkat fisik) • Terbuka setelah 15 mnt main atau 1 laga HUD",
            "Đăng nhập Google + Khóa theo thiết bị (1 lần nhận/máy) • Mở khóa sau 15p chơi hoặc 1 trận HUD",
            "Google Sign-In + Device bound (1 claim bawat pisikal na device) • Bubukas sa 15m focus play o 1 HUD match",
            "Cuenta Google + Vinculado al dispositivo (1 canje por dispositivo físico) • Se desbloquea con 15m de juego o 1 partida HUD",
            "Cuenta Google + Vinculado al dispositivo (1 canje por dispositivo físico) • Se desbloquea con 15m de juego o 1 partida HUD",
            "Googleサインイン必須＋端末固有ロック（1台につき1回のみ）• 15分フォーカスプレイまたは1回のHUDマッチで解放",
            "Google साइन-इन + डिवाइस बाउंड (प्रति डिवाइस 1 क्लेम) • 15 मिनट फ़ोकस प्ले या 1 HUD मैच पर अनलॉक"
        ),
        "DEVICE VERIFIED" to MultiLangEntry("APARELHO VERIFICADO", "PERANGKAT TERVERIFIKASI", "ĐÃ XÁC MINH THIẾT BỊ", "VERIFIED ANG DEVICE", "DISPOSITIVO VERIFICADO", "DISPOSITIVO VERIFICADO", "端末認証済み", "डिवाइस सत्यापित"),
        "Hardware ID Bound" to MultiLangEntry("ID de Hardware Vinculado", "ID Perangkat Keras Terikat", "Đã Khóa ID Phần Cứng", "Nakataling Hardware ID", "ID de Hardware Vinculado", "ID de Hardware Vinculado", "ハードウェアID紐付け済", "हार्डवेयर आईडी बाउंड"),
        "Equip Permanent Module Now" to MultiLangEntry("Equipar Módulo Permanente Agora", "Pasang Modul Permanen Sekarang", "Trang Bị Mô-đun Vĩnh Viễn Ngay", "I-equip ang Permanenteng Module", "Equipar Módulo Permanente Ahora", "Equipar Módulo Permanente Ahora", "永久モジュールを装備する", "स्थायी मॉड्यूल अभी लगाएँ"),
        "Invite Friends, Get Pro Free!" to MultiLangEntry(
            "Convide Amigos, Ganhe Pro Grátis!",
            "Undang Teman, Dapatkan Pro Gratis!",
            "Mời Bạn Bè, Nhận Pro Miễn Phí!",
            "Mag-imbita ng Kaibigan, Libreng Pro!",
            "¡Invita Amigos y Obtén Pro Gratis!",
            "¡Invita Amigos y Obtén Pro Gratis!",
            "フレンドを招待してProを無料でゲット！",
            "दोस्तों को आमंत्रित करें, मुफ़्त Pro पाएँ!"
        ),
        "Let's invite your squad to experience G Max Gaming Hub and unlock free Pro rewards together." to MultiLangEntry(
            "Convide seu esquadrão para experimentar o G Max Gaming Hub e desbloquear recompensas Pro grátis juntos.",
            "Ayo undang skuad Anda untuk merasakan G Max Gaming Hub dan buka hadiah Pro gratis bersama.",
            "Hãy mời đồng đội trải nghiệm G Max Gaming Hub và cùng mở khóa phần thưởng Pro miễn phí.",
            "Imbitahan ang iyong squad sa G Max Gaming Hub at sabay na buksan ang libreng Pro rewards.",
            "Invita a tu escuadrón a probar G Max Gaming Hub y desbloqueen recompensas Pro gratis juntos.",
            "Invita a tu escuadrón a probar G Max Gaming Hub y desbloqueen recompensas Pro gratis juntos.",
            "スクワッドをG Max Gaming Hubに招待して、無料のPro特典を一緒にアンロックしましょう。",
            "अपने स्क्वाड को G Max Gaming Hub का अनुभव करने के लिए आमंत्रित करें और साथ मिलकर मुफ़्त Pro पुरस्कार अनलॉक करें।"
        ),
        "Total Earned" to MultiLangEntry("Total Ganho", "Total Diperoleh", "Tổng Đã Nhận", "Kabuuang Nakuha", "Total Ganado", "Total Ganado", "獲得合計", "कुल कमाए"),
        "Friends Referred" to MultiLangEntry("Amigos Indicados", "Teman Diundang", "Bạn Đã Mời", "Mga Inimbita", "Amigos Invitados", "Amigos Invitados", "招待したフレンド", "आमंत्रित मित्र"),
        "Invite Friends" to MultiLangEntry("Convidar Amigos", "Undang Teman", "Mời Bạn Bè", "Mag-imbita ng Kaibigan", "Invitar Amigos", "Invitar Amigos", "フレンドを招待する", "दोस्तों को आमंत्रित करें"),
        "How does it work?" to MultiLangEntry("Como funciona?", "Bagaimana cara kerjanya?", "Cách thức hoạt động?", "Paano ito gumagana?", "¿Cómo funciona?", "¿Cómo funciona?", "仕組みを見る", "यह कैसे काम करता है?"),
        "Here's your referral code. Share it!" to MultiLangEntry(
            "Aqui está seu código de indicação. Compartilhe!",
            "Ini kode referal Anda. Bagikan sekarang!",
            "Đây là mã giới thiệu của bạn. Hãy chia sẻ ngay!",
            "Narito ang iyong referral code. I-share na!",
            "¡Aquí tienes tu código de referido. Compártelo!",
            "¡Aquí tienes tu código de referido. Compártelo!",
            "あなたの紹介コードはこちら。シェアしよう！",
            "यह रहा आपका रेफ़रल कोड। इसे शेयर करें!"
        ),
        "Share it with your friends and unlock Pro rewards." to MultiLangEntry(
            "Compartilhe com seus amigos e desbloqueie recompensas Pro.",
            "Bagikan dengan teman Anda dan buka hadiah Pro.",
            "Chia sẻ với bạn bè và mở khóa phần thưởng Pro.",
            "I-share ito sa iyong mga kaibigan at buksan ang Pro rewards.",
            "Compártelo con tus amigos y desbloquea recompensas Pro.",
            "Compártelo con tus amigos y desbloquea recompensas Pro.",
            "フレンドにコードを共有してPro報酬を獲得しましょう。",
            "इसे अपने दोस्तों के साथ शेयर करें और Pro रिवॉर्ड अनलॉक करें।"
        ),
        "Share Code" to MultiLangEntry("Compartilhar Código", "Bagikan Kode", "Chia Sẻ Mã", "I-share ang Code", "Compartir Código", "Compartir Código", "コードを共有", "कोड शेयर करें"),
        "+14 Days Pro" to MultiLangEntry("+14 Dias Pro", "+14 Hari Pro", "+14 Ngày Pro", "+14 Araw na Pro", "+14 Días Pro", "+14 Días Pro", "+14日間 Pro", "+14 दिन Pro"),
        "Referral Reward" to MultiLangEntry("Recompensa de Indicação", "Hadiah Referal", "Phần Thưởng Giới Thiệu", "Referral Reward", "Recompensa por Referido", "Recompensa por Referido", "紹介報酬", "रेफ़रल इनाम"),
        "Every time you successfully refer a friend to our app, they get 14 Days Full Pro + Starter Crosshair and you unlock permanent HUD modules." to MultiLangEntry(
            "Sempre que você indicar um amigo com sucesso, ele ganha 14 Dias Pro Completo + Mira Inicial e você desbloqueia módulos HUD permanentes.",
            "Setiap kali Anda berhasil mengundang teman ke aplikasi kami, mereka mendapat 14 Hari Pro Penuh + Crosshair Pemula dan Anda membuka modul HUD permanen.",
            "Mỗi khi bạn giới thiệu bạn bè thành công, họ nhận 14 Ngày Pro + Tâm ngắm Khởi đầu và bạn mở khóa mô-đun HUD vĩnh viễn.",
            "Sa bawat matagumpay na pag-refer ng kaibigan, makakakuha sila ng 14 Araw na Full Pro + Starter Crosshair at magbubukas ka ng permanenteng HUD modules.",
            "Cada vez que refieras con éxito a un amigo, él recibe 14 Días Pro Completo + Mira Inicial y tú desbloqueas módulos HUD permanentes.",
            "Cada vez que refieras con éxito a un amigo, él recibe 14 Días Pro Completo + Mira Inicial y tú desbloqueas módulos HUD permanentes.",
            "フレンドの紹介が成立するたびに、フレンドに14日間フルPro＋初期クロスヘアが付与され、あなたの永久HUDモジュールが解放されます。",
            "जब भी आप किसी मित्र को सफलतापूर्वक रेफ़र करते हैं, उन्हें 14 दिन का फुल Pro + स्टार्टर क्रॉसहेयर मिलता है और आप स्थायी HUD मॉड्यूल अनलॉक करते हैं।"
        ),
        "What counts as a referral?" to MultiLangEntry(
            "O que conta como uma indicação?",
            "Apa yang dihitung sebagai referal?",
            "Điều kiện tính lượt giới thiệu?",
            "Ano ang binibilang na referral?",
            "¿Qué cuenta como un referido?",
            "¿Qué cuenta como un referido?",
            "紹介が成立する条件は？",
            "रेफ़रल कब मान्य होता है?"
        ),
        "Share your referral code" to MultiLangEntry(
            "Compartilhe seu código de indicação",
            "Bagikan kode referal Anda",
            "Chia sẻ mã giới thiệu của bạn",
            "I-share ang iyong referral code",
            "Comparte tu código de referido",
            "Comparte tu código de referido",
            "紹介コードをシェアする",
            "अपना रेफ़रल कोड शेयर करें"
        ),
        "First, share your GMAX referral code with your friend or duo squadmate." to MultiLangEntry(
            "Primeiro, compartilhe seu código GMAX com seu amigo ou parceiro de esquadrão.",
            "Pertama, bagikan kode referal GMAX Anda ke teman atau rekan skuad.",
            "Đầu tiên, hãy chia sẻ mã giới thiệu GMAX cho bạn bè hoặc đồng đội của bạn.",
            "Una, i-share ang iyong GMAX referral code sa kaibigan o ka-squad mo.",
            "Primero, comparte tu código GMAX con tu amigo o compañero de escuadrón.",
            "Primero, comparte tu código GMAX con tu amigo o compañero de escuadrón.",
            "まず、あなたのGMAX紹介コードをフレンドやスクワッド仲間に共有します。",
            "सबसे पहले, अपना GMAX रेफ़रल कोड अपने दोस्त या स्क्वाडमेट के साथ शेयर करें।"
        ),
        "Make your friend sign in" to MultiLangEntry(
            "Peça para seu amigo entrar na conta",
            "Minta teman Anda login",
            "Bạn bè đăng nhập và nhập mã",
            "Mag-sign in ang iyong kaibigan",
            "Haz que tu amigo inicie sesión",
            "Haz que tu amigo inicie sesión",
            "フレンドがサインインしてコードを入力",
            "अपने दोस्त को साइन-इन करने के लिए कहें"
        ),
        "Second, make your friend sign in with Google and claim your code in Squad Pass." to MultiLangEntry(
            "Segundo, seu amigo faz login com o Google e resgata seu código na aba Passe de Esquadrão.",
            "Kedua, teman Anda login dengan Google dan mengklaim kode Anda di menu Pass Skuad.",
            "Thứ hai, bạn của bạn đăng nhập Google và nhập mã trong mục Thẻ Đội.",
            "Pangalawa, mag-sign in ang kaibigan mo gamit ang Google at i-claim ang code sa Squad Pass.",
            "Segundo, tu amigo inicia sesión con Google y canjea tu código en Pase de Escuadrón.",
            "Segundo, tu amigo inicia sesión con Google y canjea tu código en Pase de Escuadrón.",
            "次に、フレンドがGoogleでサインインし、スクワッドパス画面でコードを引き換えます。",
            "दूसरा, आपका दोस्त Google से साइन-इन करके स्क्वाड पास में आपका कोड क्लेम करे।"
        ),
        "Your friend completes 1 match or 15m focus session" to MultiLangEntry(
            "Seu amigo conclui 1 partida ou 15m de sessão foco",
            "Teman menyelesaikan 1 laga atau 15 mnt sesi fokus",
            "Bạn bè hoàn thành 1 trận hoặc 15 phút tập trung",
            "Tapusin ng kaibigan ang 1 match o 15m focus session",
            "Tu amigo completa 1 partida o 15m de sesión enfoque",
            "Tu amigo completa 1 partida o 15m de sesión enfoque",
            "フレンドが15分フォーカスセッションまたは1マッチを完了",
            "आपका दोस्त 1 मैच या 15 मिनट फ़ोकस सेशन पूरा करे"
        ),
        "Third, your friend runs GMax focus play for 15 minutes or finishes 1 HUD match." to MultiLangEntry(
            "Terceiro, seu amigo joga com o GMax por 15 minutos ou termina 1 partida com HUD.",
            "Ketiga, teman Anda bermain dengan GMax selama 15 menit atau menyelesaikan 1 laga HUD.",
            "Thứ ba, bạn của bạn chơi game GMax trong 15 phút hoặc hoàn thành 1 trận bật HUD.",
            "Pangatlo, maglaro ang kaibigan mo nang 15 minuto sa GMax o tapusin ang 1 HUD match.",
            "Tercero, tu amigo juega con GMax por 15 minutos o completa 1 partida con HUD.",
            "Tercero, tu amigo juega con GMax por 15 minutos o completa 1 partida con HUD.",
            "3つ目に、フレンドがGMaxで15分間プレイするか、HUD有効で1マッチ完了します。",
            "तीसरा, आपका दोस्त 15 मिनट तक GMax फ़ोकस गेम खेले या 1 HUD मैच पूरा करे।"
        ),
        "Finally, you both unlock Pro rewards" to MultiLangEntry(
            "Finalmente, ambos desbloqueiam recompensas Pro",
            "Terakhir, kalian berdua membuka hadiah Pro",
            "Cuối cùng, cả hai cùng nhận thưởng Pro",
            "Sa huli, pareho kayong makakakuha ng Pro rewards",
            "Finalmente, ambos desbloquean recompensas Pro",
            "Finalmente, ambos desbloquean recompensas Pro",
            "最後に、2人ともPro特典をアンロック！",
            "अंत में, आप दोनों को मुफ़्त Pro पुरस्कार मिलते हैं"
        ),
        "Finally, you both get 14 Days Full Pro access and permanent HUD modules for free." to MultiLangEntry(
            "Por fim, vocês dois ganham 14 Dias de Acesso Pro Completo e módulos HUD permanentes grátis.",
            "Terakhir, kalian berdua mendapatkan Akses Pro Penuh 14 Hari dan modul HUD permanen secara gratis.",
            "Cuối cùng, cả hai bạn đều nhận được 14 Ngày Pro Đầy Đủ và mô-đun HUD vĩnh viễn miễn phí.",
            "Sa huli, pareho kayong makakatanggap ng 14 Araw na Full Pro access at permanenteng HUD modules nang libre.",
            "Finalmente, ambos obtienen 14 Días de Acceso Pro Completo y módulos HUD permanentes gratis.",
            "Finalmente, ambos obtienen 14 Días de Acceso Pro Completo y módulos HUD permanentes gratis.",
            "最後に、あなたとフレンドの両方に14日間のフルProアクセスと永久HUDモジュールが無料で付与されます。",
            "अंत में, आप दोनों को 14 दिन का फुल Pro एक्सेस और स्थायी HUD मॉड्यूल मुफ़्त मिलते हैं।"
        ),
        "You received" to MultiLangEntry("Você recebeu", "Anda menerima", "Bạn nhận được", "Nakatanggap ka ng", "Recibiste", "Recibiste", "獲得特典", "आपको मिला"),
        "PRO ACCESS" to MultiLangEntry("ACESSO PRO", "AKSES PRO", "QUYỀN PRO", "PRO ACCESS", "ACCESO PRO", "ACCESO PRO", "PROアクセス", "PRO एक्सेस"),
        "Congratulations! You have earned +14 Days Pro and permanent Squad HUD rewards from referring your squad." to MultiLangEntry(
            "Parabéns! Você ganhou +14 Dias Pro e recompensas permanentes de HUD por convidar seu esquadrão.",
            "Selamat! Anda mendapatkan +14 Hari Pro dan hadiah HUD Skuad permanen dari mengundang skuad Anda.",
            "Chúc mừng! Bạn đã nhận được +14 Ngày Pro và phần thưởng HUD Đội vĩnh viễn nhờ giới thiệu đồng đội.",
            "Binabati kita! Nakakuha ka ng +14 Araw na Pro at permanenteng Squad HUD rewards mula sa pag-refer ng squad mo.",
            "¡Felicidades! Has ganado +14 Días Pro y recompensas permanentes de HUD por referir a tu escuadrón.",
            "¡Felicitaciones! Has ganado +14 Días Pro y recompensas permanentes de HUD por referir a tu escuadrón.",
            "おめでとうございます！スクワッドの紹介により、+14日間Proと永久HUDモジュール報酬を獲得しました。",
            "बधाई हो! अपने स्क्वाड को रेफ़र करने पर आपने +14 दिन Pro और स्थायी स्क्वाड HUD पुरस्कार अर्जित किए हैं।"
        ),
        "Great, thank you!" to MultiLangEntry("Ótimo, obrigado!", "Mantap, terima kasih!", "Tuyệt vời, cảm ơn!", "Ayos, salamat!", "¡Genial, gracias!", "¡Genial, gracias!", "最高、ありがとう！", "बहुत बढ़िया, धन्यवाद!"),
        "Preview Reward" to MultiLangEntry("Ver Recompensa", "Lihat Hadiah", "Xem Phần Thưởng", "Tingnan ang Reward", "Ver Recompensa", "Ver Recompensa", "報酬プレビュー", "रिवॉर्ड देखें"),

        // Recorder Screen
        "Videos & Gallery" to MultiLangEntry("Vídeos e Galeria", "Video & Galeri", "Video & Thư viện", "Video at Gallery", "Videos y Galería", "Videos y Galería", "動画＆ギャラリー", "वीडियो और गैलरी"),
        "No videos have been recorded" to MultiLangEntry("Nenhum vídeo foi gravado", "Belum ada video yang direkam", "Chưa có video nào được ghi", "Walang na-record na video", "No se han grabado videos", "No se han grabado videos", "録画された動画はありません", "कोई वीडियो रिकॉर्ड नहीं किया गया है"),
        "Record" to MultiLangEntry("Gravar", "Rekam", "Ghi hình", "I-record", "Grabar", "Grabar", "録画", "रिकॉर्ड करें"),
        "Stop" to MultiLangEntry("Parar", "Berhenti", "Dừng", "Ihinto", "Detener", "Detener", "停止", "रोकें"),
        "Screenshot" to MultiLangEntry("Captura de Tela", "Tangkapan Layar", "Chụp màn hình", "Screenshot", "Captura de Pantalla", "Captura de Pantalla", "スクリーンショット", "स्क्रीनशॉट"),
        "Screen Recorder" to MultiLangEntry("Gravador de Tela", "Perekam Layar", "Trình ghi màn hình", "Screen Recorder", "Grabadora de Pantalla", "Grabador de Pantalla", "画面レコーダー", "स्क्रीन रिकॉर्डर"),
        "Recording Settings" to MultiLangEntry("Ajustes de Gravação", "Pengaturan Rekaman", "Cài đặt ghi hình", "Settings ng Recording", "Ajustes de Grabación", "Configuración de Grabación", "録画設定", "रिकॉर्डिंग सेटिंग्स"),
        "Resolution" to MultiLangEntry("Resolução", "Resolusi", "Độ phân giải", "Resolusyon", "Resolución", "Resolución", "解像度", "रेज़ोल्यूशन"),
        "Frame Rate" to MultiLangEntry("Taxa de Quadros", "Frame Rate", "Tốc độ khung hình", "Frame Rate", "Cuadros por Segundo", "Fotogramas por Segundo", "フレームレート", "फ़्रेम रेट"),
        "Bitrate" to MultiLangEntry("Taxa de Bits", "Bitrate", "Tốc độ bit", "Bitrate", "Tasa de Bits", "Tasa de Bits", "ビットレート", "बिटरेट"),
        "Audio Source" to MultiLangEntry("Fonte de Áudio", "Sumber Audio", "Nguồn âm thanh", "Pinagmulan ng Audio", "Fuente de Audio", "Fuente de Audio", "音声ソース", "ऑडियो स्रोत"),
        "Recording saved to Videos!" to MultiLangEntry("Gravação salva em Vídeos!", "Rekaman disimpan ke Video!", "Đã lưu bản ghi vào Video!", "Na-save ang recording sa Videos!", "¡Grabación guardada en Videos!", "¡Grabación guardada en Videos!", "動画を保存しました！", "रिकॉर्डिंग वीडियो में सेव हो गई!"),

        // Settings Screen
        "Hub Appearance & Settings" to MultiLangEntry(
            "Aparência e Configurações do Hub",
            "Tampilan & Pengaturan Hub",
            "Giao diện & Cài đặt Hub",
            "Hitsura at Settings ng Hub",
            "Apariencia y Ajustes del Hub",
            "Apariencia y Configuración del Hub",
            "Hubの外観と設定",
            "हब दिखावट और सेटिंग्स"
        ),
        "Automatic Light/Dark display sync, custom backgrounds, and cloud account backup" to MultiLangEntry(
            "Sincronização automática Claro/Escuro, fundos personalizados e backup na nuvem",
            "Sinkronisasi layar Terang/Gelap otomatis, latar kustom, dan cadangan cloud",
            "Tự động đồng bộ Sáng/Tối, hình nền tùy chỉnh và sao lưu đám mây",
            "Auto Light/Dark sync, custom na background, at cloud account backup",
            "Sincronización automática Claro/Oscuro, fondos personalizados y respaldo en la nube",
            "Sincronización automática Claro/Oscuro, fondos personalizados y copia en la nube",
            "ライト/ダーク自動同期、カスタム背景、クラウドバックアップ",
            "ऑटो लाइट/डार्क सिंक, कस्टम बैकग्राउंड और क्लाउड अकाउंट बैकअप"
        ),
        "App Language & Region" to MultiLangEntry(
            "Idioma e Região do App",
            "Bahasa & Wilayah Aplikasi",
            "Ngôn ngữ & Khu vực",
            "Wika at Rehiyon ng App",
            "Idioma y Región de la App",
            "Idioma y Región de la Aplicación",
            "アプリの言語と地域",
            "ऐप की भाषा और क्षेत्र"
        ),
        "Detects device language or choose English, Español, Português, Indonesian, Vietnamese, or Tagalog" to MultiLangEntry(
            "Detecta automaticamente o idioma do aparelho ou escolha Português (Brasil), Español, Japonês, Hindi, Inglês, Indonésio, Vietnamita ou Tagalog",
            "Deteksi otomatis bahasa perangkat atau pilih Bahasa Indonesia, Inggris, Español, Jepang, Hindi, Português, Vietnam, atau Tagalog",
            "Tự động nhận diện ngôn ngữ thiết bị hoặc chọn Tiếng Việt, Tiếng Anh, Español, Nhật, Hindi, Português, Indonesia, Tagalog",
            "Auto detect ang wika ng device o pumili ng Tagalog, English, Español, Japanese, Hindi, Português, Indonesian, o Vietnamese",
            "Detecta automáticamente el idioma del dispositivo o elige Español, Japonés, Hindi, Inglés, Portugués, Indonesio, Vietnamita o Tagalo",
            "Detecta automáticamente el idioma del dispositivo o elige Español, Japonés, Hindi, Inglés, Portugués, Indonesio, Vietnamita o Tagalo",
            "端末の言語を自動検出、または日本語・英語・スペイン語・ヒンディー語・ポルトガル語などを選択",
            "डिवाइस की भाषा स्वतः पहचानें या हिन्दी, अंग्रेज़ी, जापानी, स्पैनिश, पुर्तगाली आदि चुनें"
        ),
        "Display Mode & Background" to MultiLangEntry(
            "Modo de Exibição e Fundo",
            "Mode Tampilan & Latar Belakang",
            "Chế độ hiển thị & Hình nền",
            "Display Mode at Background",
            "Modo de Pantalla y Fondo",
            "Modo de Pantalla y Fondo",
            "表示モードと背景",
            "डिस्प्ले मोड और बैकग्राउंड"
        ),
        "Automatically follows your device display mode (Light / Dark) or use a custom color" to MultiLangEntry(
            "Segue automaticamente o modo de tela do dispositivo (Claro / Escuro) ou use uma cor personalizada",
            "Otomatis mengikuti mode layar perangkat (Terang / Gelap) atau gunakan warna kustom",
            "Tự động theo chế độ màn hình thiết bị (Sáng / Tối) hoặc dùng màu tùy chỉnh",
            "Sumusunod sa display mode ng device (Light / Dark) o gumamit ng custom na kulay",
            "Sigue automáticamente el modo de pantalla de tu dispositivo (Claro / Oscuro) o usa un color personalizado",
            "Sigue automáticamente el modo de pantalla de tu dispositivo (Claro / Oscuro) o usa un color personalizado",
            "端末の画面モード（ライト/ダーク）に自動追従、またはカスタム背景色を使用",
            "डिवाइस के डिस्प्ले मोड (लाइट / डार्क) का स्वतः पालन करें या कस्टम रंग चुनें"
        ),
        "Signed In Account" to MultiLangEntry("Conta Conectada", "Akun Masuk", "Tài khoản đã đăng nhập", "Naka Sign In na Account", "Cuenta Conectada", "Cuenta Iniciada", "サインイン済みアカウント", "साइन इन किया गया खाता"),
        "Guest Profile (Local Settings Only)" to MultiLangEntry(
            "Perfil Convidado (Apenas Local)",
            "Profil Tamu (Hanya Pengaturan Lokal)",
            "Hồ sơ Khách (Chỉ lưu cục bộ)",
            "Guest Profile (Local Settings Lang)",
            "Perfil de Invitado (Solo Ajustes Locales)",
            "Perfil de Invitado (Solo Configuración Local)",
            "ゲストプロフィール（ローカル設定のみ）",
            "गेस्ट प्रोफ़ाइल (केवल लोकल सेटिंग्स)"
        ),
        "Account" to MultiLangEntry("Conta", "Akun", "Tài khoản", "Account", "Cuenta", "Cuenta", "アカウント", "खाता"),
        "Sign In" to MultiLangEntry("Entrar", "Masuk", "Đăng nhập", "Mag-sign In", "Iniciar Sesión", "Iniciar Sesión", "サインイン", "साइन इन करें"),
        "Sign Out" to MultiLangEntry("Sair", "Keluar", "Đăng xuất", "Mag-sign Out", "Cerrar Sesión", "Cerrar Sesión", "サインアウト", "साइन आउट करें"),
        "Edit Profile" to MultiLangEntry("Editar Perfil", "Edit Profil", "Chỉnh sửa hồ sơ", "I-edit ang Profile", "Editar Perfil", "Editar Perfil", "プロフィール編集", "प्रोफ़ाइल संपादित करें"),
        "Save Changes" to MultiLangEntry("Salvar Alterações", "Simpan Perubahan", "Lưu thay đổi", "I-save ang Pagbabago", "Guardar Cambios", "Guardar Cambios", "変更を保存", "बदलाव सेव करें"),
        "Cancel" to MultiLangEntry("Cancelar", "Batal", "Hủy", "Kanselahin", "Cancelar", "Cancelar", "キャンセル", "रद्द करें"),
        "Close" to MultiLangEntry("Fechar", "Tutup", "Đóng", "Isara", "Cerrar", "Cerrar", "閉じる", "बंद करें"),
        "Retry" to MultiLangEntry("Tentar Novamente", "Coba Lagi", "Thử lại", "Subukan Muli", "Reintentar", "Reintentar", "再試行", "पुनः प्रयास करें"),
        "Share" to MultiLangEntry("Compartilhar", "Bagikan", "Chia sẻ", "I-share", "Compartir", "Compartir", "共有", "शेयर करें"),
        "Permissions & Feature Access" to MultiLangEntry(
            "Permissões e Acesso a Recursos",
            "Izin & Akses Fitur",
            "Quyền & Truy cập tính năng",
            "Mga Pahintulot at Access sa Feature",
            "Permisos y Acceso a Funciones",
            "Permisos y Acceso a Funciones",
            "権限と機能アクセス",
            "अनुमतियाँ और फ़ीचर एक्सेस"
        ),
        "Review what each permission grants and why features need it" to MultiLangEntry(
            "Veja o que cada permissão concede e por que os recursos precisam dela",
            "Tinjau fungsi setiap izin dan mengapa fitur membutuhkannya",
            "Xem chi tiết từng quyền và lý do tính năng cần quyền đó",
            "Suriin kung ano ang binibigay ng bawat pahintulot at bakit ito kailangan",
            "Revisa qué otorga cada permiso y por qué lo necesitan las funciones",
            "Revisa qué otorga cada permiso y por qué lo necesitan las funciones",
            "各権限の役割と機能に必要な理由を確認します",
            "देखें कि प्रत्येक अनुमति क्या करती है और फ़ीचर्स को इसकी आवश्यकता क्यों है"
        ),
        "Open Unified Permissions Page" to MultiLangEntry(
            "Abrir Página de Permissões",
            "Buka Halaman Izin Terpadu",
            "Mở Trang Quản Lý Quyền",
            "Buksan ang Pahina ng Pahintulot",
            "Abrir Página de Permisos Unificada",
            "Abrir Página Unificada de Permisos",
            "統合権限ページを開く",
            "एकीकृत अनुमति पृष्ठ खोलें"
        ),
        "Tweak Safety Warnings" to MultiLangEntry(
            "Avisos de Segurança de Ajustes",
            "Peringatan Keamanan Tweak",
            "Cảnh báo an toàn tinh chỉnh",
            "Babala sa Kaligtasan ng Tweaks",
            "Advertencias de Seguridad de Ajustes",
            "Advertencias de Seguridad de Ajustes",
            "プロ調整の安全警告",
            "ट्वीक सुरक्षा चेतावनियाँ"
        ),
        "Disclosure before applying performance tweaks" to MultiLangEntry(
            "Aviso antes de aplicar ajustes de desempenho",
            "Pemberitahuan sebelum menerapkan tweak performa",
            "Thông báo trước khi áp dụng tinh chỉnh hiệu năng",
            "Paalala bago gamitin ang performance tweaks",
            "Aviso antes de aplicar ajustes de rendimiento",
            "Aviso antes de aplicar ajustes de rendimiento",
            "パフォーマンス調整を適用する前の確認表示",
            "परफॉर्मेंस ट्वीक लागू करने से पहले सूचना"
        ),
        "Show Warnings Before Activating" to MultiLangEntry(
            "Mostrar Avisos Antes de Ativar",
            "Tampilkan Peringatan Sebelum Aktif",
            "Hiển thị cảnh báo trước khi bật",
            "Ipakita ang Babala Bago I-activate",
            "Mostrar Advertencias Antes de Activar",
            "Mostrar Advertencias Antes de Activar",
            "有効化する前に警告を表示",
            "सक्रिय करने से पहले चेतावनी दिखाएँ"
        ),
        "Alerts about online game anti-cheat notices, heat buildup, and animation changes" to MultiLangEntry(
            "Alertas sobre avisos anti-cheat em jogos online, aquecimento e alterações de animação",
            "Peringatan tentang anti-cheat game online, suhu panas, dan perubahan animasi",
            "Cảnh báo về chống gian lận game online, nhiệt độ máy và thay đổi hiệu ứng",
            "Mga alerto tungkol sa anti-cheat sa online games, pag-init, at pagbabago sa animation",
            "Alertas sobre avisos antitrampas en juegos en línea, temperatura y cambios de animación",
            "Alertas sobre avisos antitrampas en juegos en línea, temperatura y cambios de animación",
            "オンラインゲームのアンチチート規定、発熱、アニメーション変更に関する通知",
            "ऑनलाइन गेम एंटी-चीट नोटिस, हीटिंग और एनिमेशन बदलावों के बारे में अलर्ट"
        ),
        "Automatic Language Sync • Device Locale Active" to MultiLangEntry(
            "Sincronização de Idioma • Multilíngue Ativo",
            "Sinkronisasi Bahasa Otomatis • Lokal Aktif",
            "Đồng bộ ngôn ngữ tự động • Đa ngôn ngữ",
            "Automatic Language Sync • Aktibo ang Wika",
            "Sincronización Automática de Idioma • Idioma Activo",
            "Sincronización Automática de Idioma • Región Activa",
            "自動言語同期・端末ロケール有効",
            "ऑटो भाषा सिंक • डिवाइस भाषा सक्रिय"
        ),
        "Version 1.0" to MultiLangEntry("Versão 1.0", "Versi 1.0", "Phiên bản 1.0", "Bersyon 1.0", "Versión 1.0", "Versión 1.0", "バージョン 1.0", "संस्करण 1.0"),

        // Hardware Specs & Diagnostics
        "Phone Hardware Specifications" to MultiLangEntry(
            "Especificações de Hardware do Celular",
            "Spesifikasi Perangkat Keras Ponsel",
            "Thông số phần cứng điện thoại",
            "Hardware Specs ng Telepono",
            "Especificaciones de Hardware del Teléfono",
            "Especificaciones de Hardware del Dispositivo",
            "スマートフォン・ハードウェア仕様",
            "फ़ोन हार्डवेयर स्पेसिफिकेशन"
        ),
        "Genuine detected hardware parameters" to MultiLangEntry(
            "Parâmetros reais de hardware detectados",
            "Parameter perangkat keras asli yang terdeteksi",
            "Thông số phần cứng thực tế được phát hiện",
            "Totoong na-detect na hardware parameters",
            "Parámetros de hardware genuinos detectados",
            "Parámetros de hardware reales detectados",
            "検出された実機ハードウェア情報",
            "वास्तविक डिटेक्ट किए गए हार्डवेयर पैरामीटर"
        ),
        "Processor / SoC" to MultiLangEntry("Processador / SoC", "Prosesor / SoC", "Vi xử lý / SoC", "Processor / SoC", "Procesador / SoC", "Procesador / SoC", "プロセッサ / SoC", "प्रोसेसर / SoC"),
        "Cores" to MultiLangEntry("Núcleos", "Inti", "Nhân", "Cores", "Núcleos", "Núcleos", "コア数", "कोर"),
        "Graphics & 3D Engine" to MultiLangEntry("Gráficos e Motor 3D", "Grafis & Mesin 3D", "Đồ họa & Engine 3D", "Graphics at 3D Engine", "Motor Gráfico y 3D", "Gráficos y Motor 3D", "グラフィックス＆3Dエンジン", "ग्राफ़िक्स और 3D इंजन"),
        "Hardware Battery Thermals" to MultiLangEntry("Temperatura da Bateria", "Suhu Baterai Perangkat", "Nhiệt độ pin phần cứng", "Temperatura ng Baterya", "Temperatura de Batería de Hardware", "Temperatura de la Batería", "バッテリー温度", "हार्डवेयर बैटरी तापमान"),
        "Level" to MultiLangEntry("Nível", "Level", "Mức", "Antas", "Nivel", "Nivel", "残量", "स्तर"),
        "System RAM Headroom" to MultiLangEntry("Memória RAM do Sistema", "Kapasitas RAM Sistem", "Bộ nhớ RAM hệ thống", "System RAM Headroom", "Memoria RAM del Sistema", "Memoria RAM del Sistema", "システムRAM空き容量", "सिस्टम RAM क्षमता"),
        "Available of" to MultiLangEntry("Disponíveis de", "Tersedia dari", "Khả dụng trên", "Available sa", "Disponibles de", "Disponibles de", "利用可能 / 合計", "उपलब्ध / कुल"),
        "Internal Flash Storage" to MultiLangEntry("Armazenamento Interno", "Penyimpanan Internal", "Bộ nhớ trong", "Internal Storage", "Almacenamiento Flash Interno", "Almacenamiento Interno", "内部ストレージ", "इंटरनल स्टोरेज"),
        "Free of" to MultiLangEntry("Livres de", "Kosong dari", "Trống trên", "Libre sa", "Libres de", "Libres de", "空き容量 / 合計", "खाली / कुल"),
        "Total" to MultiLangEntry("Total", "Total", "Tổng", "Kabuuan", "Total", "Total", "合計", "कुल"),
        "Display & Refresh Rate" to MultiLangEntry("Tela e Taxa de Atualização", "Layar & Refresh Rate", "Màn hình & Tần số quét", "Display at Refresh Rate", "Pantalla y Tasa de Refresco", "Pantalla y Tasa de Refresco", "ディスプレイ＆リフレッシュレート", "डिस्प्ले और रिफ्रेश रेट"),
        "Device & Operating System" to MultiLangEntry("Dispositivo e Sistema Operacional", "Perangkat & Sistem Operasi", "Thiết bị & Hệ điều hành", "Device at Operating System", "Dispositivo y Sistema Operativo", "Dispositivo y Sistema Operativo", "デバイス＆OS情報", "डिवाइस और ऑपरेटिंग सिस्टम"),

        // Controller & Overlay Strings
        "Controller Layout & Button Icons" to MultiLangEntry(
            "Layout do Controle e Ícones de Botões",
            "Tata Letak Stik & Ikon Tombol",
            "Bố cục tay cầm & Biểu tượng nút",
            "Layout ng Controller at Icon ng Button",
            "Diseño del Control e Iconos de Botones",
            "Diseño del Mando e Iconos de Botones",
            "コントローラー配置＆ボタンアイコン",
            "कंट्रोलर लेआउट और बटन आइकन"
        ),
        "Real-Time Controller Input Screen Overlay" to MultiLangEntry(
            "Sobreposição de Controle em Tempo Real",
            "Overlay Layar Input Stik Real-Time",
            "Lớp phủ tay cầm thời gian thực",
            "Real-Time Controller Input Overlay",
            "Superposición de Control en Tiempo Real",
            "Superposición de Mando en Tiempo Real",
            "リアルタイム・コントローラー入力オーバーレイ",
            "रियल-टाइम कंट्रोलर इनपुट स्क्रीन ओवरले"
        ),
        "Wired USB-C / OTG Connection Required" to MultiLangEntry(
            "Conexão USB-C / OTG com Fio Necessária",
            "Wajib Koneksi Kabel USB-C / OTG",
            "Yêu cầu kết nối cáp USB-C / OTG",
            "Kailangan ng Wired USB-C / OTG Connection",
            "Se Requiere Conexión por Cable USB-C / OTG",
            "Se Requiere Conexión por Cable USB-C / OTG",
            "有線 USB-C / OTG 接続が必要です",
            "वायर्ड USB-C / OTG कनेक्शन आवश्यक है"
        ),
        "Big Screen Console Mode Activated" to MultiLangEntry(
            "Modo Console em Tela Grande Ativado",
            "Mode Konsol Layar Besar Diaktifkan",
            "Đã bật Chế độ Console màn hình lớn",
            "Aktibo na ang Big Screen Console Mode",
            "Modo Consola en Pantalla Grande Activado",
            "Modo Consola en Pantalla Grande Activado",
            "大画面コンソールモード有効",
            "बिग स्क्रीन कंसोल मोड सक्रिय"
        ),
        "Rotate phone to Landscape to activate Console Mode" to MultiLangEntry(
            "Gire o celular na horizontal para ativar o Modo Console",
            "Putar ponsel ke Lanskap untuk mengaktifkan Mode Konsol",
            "Xoay ngang điện thoại để bật Chế độ Console",
            "I-rotate ang telepono nang Pahiga para sa Console Mode",
            "Gira el teléfono en horizontal para activar el Modo Consola",
            "Gira el teléfono en horizontal para activar el Modo Consola",
            "スマートフォンを横向きにしてコンソールモードを起動",
            "कंसोल मोड सक्रिय करने के लिए फ़ोन को लैंडस्केप में घुमाएँ"
        ),
        "Console Mode UI Sounds (Controller Connected Only)" to MultiLangEntry(
            "Sons de Interface no Modo Console (Apenas com Controle)",
            "Suara UI Mode Konsol (Saat Stik Terhubung)",
            "Âm thanh giao diện Console (Khi kết nối tay cầm)",
            "Tunog ng Console Mode UI (Kapag May Controller)",
            "Sonidos de Interfaz en Modo Consola (Solo con Control)",
            "Sonidos de Interfaz en Modo Consola (Solo con Mando)",
            "コンソールモードUI効果音（コントローラー接続時のみ）",
            "कंसोल मोड UI साउंड (केवल कंट्रोलर कनेक्ट होने पर)"
        ),
        "Plays tactile console navigation ticks, button confirmation chimes, and launch chords only when a controller is connected" to MultiLangEntry(
            "Reproduz sons táteis de navegação, confirmação de botões e acordes ao iniciar somente quando um controle está conectado",
            "Memutar suara navigasi konsol, konfirmasi tombol, dan efek peluncuran hanya saat stik terhubung",
            "Phát âm thanh điều hướng console, xác nhận nút và hiệu ứng khởi chạy khi có tay cầm kết nối",
            "Nagpapatugtog ng console navigation ticks at button chimes kapag may nakakonektang controller",
            "Reproduce sonidos táctiles de navegación, confirmación y lanzamiento solo cuando hay un control conectado",
            "Reproduce sonidos táctiles de navegación, confirmación y lanzamiento solo cuando hay un mando conectado",
            "コントローラー接続時のみコンソール操作音や起動サウンドを再生します",
            "केवल कंट्रोलर कनेक्ट होने पर कंसोल नेविगेशन और लॉन्च साउंड बजाता है"
        ),

        // Statuses
        "Connected" to MultiLangEntry("Conectado", "Terhubung", "Đã kết nối", "Nakakonekta", "Conectado", "Conectado", "接続済み", "कनेक्टेड"),
        "Disconnected" to MultiLangEntry("Desconectado", "Terputus", "Đã ngắt kết nối", "Hindi Nakakonekta", "Desconectado", "Desconectado", "未接続", "डिस्कनेक्टेड"),
        "Active" to MultiLangEntry("Ativo", "Aktif", "Đang bật", "Aktibo", "Activo", "Activo", "有効", "सक्रिय"),
        "Inactive" to MultiLangEntry("Inativo", "Tidak Aktif", "Đang tắt", "Hindi Aktibo", "Inactivo", "Inactivo", "無効", "निष्क्रिय"),
        "Enabled" to MultiLangEntry("Ativado", "Diaktifkan", "Đã bật", "Naka-enable", "Activado", "Activado", "オン", "चालू"),
        "Disabled" to MultiLangEntry("Desativado", "Dinonaktifkan", "Đã tắt", "Naka-disable", "Desactivado", "Desactivado", "オフ", "बंद"),
        "Optimal" to MultiLangEntry("Ideal", "Optimal", "Tối ưu", "Optimal", "Óptimo", "Óptimo", "最適", "उत्तम"),
        "Warm" to MultiLangEntry("Morno", "Hangat", "Ấm", "Medyo Mainit", "Templado", "Templado", "やや高温", "सामान्य गर्म"),
        "Hot" to MultiLangEntry("Quente", "Panas", "Nóng", "Mainit", "Caliente", "Caliente", "高温", "गर्म"),
        "Critical" to MultiLangEntry("Crítico", "Kritis", "Nguy kịch", "Kritikal", "Crítico", "Crítico", "危険温度", "अत्यधिक गर्म"),

        // Smart Push Notifications Titles & UI Labels
        "Smart Gaming Notifications (Max 4/Week)" to MultiLangEntry(
            "Notificações Inteligentes (Máx. 4/Semana)",
            "Notifikasi Gaming Pintar (Maks 4/Minggu)",
            "Thông báo Chơi game Thông minh (Tối đa 4/Tuần)",
            "Smart Gaming Notifications (Max 4/Linggo)",
            "Notificaciones Inteligentes (Máx. 4/Semana)",
            "Notificaciones Inteligentes (Máx. 4/Semana)",
            "スマートゲーミング通知（週最大4回）",
            "स्मार्ट गेमिंग नोटिफिकेशन (अधिकतम 4/सप्ताह)"
        ),
        "Intelligent reminders for Clock Overlay, 4K 120fps Recorder, Peak Play-Time Modeling, Post-Session Wrap-Up, and Weekly Stats" to MultiLangEntry(
            "Lembretes inteligentes para Relógio na Tela, Gravador 4K 120fps, Horário de Jogo, Resumo Pós-Sessão e Estatísticas Semanais",
            "Pengingat cerdas untuk Overlay Jam, Perekam 4K 120fps, Waktu Main Puncak, Ringkasan Sesi, dan Statistik Mingguan",
            "Nhắc nhở thông minh cho Đồng hồ nổi, Ghi hình 4K 120fps, Giờ chơi cao điểm, Tổng kết phiên và Thống kê tuần",
            "Matalinong paalala para sa Clock Overlay, 4K 120fps Recorder, Peak Play-Time, Post-Session Wrap-Up, at Weekly Stats",
            "Recordatorios inteligentes de Reloj en Pantalla, Grabadora 4K 120fps, Hora Pico de Juego, Resumen de Sesión y Estadísticas Semanales",
            "Recordatorios inteligentes de Reloj en Pantalla, Grabador 4K 120fps, Hora Pico de Juego, Resumen de Sesión y Estadísticas Semanales",
            "時計オーバーレイ、4K 120fps録画、ピークプレイ時間、セッション集計、週間統計のスマート通知",
            "क्लॉक ओवरले, 4K 120fps रिकॉर्डर, पीक प्ले-टाइम, सेशन सारांश और साप्ताहिक आंकड़ों के लिए स्मार्ट रिमाइंडर"
        ),
        "Forgetting the Time after a long gaming session? Turn on your clock overlay to keep up with the time" to MultiLangEntry(
            "Perdendo a noção da hora após uma longa sessão de jogo? Ative o relógio na sobreposição para acompanhar o tempo",
            "Lupa waktu setelah sesi gaming panjang? Nyalakan overlay jam untuk tetap memantau waktu",
            "Quên thời gian sau phiên chơi game dài? Hãy bật đồng hồ trên lớp phủ để theo dõi thời gian",
            "Nakakalimutan ang oras pagkatapos ng mahabang laro? Buksan ang clock overlay para mabantayan ang oras",
            "¿Olvidas la hora después de una larga sesión de juego? Activa el reloj en pantalla para llevar el control del tiempo",
            "¿Pierdes la noción del tiempo al jugar? Activa el reloj en pantalla para controlar la hora",
            "長時間のゲームで時間を忘れていませんか？時計オーバーレイをオンにして時間を確認しましょう",
            "लंबे गेमिंग सेशन में समय भूल जाते हैं? समय पर नज़र रखने के लिए अपना क्लॉक ओवरले चालू करें"
        ),
        "Don't miss a clutch moment. Enable the up to 4K 120fps screen recorder before you launch." to MultiLangEntry(
            "Não perca uma jogada decisiva. Ative o gravador de tela em até 4K 120fps antes de iniciar.",
            "Jangan lewatkan momen clutch. Aktifkan perekam layar hingga 4K 120fps sebelum memulai game.",
            "Đừng bỏ lỡ khoảnh khắc xuất thần. Hãy bật trình ghi màn hình lên đến 4K 120fps trước khi vào trận.",
            "Huwag palampasin ang clutch moment. I-enable ang hanggang 4K 120fps screen recorder bago maglaro.",
            "No te pierdas un momento épico. Activa la grabadora de pantalla de hasta 4K 120fps antes de jugar.",
            "No te pierdas una jugada épica. Activa el grabador de pantalla de hasta 4K 120fps antes de iniciar.",
            "決定的な瞬間を逃さないために、起動前に最大4K 120fpsの画面レコーダーを有効にしましょう。",
            "कोई भी शानदार पल मिस न करें। गेम शुरू करने से पहले 4K 120fps स्क्रीन रिकॉर्डर चालू करें।"
        ),
        "Time to play? Launch through GMAX to clear background RAM and lock in your frame rate." to MultiLangEntry(
            "Hora de jogar? Inicie pelo GMAX para limpar a RAM em segundo plano e estabilizar sua taxa de quadros.",
            "Waktunya main? Mulai lewat GMAX untuk membersihkan RAM latar belakang dan mengunci frame rate Anda.",
            "Đến giờ chiến game? Khởi chạy qua GMAX để dọn RAM nền và khóa tốc độ khung hình ổn định.",
            "Oras na para maglaro? Ilunsad sa GMAX para linisin ang background RAM at i-lock ang iyong frame rate.",
            "¿Hora de jugar? Inicia desde GMAX para liberar RAM en segundo plano y asegurar tus FPS.",
            "¿Hora de jugar? Inicia desde GMAX para liberar RAM en segundo plano y estabilizar tus FPS.",
            "プレイの時間ですか？GMAXから起動してバックグラウンドRAMを解放し、FPSを安定させましょう。",
            "खेलने का समय हो गया? बैकग्राउंड RAM साफ़ करने और फ़्रेम रेट स्थिर करने के लिए GMAX से गेम लॉन्च करें।"
        ),
        "Your games are ready for Focus Mode. Jump back in for a lag free session." to MultiLangEntry(
            "Seus jogos estão prontos para o Modo Foco. Volte a jogar para uma sessão sem travamentos.",
            "Game Anda siap untuk Mode Fokus. Kembali bermain untuk sesi bebas lag.",
            "Các trò chơi đã sẵn sàng cho Chế độ Tập trung. Quay lại ngay để trải nghiệm phiên chơi mượt mà.",
            "Handa na ang mga laro mo para sa Focus Mode. Bumalik na para sa lag-free na gaming session.",
            "Tus juegos están listos para el Modo Enfoque. Vuelve a jugar para una sesión sin lag.",
            "Tus juegos están listos para el Modo Enfoque. Vuelve a jugar para una partida fluida y sin lag.",
            "ゲームがフォーカスモードの準備完了です。ラグのない快適なセッションに戻りましょう。",
            "आपके गेम्स फ़ोकस मोड के लिए तैयार हैं। बिना लैग के स्मूथ गेमिंग सेशन के लिए वापस आएँ।"
        ),
        "Gameplay stuttering lately? Run a quick diagnostic to optimize your device right now." to MultiLangEntry(
            "O jogo está engasgando ultimamente? Execute um diagnóstico rápido para otimizar seu aparelho agora mesmo.",
            "Gameplay sering patah-patah belakangan ini? Jalankan diagnostik cepat untuk mengoptimalkan perangkat Anda sekarang.",
            "Chơi game gần đây bị giật khung hình? Chạy chẩn đoán nhanh để tối ưu hóa thiết bị của bạn ngay bây giờ.",
            "Nagla-lag ba ang laro mo kamakailan? Magpatakbo ng mabilis na diagnostic para i-optimize ang device mo ngayon.",
            "¿Tu juego tiene tirones últimamente? Ejecuta un diagnóstico rápido para optimizar tu dispositivo ahora mismo.",
            "¿Notaste tirones al jugar últimamente? Ejecuta un diagnóstico rápido para optimizar tu dispositivo ahora mismo.",
            "最近ゲームがカクつきますか？クイック診断を実行して今すぐ端末を最適化しましょう。",
            "क्या हाल ही में गेम अटक रहा है? अपने डिवाइस को तुरंत ऑप्टिमाइज़ करने के लिए क्विक डायग्नोस्टिक चलाएँ।"
        ),

        // Unity Ads Banner, Rewarded & Consent Localization
        "Ad" to MultiLangEntry(
            "Anúncio",
            "Iklan",
            "Quảng cáo",
            "Ad",
            "Anuncio",
            "Anuncio",
            "広告",
            "विज्ञापन"
        ),
        "Loading Ad..." to MultiLangEntry(
            "Carregando anúncio...",
            "Memuat iklan...",
            "Đang tải quảng cáo...",
            "Nilo-load ang ad...",
            "Cargando anuncio...",
            "Cargando anuncio...",
            "広告を読み込み中...",
            "विज्ञापन लोड हो रहा है..."
        ),
        "Loading Sponsored Ad..." to MultiLangEntry(
            "Carregando anúncio patrocinado...",
            "Memuat iklan bersponsor...",
            "Đang tải quảng cáo được tài trợ...",
            "Nilo-load ang sponsored ad...",
            "Cargando anuncio patrocinado...",
            "Cargando anuncio patrocinado...",
            "スポンサー広告を読み込み中...",
            "प्रायोजित विज्ञापन लोड हो रहा है..."
        ),
        "Loading Rewarded Video Ad..." to MultiLangEntry(
            "Carregando vídeo premiado...",
            "Memuat iklan video berhadiah...",
            "Đang tải video quảng cáo có thưởng...",
            "Nilo-load ang rewarded video ad...",
            "Cargando video recompensado...",
            "Cargando video recompensado...",
            "リワード動画広告を読み込み中...",
            "रिवार्डेड वीडियो विज्ञापन लोड हो रहा है..."
        ),
        "Loading Unity Rewarded Ad..." to MultiLangEntry(
            "Carregando anúncio premiado do Unity...",
            "Memuat iklan berhadiah Unity...",
            "Đang tải quảng cáo có thưởng Unity...",
            "Nilo-load ang Unity rewarded ad...",
            "Cargando anuncio recompensado de Unity...",
            "Cargando anuncio recompensado de Unity...",
            "Unityリワード広告を読み込み中...",
            "Unity रिवार्डेड विज्ञापन लोड हो रहा है..."
        ),
        "Unity Ads could not load a video ad right now. Please check your connection and tap Retry." to MultiLangEntry(
            "O Unity Ads não conseguiu carregar um vídeo agora. Verifique sua conexão e toque em Tentar Novamente.",
            "Unity Ads tidak dapat memuat iklan video saat ini. Periksa koneksi Anda dan ketuk Coba Lagi.",
            "Unity Ads hiện không thể tải quảng cáo video. Vui lòng kiểm tra kết nối và nhấn Thử lại.",
            "Hindi ma-load ng Unity Ads ang video ad ngayon. Suriin ang koneksyon at pindutin ang Subukan Muli.",
            "Unity Ads no pudo cargar un video en este momento. Verifica tu conexión y toca Reintentar.",
            "Unity Ads no pudo cargar un video en este momento. Revisa tu conexión y toca Reintentar.",
            "現在Unity Adsの動画広告を読み込めませんでした。接続を確認して再試行してください。",
            "Unity Ads अभी वीडियो विज्ञापन लोड नहीं कर सका। कृपया अपना कनेक्शन जांचें और पुनः प्रयास करें।"
        ),
        "Retry Ad" to MultiLangEntry(
            "Tentar Novamente",
            "Coba Lagi Iklan",
            "Thử lại",
            "Subukan Muli",
            "Reintentar",
            "Reintentar",
            "再試行",
            "पुनः प्रयास करें"
        ),
        "Ad Privacy & Preferences" to MultiLangEntry(
            "Privacidade e Preferências de Anúncios",
            "Privasi & Preferensi Iklan",
            "Quyền riêng tư & Tùy chọn Quảng cáo",
            "Privacy at Mga Kagustuhan sa Ad",
            "Privacidad y Preferencias de Anuncios",
            "Privacidad y Preferencias de Anuncios",
            "広告のプライバシーと設定",
            "विज्ञापन गोपनीयता और प्राथमिकताएँ"
        ),
        "G Max Gaming Hub uses Unity Ads to keep core optimization features free. Choose whether to allow personalized gaming ads using your device Advertising ID, or use non-personalized ads only." to MultiLangEntry(
            "O G Max Gaming Hub usa o Unity Ads para manter os recursos principais gratuitos. Escolha permitir anúncios personalizados com seu ID de publicidade ou usar apenas anúncios não personalizados.",
            "G Max Gaming Hub menggunakan Unity Ads agar fitur optimasi inti tetap gratis. Pilih untuk mengizinkan iklan game yang dipersonalisasi menggunakan ID Iklan perangkat Anda, atau hanya iklan non-personalisasi.",
            "G Max Gaming Hub sử dụng Unity Ads để duy trì miễn phí các tính năng tối ưu hóa chính. Chọn cho phép quảng cáo trò chơi được cá nhân hóa bằng ID Quảng cáo của thiết bị hoặc chỉ quảng cáo không cá nhân hóa.",
            "Ginagamit ng G Max Gaming Hub ang Unity Ads upang manatiling libre ang mga pangunahing tampok ng pag-optimize. Piliin kung papayagan ang mga naka-personalize na ad o mga non-personalized na ad lamang.",
            "G Max Gaming Hub usa Unity Ads para mantener gratis las funciones de optimización. Elige si deseas permitir anuncios personalizados con tu ID de publicidad o solo anuncios no personalizados.",
            "G Max Gaming Hub usa Unity Ads para mantener gratuitas las funciones de optimización. Elige si deseas permitir anuncios personalizados con tu ID de publicidad o solo anuncios no personalizados.",
            "G Max Gaming Hubは、主要な最適化機能を無料で提供するためにUnity Adsを使用しています。端末の広告IDを使用したパーソナライズ広告を許可するか、非パーソナライズ広告のみにするか選択してください。",
            "G Max Gaming Hub मुख्य ऑप्टिमाइज़ेशन फ़ीचर को मुफ़्त रखने के लिए Unity Ads का उपयोग करता है। चुनें कि आपके डिवाइस की विज्ञापन ID का उपयोग करके वैयक्तिकृत गेमिंग विज्ञापन दिखाए जाएँ या केवल गैर-वैयक्तिकृत विज्ञापन।"
        ),
        "You can update this privacy preference at any time inside Settings → Ad Privacy & Consent." to MultiLangEntry(
            "Você pode atualizar esta preferência a qualquer momento em Configurações → Privacidade e Consentimento de Anúncios.",
            "Anda dapat memperbarui preferensi privasi ini kapan saja di Pengaturan → Privasi & Persetujuan Iklan.",
            "Bạn có thể cập nhật tùy chọn quyền riêng tư này bất kỳ lúc nào trong Cài đặt → Quyền riêng tư & Chấp thuận Quảng cáo.",
            "Maaari mong i-update ang kagustuhang ito anumang oras sa Settings → Ad Privacy & Consent.",
            "Puedes cambiar esta preferencia en cualquier momento desde Ajustes → Privacidad y Consentimiento de Anuncios.",
            "Puedes actualizar esta preferencia en cualquier momento desde Configuración → Privacidad y Consentimiento de Anuncios.",
            "このプライバシー設定は「設定 → 広告のプライバシーと同意」からいつでも変更できます。",
            "आप इस गोपनीयता प्राथमिकता को कभी भी सेटिंग्स → विज्ञापन गोपनीयता और सहमति में बदल सकते हैं।"
        ),
        "Accept Personalized Ads" to MultiLangEntry(
            "Aceitar Anúncios Personalizados",
            "Terima Iklan Personalisasi",
            "Chấp nhận Quảng cáo Cá nhân hóa",
            "Tanggapin ang Personalized Ads",
            "Aceptar Anuncios Personalizados",
            "Aceptar Anuncios Personalizados",
            "パーソナライズ広告を許可する",
            "वैयक्तिकृत विज्ञापन स्वीकार करें"
        ),
        "Non-Personalized Ads Only" to MultiLangEntry(
            "Apenas Anúncios Não Personalizados",
            "Hanya Iklan Non-Personalisasi",
            "Chỉ Quảng cáo Không Cá nhân hóa",
            "Non-Personalized Ads Lamang",
            "Solo Anuncios No Personalizados",
            "Solo Anuncios No Personalizados",
            "非パーソナライズ広告のみ",
            "केवल गैर-वैयक्तिकृत विज्ञापन"
        ),
        "Ad Privacy & Consent" to MultiLangEntry(
            "Privacidade e Consentimento de Anúncios",
            "Privasi & Persetujuan Iklan",
            "Quyền riêng tư & Chấp thuận Quảng cáo",
            "Ad Privacy at Pahintulot",
            "Privacidad y Consentimiento de Anuncios",
            "Privacidad y Consentimiento de Anuncios",
            "広告のプライバシーと同意",
            "विज्ञापन गोपनीयता और सहमति"
        ),
        "Manage Unity Ads personalization and Advertising ID consent preferences" to MultiLangEntry(
            "Gerencie a personalização do Unity Ads e as preferências do ID de publicidade",
            "Kelola personalisasi Unity Ads dan preferensi persetujuan ID Iklan",
            "Quản lý cá nhân hóa Unity Ads và tùy chọn ID Quảng cáo",
            "Pamahalaan ang pag-personalize ng Unity Ads at mga kagustuhan sa Advertising ID",
            "Administra la personalización de anuncios de Unity Ads y tu ID de publicidad",
            "Administra la personalización de Unity Ads y las preferencias del ID de publicidad",
            "Unity Adsのパーソナライズ設定と広告IDの同意を管理します",
            "Unity Ads वैयक्तिकरण और विज्ञापन ID सहमति प्राथमिकताएँ प्रबंधित करें"
        ),
        "PERSONALIZED" to MultiLangEntry(
            "PERSONALIZADO",
            "DIPERSONALISASI",
            "CÁ NHÂN HÓA",
            "PERSONALIZED",
            "PERSONALIZADO",
            "PERSONALIZADO",
            "パーソナライズ済",
            "वैयक्तिकृत"
        ),
        "LIMITED" to MultiLangEntry(
            "LIMITADO",
            "TERBATAS",
            "GIỚI HẠN",
            "LIMITADO",
            "LIMITADO",
            "LIMITADO",
            "制限付き",
            "सीमित"
        ),
        "Personalized gaming ads enabled via Unity Ads privacy consent." to MultiLangEntry(
            "Anúncios de jogos personalizados ativados via consentimento do Unity Ads.",
            "Iklan game yang dipersonalisasi diaktifkan melalui persetujuan privasi Unity Ads.",
            "Quảng cáo trò chơi được cá nhân hóa đã bật theo sự chấp thuận Unity Ads.",
            "Pinagana ang naka-personalize na gaming ads gamit ang Unity Ads privacy consent.",
            "Anuncios de juegos personalizados activados mediante tu consentimiento de Unity Ads.",
            "Anuncios de juegos personalizados activados mediante tu consentimiento de Unity Ads.",
            "Unity Adsのプライバシー同意によりパーソナライズ広告が有効です。",
            "Unity Ads गोपनीयता सहमति के माध्यम से वैयक्तिकृत गेमिंग विज्ञापन सक्षम हैं।"
        ),
        "Non-personalized ads only enabled via Unity Ads privacy consent." to MultiLangEntry(
            "Apenas anúncios não personalizados ativados via consentimento do Unity Ads.",
            "Hanya iklan non-personalisasi yang diaktifkan melalui persetujuan privasi Unity Ads.",
            "Chỉ bật quảng cáo không cá nhân hóa theo sự chấp thuận Unity Ads.",
            "Mga non-personalized na ad lamang ang pinagana sa Unity Ads privacy consent.",
            "Solo anuncios no personalizados activados mediante tu consentimiento de Unity Ads.",
            "Solo anuncios no personalizados activados mediante tu consentimiento de Unity Ads.",
            "Unity Adsのプライバシー同意により非パーソナライズ広告のみ有効です。",
            "Unity Ads गोपनीयता सहमति के माध्यम से केवल गैर-वैयक्तिकृत विज्ञापन सक्षम हैं।"
        ),
        "Manage Ad Privacy Preferences" to MultiLangEntry(
            "Gerenciar Preferências de Privacidade",
            "Kelola Preferensi Privasi Iklan",
            "Quản lý Quyền riêng tư Quảng cáo",
            "Pamahalaan ang Ad Privacy",
            "Administrar Privacidad de Anuncios",
            "Administrar Privacidad de Anuncios",
            "広告プライバシー設定を管理",
            "विज्ञापन गोपनीयता प्राथमिकताएँ प्रबंधित करें"
        ),
        "Unlock Custom RGB & Overlay (24h)" to MultiLangEntry(
            "Desbloquear RGB e Sobreposição (24h)",
            "Buka Kunci RGB Kustom & Overlay (24j)",
            "Mở khóa RGB Tùy chỉnh & Lớp phủ (24g)",
            "I-unlock ang Custom RGB at Overlay (24h)",
            "Desbloquea RGB y Superposición (24h)",
            "Desbloquea RGB y Superposición (24h)",
            "カスタムRGB＆オーバーレイを解除 (24時間)",
            "कस्टम RGB और ओवरले अनलॉक करें (24 घंटे)"
        ),
        "Banner Debug Status" to MultiLangEntry(
            "Status de Depuração do Banner",
            "Status Debug Banner",
            "Trạng thái Gỡ lỗi Biểu ngữ",
            "Katayuan ng Banner Debug",
            "Estado Depuración Banner",
            "Estado de Depuración de Banner",
            "バナーデバッグ状態",
            "बैनर डिबग स्थिति"
        ),
        "Use Live Mode" to MultiLangEntry(
            "Usar Modo Real",
            "Gunakan Mode Langsung",
            "Dùng Chế độ Thực",
            "Gamitin ang Live Mode",
            "Usar Modo Real",
            "Usar Modo Real",
            "本番モードを使う",
            "लाइव मोड उपयोग करें"
        ),
        "Force Test Mode" to MultiLangEntry(
            "Forçar Modo Teste",
            "Paksa Mode Uji",
            "Bật Chế độ Thử",
            "Pilitin ang Test Mode",
            "Forzar Modo Test",
            "Forzar Modo Test",
            "テストモードを強制",
            "टेस्ट मोड लागू करें"
        ),

        // All 9 PerformanceOption Tweaks (Titles, Categories, Tags, Descriptions)
        "Kernel RAM Purge & Memory Compaction" to MultiLangEntry(
            "Limpeza de Memória de Acesso Aleatório do Kernel e Compactação",
            "Pembersihan Memori Akses Acak Kernel & Pemadatan Memori",
            "Giải Phóng Bộ Nhớ Truy Cập Ngẫu Nhiên Nhân Hệ Thống & Nén Bộ Nhớ",
            "Paglilinis ng Memorya ng Random na Access sa Kernel at Pag-compact",
            "Purga de Memoria de Acceso Aleatorio del Kernel y Compactación",
            "Purga de Memoria de Acceso Aleatorio del Kernel y Compactación",
            "カーネル・ランダムアクセスメモリ解放＆メモリ圧縮",
            "कर्नेल रैंडम एक्सेस मेमोरी क्लीन और मेमोरी कॉम्पैक्शन"
        ),
        "Memory & Kernel" to MultiLangEntry(
            "Memória e Kernel",
            "Memori & Kernel",
            "Bộ Nhớ & Nhân Hệ Thống",
            "Memorya at Kernel",
            "Memoria y Kernel",
            "Memoria y Kernel",
            "メモリ＆カーネル",
            "मेमोरी और कर्नेल"
        ),
        "Memory" to MultiLangEntry(
            "Memória",
            "Memori",
            "Bộ Nhớ",
            "Memorya",
            "Memoria",
            "Memoria",
            "メモリ",
            "मेमोरी"
        ),
        "RAM Clean | Cache Trim | GC" to MultiLangEntry(
            "Limpeza de Memória | Corte de Cache | Coleta de Lixo",
            "Pembersihan Memori | Pangkas Cache | Pembersihan Sampah",
            "Dọn Bộ Nhớ | Cắt Bộ Nhớ Đệm | Thu Gom Rác",
            "Linis Memorya | Bawas Cache | Linis Basura",
            "Limpieza de Memoria | Recorte de Caché | Recolección de Basura",
            "Limpieza de Memoria | Recorte de Caché | Recolección de Basura",
            "メモリ解放 | キャッシュ整理 | ガベージコレクション",
            "मेमोरी सफ़ाई | कैश ट्रिम | गार्बेज कलेक्शन"
        ),
        "Directly terminates cached background process memory and forces JVM garbage collection to guarantee texture buffer headroom." to MultiLangEntry(
            "Encerra diretamente processos em cache em segundo plano e força a limpeza de memória para garantir espaço para texturas.",
            "Menghentikan langsung memori proses latar belakang dan memaksa pembersihan memori untuk menjamin ruang tekstur.",
            "Trực tiếp đóng bộ nhớ tiến trình nền và buộc thu gom rác bộ nhớ để đảm bảo dung lượng đệm kết cấu đồ họa.",
            "Direktang tinatapos ang naka-cache na proseso sa background at nililinis ang memorya para sa espasyo ng texture.",
            "Termina directamente los procesos en caché en segundo plano y fuerza la recolección de basura para garantizar margen de texturas.",
            "Finaliza directamente los procesos en caché en segundo plano y fuerza la liberación de memoria para asegurar margen de texturas.",
            "バックグラウンドのキャッシュプロセスを直接終了し、メモリ回収を実行してテクスチャバッファの空き容量を確保します。",
            "टेक्सचर बफ़र स्पेस सुनिश्चित करने के लिए बैकग्राउंड कैश प्रोसेस मेमोरी को सीधे समाप्त करता है और मेमोरी क्लीनअप करता है।"
        ),
        "Wireless Low Latency Radio Lock" to MultiLangEntry(
            "Bloqueio de Rádio Sem Fio de Baixa Latência",
            "Kunci Radio Nirkabel Latensi Rendah",
            "Khóa Sóng Không Dây Độ Trễ Thấp",
            "Lock ng Wireless Radio na Mababang Latency",
            "Bloqueo de Radio Inalámbrico de Baja Latencia",
            "Bloqueo de Radio Inalámbrico de Baja Latencia",
            "ワイヤレス低遅延無線ロック",
            "वायरलेस लो-लेटेंसी रेडियो लॉक"
        ),
        "Network Subsystem" to MultiLangEntry(
            "Subsistema de Rede",
            "Subsistem Jaringan",
            "Hệ Thống Mạng",
            "Subsystem ng Network",
            "Subsistema de Red",
            "Subsistema de Red",
            "ネットワークサブシステム",
            "नेटवर्क सबसिस्टम"
        ),
        "Network" to MultiLangEntry(
            "Rede",
            "Jaringan",
            "Mạng",
            "Network",
            "Red",
            "Red",
            "ネットワーク",
            "नेटवर्क"
        ),
        "Wireless Lock | Hardware Polling | Low Jitter" to MultiLangEntry(
            "Trava Sem Fio | Sondagem de Hardware | Baixa Oscilação",
            "Kunci Nirkabel | Polling Perangkat Keras | Jitter Rendah",
            "Khóa Không Dây | Quét Phần Cứng | Giảm Dao Động Mạng",
            "Wireless Lock | Hardware Polling | Mababang Jitter",
            "Bloqueo Inalámbrico | Sondeo de Hardware | Baja Variación",
            "Bloqueo Inalámbrico | Sondeo de Hardware | Baja Fluctuación",
            "無線ロック | ハードウェアポーリング | 低ジッター",
            "वायरलेस लॉक | हार्डवेयर पोलिंग | कम जिटर"
        ),
        "Directly acquires the Android wireless low latency radio lock (WIFI_MODE_FULL_LOW_LATENCY) to eliminate packet jitter in multiplayer matches." to MultiLangEntry(
            "Ativa diretamente o bloqueio de rádio sem fio de baixa latência do Android para eliminar oscilações de pacotes em partidas multijogador.",
            "Mengaktifkan langsung kunci radio nirkabel latensi rendah Android untuk menghilangkan jitter paket dalam pertandingan multipemain.",
            "Trực tiếp kích hoạt khóa sóng không dây độ trễ thấp của Android để loại bỏ giật gói tin trong các trận đấu nhiều người chơi.",
            "Direktang binubuksan ang wireless low latency radio lock ng Android upang maalis ang pagkaantala ng packet sa multiplayer.",
            "Adquiere directamente el bloqueo de radio inalámbrico de baja latencia de Android para eliminar la variación de paquetes en partidas multijugador.",
            "Activa directamente el bloqueo de radio inalámbrico de baja latencia de Android para eliminar la fluctuación de paquetes en partidas multijugador.",
            "Androidの低遅延ワイヤレス無線ロックを直接取得し、マルチプレイヤー対戦でのパケット遅延やジッターを解消します。",
            "मल्टीप्लेयर मैचों में पैकेट जिटर को खत्म करने के लिए सीधे एंड्रॉइड वायरलेस लो-लेटेंसी रेडियो लॉक सक्रिय करता है।"
        ),
        "Directly acquires the Android wireless low latency radio lock to eliminate packet jitter in multiplayer matches." to MultiLangEntry(
            "Ativa diretamente o bloqueio de rádio sem fio de baixa latência do Android para eliminar oscilações de pacotes em partidas multijogador.",
            "Mengaktifkan langsung kunci radio nirkabel latensi rendah Android untuk menghilangkan jitter paket dalam pertandingan multipemain.",
            "Trực tiếp kích hoạt khóa sóng không dây độ trễ thấp của Android để loại bỏ giật gói tin trong các trận đấu nhiều người chơi.",
            "Direktang binubuksan ang wireless low latency radio lock ng Android upang maalis ang pagkaantala ng packet sa multiplayer.",
            "Adquiere directamente el bloqueo de radio inalámbrico de baja latencia de Android para eliminar la variación de paquetes en partidas multijugador.",
            "Activa directamente el bloqueo de radio inalámbrico de baja latencia de Android para eliminar la fluctuación de paquetes en partidas multijugador.",
            "Androidの低遅延ワイヤレス無線ロックを直接取得し、マルチプレイヤー対戦でのパケットジッターを解消します。",
            "मल्टीप्लेयर मैचों में पैकेट जिटर को खत्म करने के लिए सीधे एंड्रॉइड वायरलेस लो-लेटेंसी रेडियो लॉक सक्रिय करता है।"
        ),
        "Sustained Hardware Performance Mode" to MultiLangEntry(
            "Modo de Desempenho de Hardware Sustentado",
            "Mode Performa Perangkat Keras Berkelanjutan",
            "Chế Độ Hiệu Năng Phần Cứng Duy Trì Ổn Định",
            "Mode ng Tuloy-tuloy na Pagganap ng Hardware",
            "Modo de Rendimiento de Hardware Sostenido",
            "Modo de Rendimiento de Hardware Sostenido",
            "持続的ハードウェアパフォーマンスモード",
            "निरंतर हार्डवेयर परफॉर्मेंस मोड"
        ),
        "CPU & GPU Governor" to MultiLangEntry(
            "Controlador do Processador Central e Gráfico",
            "Pengatur Unit Pemrosesan Pusat & Grafis",
            "Bộ Điều Tốc Vi Xử Lý Trung Tâm & Đồ Họa",
            "Governor ng Sentral at Grapikong Prosesor",
            "Gobernador del Procesador Central y Gráfico",
            "Gobernador de Procesador Central y Gráfico",
            "中央演算処理装置・画像処理装置ガバナー",
            "केंद्रीय और ग्राफ़िक्स प्रोसेसर गवर्नर"
        ),
        "Governor" to MultiLangEntry(
            "Controlador de Frequência",
            "Pengatur Performa",
            "Bộ Điều Tốc",
            "Tagapamahala ng Bilis",
            "Gobernador de Frecuencia",
            "Gobernador de Frecuencia",
            "周波数ガバナー",
            "फ़्रीक्वेंसी गवर्नर"
        ),
        "PowerManager | Sustained Clocks | Stable Speed" to MultiLangEntry(
            "Gerenciador de Energia | Frequências Sustentadas | Velocidade Estável",
            "Manajer Daya | Frekuensi Stabil | Kecepatan Konstan",
            "Quản Lý Nguồn | Xung Nhịp Ổn Định | Tốc Độ Bền Bỉ",
            "Power Manager | Matatag na Bilis | Tuloy-tuloy na Lakas",
            "Gestor de Energía | Relojes Sostenidos | Velocidad Estable",
            "Gestor de Energía | Frecuencias Sostenidas | Velocidad Estable",
            "電源管理 | 持続クロック維持 | 安定速度",
            "पावर प्रबंधन | स्थिर क्लॉक गति | निरंतर रफ़्तार"
        ),
        "Window | Sustained Clocks | Stable Speed" to MultiLangEntry(
            "Janela | Frequências Sustentadas | Velocidade Estável",
            "Jendela | Frekuensi Stabil | Kecepatan Konstan",
            "Cửa Sổ | Xung Nhịp Ổn Định | Tốc Độ Bền Bỉ",
            "Window | Matatag na Bilis | Tuloy-tuloy na Lakas",
            "Ventana | Relojes Sostenidos | Velocidad Estable",
            "Ventana | Frecuencias Sostenidas | Velocidad Estable",
            "ウィンドウ | 持続クロック維持 | 安定速度",
            "विंडो | स्थिर क्लॉक गति | निरंतर रफ़्तार"
        ),
        "Signals the Android window manager to engage sustained performance governor mode, preventing sudden CPU frequency throttling." to MultiLangEntry(
            "Sinaliza ao gerenciador de janelas do Android para ativar o modo de desempenho sustentado, evitando quedas bruscas de frequência do Processador Central.",
            "Memberi sinyal ke pengelola jendela Android untuk mengaktifkan mode performa berkelanjutan, mencegah penurunan frekuensi Unit Pemrosesan Pusat secara tiba-tiba.",
            "Báo hiệu trình quản lý cửa sổ Android bật chế độ hiệu năng duy trì, ngăn chặn giảm xung nhịp Bộ Vi Xử Lý Trung Tâm đột ngột.",
            "Nagbibigay-hudyat sa Android window manager na gamitin ang sustained performance mode upang maiwasan ang biglaang pagbagal ng Sentral na Prosesor.",
            "Indica al gestor de ventanas de Android que active el modo de rendimiento sostenido, evitando caídas repentinas de frecuencia del Procesador Central.",
            "Indica al gestor de ventanas de Android que active el modo de rendimiento sostenido, evitando reducciones bruscas de frecuencia en la Unidad Central de Procesamiento.",
            "Androidウィンドウマネージャーに持続パフォーマンスモードを要求し、中央演算処理装置の急激なクロック低下を防止します。",
            "अचानक केंद्रीय प्रोसेसर की गति घटने से रोकने के लिए एंड्रॉइड विंडो मैनेजर को निरंतर परफॉर्मेंस मोड सक्रिय करने का संकेत देता है।"
        ),
        "Requests Android sustained performance mode to prevent CPU/GPU downclocking during lengthy gaming sessions." to MultiLangEntry(
            "Solicita o modo de desempenho sustentado do Android para evitar redução de velocidade do Processador Central e Gráfico em sessões longas.",
            "Meminta mode performa berkelanjutan Android untuk mencegah penurunan kecepatan Unit Pemrosesan Pusat dan Grafis selama sesi bermain panjang.",
            "Yêu cầu chế độ hiệu năng duy trì của Android để ngăn giảm xung nhịp Bộ Vi Xử Lý Trung Tâm và Đồ Họa trong các phiên chơi dài.",
            "Humihiling ng Android sustained performance mode upang maiwasan ang pagbagal ng Sentral at Grapikong Prosesor sa mahabang laro.",
            "Solicita el modo de rendimiento sostenido de Android para evitar la reducción de velocidad del Procesador Central y Gráfico en sesiones largas.",
            "Solicita el modo de rendimiento sostenido de Android para evitar caídas de frecuencia del Procesador Central y Gráfico en partidas prolongadas.",
            "長時間のゲームセッション中に中央演算処理装置および画像処理装置の速度低下を防ぐため、Android持続パフォーマンスモードを要求します。",
            "लंबे गेमिंग सेशन के दौरान केंद्रीय और ग्राफ़िक्स प्रोसेसर की गति कम होने से रोकने के लिए एंड्रॉइड निरंतर परफॉर्मेंस मोड सक्रिय करता है।"
        ),
        "Live Screen HUD (FPS, Temp, Battery)" to MultiLangEntry(
            "Painel Visual na Tela ao Vivo (Quadros por Segundo, Temperatura, Bateria)",
            "Tampilan Informasi Layar Langsung (Bingkai per Detik, Suhu, Baterai)",
            "Bảng Hiển Thị Trên Màn Hình Trực Tiếp (Khung Hình Mỗi Giây, Nhiệt Độ, Pin)",
            "Live na Impormasyon sa Ibabaw ng Screen (Kuwadro Bawat Segundo, Temperatura, Baterya)",
            "Panel Visual en Pantalla en Vivo (Cuadros por Segundo, Temperatura, Batería)",
            "Indicador Visual en Pantalla en Vivo (Fotogramas por Segundo, Temperatura, Batería)",
            "ライブ画面ヘッドアップ表示（毎秒フレーム数・温度・バッテリー）",
            "लाइव स्क्रीन सूचना प्रदर्शन (फ़्रेम प्रति सेकंड, तापमान, बैटरी)"
        ),
        "Screen Telemetry" to MultiLangEntry(
            "Telemetria na Tela",
            "Telemetri Layar",
            "Đo Lường Trên Màn Hình",
            "Telemetry sa Screen",
            "Telemetría en Pantalla",
            "Telemetría en Pantalla",
            "画面テレメトリ計測",
            "स्क्रीन टेलीमेट्री"
        ),
        "Overlay" to MultiLangEntry(
            "Sobreposição",
            "Lapisan Layar",
            "Lớp Phủ Màn Hình",
            "Overlay sa Screen",
            "Superposición",
            "Superposición",
            "画面オーバーレイ",
            "स्क्रीन ओवरले"
        ),
        "Choreographer FPS | Live Temps | Overlay" to MultiLangEntry(
            "Quadros por Segundo Sincronizados | Temperaturas ao Vivo | Sobreposição",
            "Bingkai per Detik Layar | Suhu Langsung | Lapisan Layar",
            "Khung Hình Mỗi Giây Đồng Bộ | Nhiệt Độ Trực Tiếp | Lớp Phủ",
            "Kuwadro Bawat Segundo | Live na Temperatura | Overlay",
            "Cuadros por Segundo Sincronizados | Temperaturas en Vivo | Superposición",
            "Fotogramas por Segundo Sincronizados | Temperaturas en Vivo | Superposición",
            "画面同期フレーム数 | リアルタイム温度 | オーバーレイ",
            "सिंक फ़्रेम प्रति सेकंड | लाइव तापमान | ओवरले"
        ),
        "Floating Pill | Live FPS | Hardware Sensors" to MultiLangEntry(
            "Cápsula Flutuante | Quadros por Segundo ao Vivo | Sensores de Hardware",
            "Kapsul Mengambang | Bingkai per Detik Langsung | Sensor Perangkat Keras",
            "Viên Nang Nổi | Khung Hình Mỗi Giây Trực Tiếp | Cảm Biến Phần Cứng",
            "Lumulutang na Pill | Live na Kuwadro Bawat Segundo | Sensor ng Hardware",
            "Píldora Flotante | Cuadros por Segundo en Vivo | Sensores de Hardware",
            "Cápsula Flotante | Fotogramas por Segundo en Vivo | Sensores de Hardware",
            "フローティングピル | リアルタイム毎秒フレーム数 | ハードウェアセンサー",
            "फ़्लोटिंग पिल | लाइव फ़्रेम प्रति सेकंड | हार्डवेयर सेंसर"
        ),
        "Renders a live hardware synchronized FPS meter, thermal sensor readouts, and battery gauge over all games." to MultiLangEntry(
            "Exibe um medidor de Quadros por Segundo sincronizado por hardware, leitura térmica e nível de bateria sobre todos os jogos.",
            "Menampilkan pengukur Bingkai per Detik yang disinkronkan perangkat keras, pembacaan sensor suhu, dan indikator baterai di atas semua game.",
            "Hiển thị bộ đếm Khung Hình Mỗi Giây đồng bộ phần cứng, thông số cảm biến nhiệt và mức pin trực tiếp trên mọi trò chơi.",
            "Nagpapakita ng live na sukatan ng Kuwadro Bawat Segundo, sensor ng temperatura, at antas ng baterya sa ibabaw ng lahat ng laro.",
            "Muestra un medidor en vivo de Cuadros por Segundo sincronizado por hardware, sensores térmicos y nivel de batería sobre todos los juegos.",
            "Muestra un medidor en vivo de Fotogramas por Segundo sincronizado por hardware, lectura térmica y nivel de batería sobre todos los juegos.",
            "ハードウェア同期された毎秒フレーム数メーター、温度センサー値、バッテリー残量をすべてのゲーム上にリアルタイム表示します。",
            "सभी गेम्स के ऊपर हार्डवेयर-सिंक फ़्रेम प्रति सेकंड मीटर, थर्मल सेंसर रीडिंग और बैटरी स्तर लाइव दिखाता है।"
        ),
        "Draws live FPS frame intervals, battery temperature, and battery percentage over all games." to MultiLangEntry(
            "Desenha intervalos de Quadros por Segundo ao vivo, temperatura da bateria e porcentagem de bateria sobre todos os jogos.",
            "Menampilkan interval Bingkai per Detik langsung, suhu baterai, dan persentase baterai di atas semua game.",
            "Hiển thị khoảng thời gian Khung Hình Mỗi Giây trực tiếp, nhiệt độ pin và phần trăm pin trên mọi trò chơi.",
            "Nagpapakita ng live na pagitan ng Kuwadro Bawat Segundo, temperatura ng baterya, at porsyento ng baterya sa lahat ng laro.",
            "Dibuja intervalos de Cuadros por Segundo en vivo, temperatura de batería y porcentaje de batería sobre todos los juegos.",
            "Muestra intervalos de Fotogramas por Segundo en vivo, temperatura de batería y porcentaje de batería sobre todos los juegos.",
            "リアルタイムの毎秒フレーム数間隔、バッテリー温度、バッテリー残量パーセントをすべてのゲーム上に表示します。",
            "सभी गेम्स के ऊपर लाइव फ़्रेम प्रति सेकंड अंतराल, बैटरी तापमान और बैटरी प्रतिशत दिखाता है।"
        ),
        "Do Not Disturb Gaming Focus Shield" to MultiLangEntry(
            "Escudo de Foco Não Perturbe para Jogos",
            "Perisai Fokus Bermain Jangan Ganggu",
            "Lá Chắn Tập Trung Chơi Game Không Làm Phiền",
            "Panangga sa Pokus ng Laro na Huwag Istorbohin",
            "Escudo de Enfoque Gamer No Molestar",
            "Escudo de Concentración No Molestar para Juegos",
            "おやすみモード・ゲーミング集中シールド",
            "डू नॉट डिस्टर्ब गेमिंग फ़ोकस शील्ड"
        ),
        "Focus" to MultiLangEntry(
            "Foco",
            "Fokus",
            "Tập Trung",
            "Pokus",
            "Enfoque",
            "Concentración",
            "集中モード",
            "फ़ोकस"
        ),
        "Priority Mute | No Popups | Quiet" to MultiLangEntry(
            "Silêncio Prioritário | Sem Pop-ups | Silencioso",
            "Bisu Prioritas | Tanpa Pop-up | Tenang",
            "Tắt Tiếng Ưu Tiên | Không Thông Báo Nổi | Yên Tĩnh",
            "Prayoridad na Mute | Walang Popup | Tahimik",
            "Silencio Prioritario | Sin Ventanas Emergentes | Silencioso",
            "Silencio Prioritario | Sin Avisos Emergentes | Silencioso",
            "優先ミュート | ポップアップ抑止 | 静音",
            "प्राथमिकता म्यूट | कोई पॉपअप नहीं | शांत"
        ),
        "Directly activates Do Not Disturb priority mode to suppress phone calls, banner alerts, and popups while playing." to MultiLangEntry(
            "Ativa diretamente o modo prioritário Não Perturbe para bloquear chamadas, alertas de banner e janelas flutuantes durante o jogo.",
            "Mengaktifkan langsung mode prioritas Jangan Ganggu untuk menahan panggilan telepon, spanduk peringatan, dan pop-up saat bermain.",
            "Trực tiếp bật chế độ ưu tiên Không Làm Phiền để chặn cuộc gọi, biểu ngữ cảnh báo và thông báo nổi khi đang chơi.",
            "Direktang binubuksan ang priority mode na Huwag Istorbohin upang pigilan ang mga tawag, banner alert, at popup habang naglalaro.",
            "Activa directamente el modo prioritario No Molestar para suprimir llamadas, alertas de banner y ventanas emergentes mientras juegas.",
            "Activa directamente el modo prioritario No Molestar para bloquear llamadas, alertas en pantalla y avisos emergentes mientras juegas.",
            "おやすみモードの優先フィルタを直接有効化し、プレイ中の着信・バナー通知・ポップアップをブロックします。",
            "खेलते समय फ़ोन कॉल, बैनर अलर्ट और पॉपअप को रोकने के लिए सीधे डू नॉट डिस्टर्ब प्राथमिकता मोड सक्रिय करता है।"
        ),
        "Live Thread Scheduler Priority" to MultiLangEntry(
            "Prioridade de Agendamento de Threads ao Vivo",
            "Prioritas Penjadwal Thread Langsung",
            "Ưu Tiên Bộ Lập Lịch Luồng Trực Tiếp",
            "Prayoridad ng Live Thread Scheduler",
            "Prioridad en Vivo del Planificador de Hilos",
            "Prioridad en Vivo del Planificador de Hilos",
            "ライブスレッドスケジューラ優先度強化",
            "लाइव थ्रेड शेड्यूलर प्राथमिकता"
        ),
        "CPU Scheduling" to MultiLangEntry(
            "Agendamento do Processador Central",
            "Penjadwalan Unit Pemrosesan Pusat",
            "Lập Lịch Bộ Vi Xử Lý Trung Tâm",
            "Pag-iskedyul ng Sentral na Prosesor",
            "Planificación del Procesador Central",
            "Planificación de Unidad Central de Procesamiento",
            "中央演算処理装置スケジューリング",
            "केंद्रीय प्रोसेसर शेड्यूलिंग"
        ),
        "Thread Priority | Urgent Display | Fast Render" to MultiLangEntry(
            "Prioridade de Thread | Exibição Urgente | Renderização Rápida",
            "Prioritas Thread | Layar Mendesak | Render Cepat",
            "Ưu Tiên Luồng | Hiển Thị Khẩn Cấp | Kết Xuất Nhanh",
            "Prayoridad ng Thread | Mabilis na Display | Mabilis na Render",
            "Prioridad de Hilo | Pantalla Urgente | Renderizado Rápido",
            "Prioridad de Hilo | Pantalla Urgente | Renderizado Rápido",
            "スレッド優先度 | 緊急描画優先 | 高速レンダリング",
            "थ्रेड प्राथमिकता | अर्जेंट डिस्प्ले | तेज़ रेंडर"
        ),
        "Elevates process and rendering thread priority directly in the Linux scheduler to THREAD_PRIORITY_URGENT_DISPLAY." to MultiLangEntry(
            "Eleva diretamente a prioridade do processo e da thread de renderização no agendador Linux para exibição urgente.",
            "Meningkatkan prioritas proses dan thread rendering langsung di penjadwal Linux ke prioritas tampilan mendesak.",
            "Nâng mức ưu tiên của tiến trình và luồng kết xuất đồ họa trực tiếp trong bộ lập lịch Linux lên mức hiển thị khẩn cấp.",
            "Direktang itinataas ang prayoridad ng proseso at rendering thread sa Linux scheduler para sa mabilis na pagpapakita sa screen.",
            "Eleva directamente la prioridad del proceso y del hilo de renderizado en el planificador de Linux a prioridad urgente de pantalla.",
            "Eleva directamente la prioridad del proceso y del hilo de renderizado en el planificador de Linux a prioridad urgente de pantalla.",
            "Linuxスケジューラ内のプロセスおよび描画スレッドの優先度を緊急ディスプレイ描画レベルへ直接引き上げます。",
            "लिनक्स शेड्यूलर में प्रोसेस और रेंडरिंग थ्रेड की प्राथमिकता को सीधे अर्जेंट डिस्प्ले स्तर तक बढ़ाता है।"
        ),
        "Tactical Screen Crosshair (8 Reticles & Neon Colors)" to MultiLangEntry(
            "Mira Tática na Tela (8 Retículas e Cores Neon)",
            "Bidik Taktis di Layar (8 Retikel & Warna Neon)",
            "Tâm Ngắm Chiến Thuật Trên Màn Hình (8 Kiểu Tâm & Màu Neon)",
            "Taktikal na Crosshair sa Screen (8 Reticle at Kulay Neon)",
            "Mira Táctica en Pantalla (8 Retículas y Colores Neón)",
            "Mira Táctica en Pantalla (8 Retículas y Colores Neón)",
            "タクティカル画面クロスヘア（8種レティクル＆ネオンカラー）",
            "टैक्टिकल स्क्रीन क्रॉसहेयर (8 रेटिकल और नियॉन रंग)"
        ),
        "8 Reticle Designs | Neon Colors | Touch Safe" to MultiLangEntry(
            "8 Designs de Retícula | Cores Neon | Toque Seguro",
            "8 Desain Retikel | Warna Neon | Aman Sentuhan",
            "8 Thiết Kế Tâm Ngắm | Màu Neon | Xuyên Cảm Ứng",
            "8 Disenyo ng Reticle | Kulay Neon | Ligtas sa Touch",
            "8 Diseños de Retícula | Colores Neón | Paso Táctil Seguro",
            "8 Diseños de Retícula | Colores Neón | Paso Táctil Seguro",
            "8種類のレティクル | ネオンカラー | タッチ透過安全設計",
            "8 रेटिकल डिज़ाइन | नियॉन रंग | टच सुरक्षित"
        ),
        "ACTIVE • True Full-Screen Pixel Center" to MultiLangEntry(
            "ATIVO • Centro Físico em Tela Cheia",
            "AKTIF • Titik Tengah Piksel Layar Penuh",
            "ĐANG BẬT • Tâm Điểm Ảnh Toàn Màn Hình",
            "AKTIBO • Tunay na Gitnang Pixel ng Buong Screen",
            "ACTIVO • Centro Físico de Pantalla Completa",
            "ACTIVO • Centro Físico de Pantalla Completa",
            "有効 • フルスクリーン物理ピクセル中央",
            "सक्रिय • पूर्ण-स्क्रीन वास्तविक पिक्सेल केंद्र"
        ),
        "Center Calibration & 1px Nudge" to MultiLangEntry(
            "Calibração Central e Ajuste de 1px",
            "Kalibrasi Tengah & Geser Halus 1px",
            "Hiệu Chuẩn Tâm & Dịch Chuyển 1px",
            "Sentral na Kalibrasyon at 1px Nudge",
            "Calibración Central y Ajuste de 1px",
            "Calibración Central y Ajuste de 1px",
            "中央キャリブレーション＆1px微調整",
            "केंद्र अंशांकन और 1px सूक्ष्म समायोजन"
        ),
        "Reset Center (0, 0)" to MultiLangEntry(
            "Redefinir Centro (0, 0)",
            "Reset ke Tengah (0, 0)",
            "Đặt Lại Tâm (0, 0)",
            "I-reset sa Gitna (0, 0)",
            "Restablecer Centro (0, 0)",
            "Restablecer Centro (0, 0)",
            "中央にリセット (0, 0)",
            "केंद्र रीसेट करें (0, 0)"
        ),
        "Draws a centered precision crosshair reticle over games. Includes 8 distinct designs and customizable colors. (Notice: Check online fair play rules before use)." to MultiLangEntry(
            "Desenha uma mira de precisão centralizada sobre os jogos. Inclui 8 estilos diferentes e cores personalizáveis. (Aviso: Verifique as regras de jogo limpo antes de usar).",
            "Menampilkan retikel bidik presisi di tengah layar di atas game. Mencakup 8 desain berbeda dan warna yang dapat disesuaikan. (Catatan: Periksa aturan fair play game online sebelum digunakan).",
            "Hiển thị tâm ngắm chính xác ở giữa màn hình trên các trò chơi. Bao gồm 8 kiểu dáng và màu sắc tùy chỉnh. (Lưu ý: Kiểm tra quy định chơi công bằng trước khi dùng).",
            "Naglalagay ng sentrong crosshair sa ibabaw ng mga laro. May 8 disenyo at napipiling kulay. (Paalala: Suriin ang fair play rules ng online games bago gamitin).",
            "Dibuja una retícula de precisión centrada sobre los juegos. Incluye 8 diseños distintos y colores personalizables. (Aviso: Revisa las reglas de juego limpio antes de usar).",
            "Dibuja una retícula de precisión centrada sobre los juegos. Incluye 8 diseños distintos y colores personalizables. (Aviso: Revisa las normas de juego limpio antes de usar).",
            "ゲーム画面の中央に高精度な照準レティクルを描画します。8種類のデザインとカスタムカラーに対応。（注意：オンラインゲームのフェアプレイ規約を確認してご使用ください）。",
            "गेम्स के ऊपर बीच में सटीक क्रॉसहेयर रेटिकल दिखाता है। इसमें 8 अलग डिज़ाइन और कस्टम रंग शामिल हैं। (सूचना: उपयोग से पहले ऑनलाइन फेयर प्ले नियम जांचें)।"
        ),
        "Zero Animation Latency (0.0x Scales)" to MultiLangEntry(
            "Zero Latência de Animação (Escalas 0.0x)",
            "Nol Latensi Animasi (Skala 0.0x)",
            "Không Độ Trễ Hiệu Ứng Chuyển Động (Tỷ Lệ 0.0x)",
            "Zero Latency sa Animation (0.0x na Sukat)",
            "Cero Latencia de Animación (Escalas 0.0x)",
            "Cero Latencia de Animación (Escalas 0.0x)",
            "アニメーション遅延ゼロ（0.0倍スケール）",
            "शून्य एनिमेशन लेटेंसी (0.0x स्केल)"
        ),
        "System Latency" to MultiLangEntry(
            "Latência do Sistema",
            "Latensi Sistem",
            "Độ Trễ Hệ Thống",
            "Latency ng Sistema",
            "Latencia del Sistema",
            "Latencia del Sistema",
            "システム応答遅延",
            "सिस्टम लेटेंसी"
        ),
        "Secure Settings | 0.0x Instant" to MultiLangEntry(
            "Configurações Seguras | 0.0x Instantâneo",
            "Pengaturan Aman | 0.0x Instan",
            "Cài Đặt Bảo Mật | 0.0x Tức Thì",
            "Secure Settings | 0.0x Agad",
            "Ajustes Seguros | 0.0x Instantáneo",
            "Configuración Segura | 0.0x Instantáneo",
            "セキュア設定 | 0.0倍即時適用",
            "सुरक्षित सेटिंग्स | 0.0x तुरंत"
        ),
        "Requires Developer Options" to MultiLangEntry(
            "Requer Opções do Desenvolvedor",
            "Memerlukan Opsi Pengembang",
            "Yêu Cầu Tùy Chọn Nhà Phát Triển",
            "Kailangan ng Developer Options",
            "Requiere Opciones de Desarrollador",
            "Requiere Opciones de Desarrollador",
            "開発者向けオプションが必要です",
            "डेवलपर विकल्प आवश्यक है"
        ),
        "Instantly sets window, transition, and animator scales to 0.0x for zero animation delays across the entire system." to MultiLangEntry(
            "Define instantaneamente as escalas de janela, transição e animador para 0.0x para eliminar atrasos de animação em todo o sistema.",
            "Mengatur skala jendela, transisi, dan animator secara instan ke 0.0x untuk menghilangkan jeda animasi di seluruh sistem.",
            "Đặt ngay tỷ lệ hiệu ứng cửa sổ, chuyển cảnh và hoạt ảnh về 0.0x để loại bỏ hoàn toàn độ trễ chuyển động trên toàn hệ thống.",
            "Agad na itinatakda ang window, transition, at animator scales sa 0.0x para sa zero na pagkaantala ng animation sa buong sistema.",
            "Ajusta al instante las escalas de ventana, transición y animador a 0.0x para eliminar por completo los retrasos de animación en el sistema.",
            "Ajusta al instante las escalas de ventana, transición y animador a 0.0x para eliminar por completo las demoras de animación en el sistema.",
            "ウィンドウ・トランジション・Animatorのスケールを即座に0.0倍に設定し、システム全体のアニメーション遅延をゼロにします。",
            "पूरे सिस्टम में एनिमेशन देरी को खत्म करने के लिए विंडो, ट्रांज़िशन और एनिमेटर स्केल को तुरंत 0.0x पर सेट करता है।"
        ),
        "Cloudflare 1.1.1.1 Gaming Private DNS" to MultiLangEntry(
            "Sistema de Nomes de Domínio Privado Cloudflare 1.1.1.1 para Jogos",
            "Sistem Nama Domain Pribadi Gaming Cloudflare 1.1.1.1",
            "Hệ Thống Phân Giải Tên Miền Riêng Tư Chơi Game Cloudflare 1.1.1.1",
            "Pribadong Sistema ng Pangalan ng Domain sa Laro na Cloudflare 1.1.1.1",
            "Sistema de Nombres de Dominio Privado Cloudflare 1.1.1.1 para Juegos",
            "Sistema de Nombres de Dominio Privado Cloudflare 1.1.1.1 para Juegos",
            "Cloudflare 1.1.1.1 ゲーミング・プライベート・ドメインネームシステム",
            "Cloudflare 1.1.1.1 गेमिंग प्राइवेट डोमेन नाम प्रणाली"
        ),
        "Secure Settings | 1.1.1.1 Active" to MultiLangEntry(
            "Configurações Seguras | 1.1.1.1 Ativo",
            "Pengaturan Aman | 1.1.1.1 Aktif",
            "Cài Đặt Bảo Mật | 1.1.1.1 Đang Bật",
            "Secure Settings | 1.1.1.1 Aktibo",
            "Ajustes Seguros | 1.1.1.1 Activo",
            "Configuración Segura | 1.1.1.1 Activo",
            "セキュア設定 | 1.1.1.1 有効",
            "सुरक्षित सेटिंग्स | 1.1.1.1 सक्रिय"
        ),
        "Applies encrypted 1.1.1.1 Cloudflare DNS directly to eliminate ISP routing delays on game domain queries." to MultiLangEntry(
            "Aplica diretamente o Sistema de Nomes de Domínio criptografado 1.1.1.1 da Cloudflare para eliminar atrasos de roteamento da operadora em servidores de jogos.",
            "Menerapkan Sistem Nama Domain terenkripsi 1.1.1.1 Cloudflare secara langsung untuk menghilangkan penundaan perutean operator pada kueri server game.",
            "Áp dụng trực tiếp Hệ Thống Phân Giải Tên Miền mã hóa 1.1.1.1 của Cloudflare để loại bỏ độ trễ định tuyến nhà mạng khi kết nối máy chủ trò chơi.",
            "Direktang inilalapat ang naka-encrypt na 1.1.1.1 Cloudflare Sistema ng Pangalan ng Domain upang alisin ang pagkaantala ng ISP sa pag-connect sa laro.",
            "Aplica directamente el Sistema de Nombres de Dominio cifrado 1.1.1.1 de Cloudflare para eliminar retrasos de enrutamiento del proveedor de internet en servidores de juegos.",
            "Aplica directamente el Sistema de Nombres de Dominio cifrado 1.1.1.1 de Cloudflare para eliminar demoras de enrutamiento del proveedor de internet en servidores de juegos.",
            "暗号化されたCloudflare 1.1.1.1ドメインネームシステムを直接適用し、ゲームサーバー名前解決時のプロバイダ経路遅延を解消します。",
            "गेम सर्वर डोमेन क्वेरी पर इंटरनेट प्रदाता की देरी को खत्म करने के लिए सीधे एन्क्रिप्टेड 1.1.1.1 Cloudflare डोमेन नाम प्रणाली लागू करता है।"
        ),

        // AppBackgroundTheme & CuratedBackgroundPresets (SettingsScreen)
        "Automatic (System Default)" to MultiLangEntry(
            "Automático (Padrão do Sistema)",
            "Otomatis (Bawaan Sistem)",
            "Tự Động (Mặc Định Hệ Thống)",
            "Awtomatiko (Default ng Sistema)",
            "Automático (Predeterminado del Sistema)",
            "Automático (Predeterminado del Sistema)",
            "自動（システム標準）",
            "स्वचालित (सिस्टम डिफ़ॉल्ट)"
        ),
        "Follows your device's Light or Dark display mode automatically" to MultiLangEntry(
            "Segue automaticamente o modo Claro ou Escuro do seu dispositivo",
            "Mengikuti mode layar Terang atau Gelap perangkat Anda secara otomatis",
            "Tự động theo chế độ Sáng hoặc Tối của thiết bị",
            "Awtomatikong sumusunod sa Maliwanag o Madilim na mode ng iyong device",
            "Sigue automáticamente el modo Claro u Oscuro de tu dispositivo",
            "Sigue automáticamente el modo Claro u Oscuro de tu dispositivo",
            "端末のライトモードまたはダークモードに自動で追従します",
            "आपके डिवाइस के लाइट या डार्क डिस्प्ले मोड का स्वतः पालन करता है"
        ),
        "Light Hub Theme" to MultiLangEntry(
            "Tema Claro do Hub",
            "Tema Hub Terang",
            "Giao Diện Hub Sáng",
            "Maliwanag na Tema ng Hub",
            "Tema Claro del Hub",
            "Tema Claro del Hub",
            "ライトHubテーマ",
            "लाइट हब थीम"
        ),
        "Clean alabaster surfaces with high-contrast text and crisp blue accents" to MultiLangEntry(
            "Superfícies claras com texto de alto contraste e detalhes em azul nítido",
            "Permukaan putih bersih dengan teks kontras tinggi dan aksen biru tajam",
            "Nền sáng rõ nét với chữ tương phản cao và điểm nhấn xanh lam",
            "Malinis na puting surface na may malinaw na teksto at asul na accent",
            "Superficies claras con texto de alto contraste y acentos azules nítidos",
            "Superficies claras con texto de alto contraste y detalles azules definidos",
            "高コントラストな文字と鮮やかなブルーアクセントを備えたクリーンなライト外観",
            "उच्च कंट्रास्ट टेक्स्ट और साफ़ नीले एक्सेंट के साथ क्लीन लाइट सरफ़ेस"
        ),
        "Dark Hub Theme" to MultiLangEntry(
            "Tema Escuro do Hub",
            "Tema Hub Gelap",
            "Giao Diện Hub Tối",
            "Madilim na Tema ng Hub",
            "Tema Oscuro del Hub",
            "Tema Oscuro del Hub",
            "ダークHubテーマ",
            "डार्क हब थीम"
        ),
        "Deep obsidian dark surfaces designed for low-glare night gaming" to MultiLangEntry(
            "Superfícies escuras profundas projetadas para jogar à noite sem reflexos",
            "Permukaan gelap obsidian yang dirancang untuk bermain malam tanpa silau",
            "Bề mặt tối sâu giảm chói mắt khi chơi game ban đêm",
            "Malalim na madilim na surface na idinisenyo para sa paglalaro sa gabi",
            "Superficies oscuras profundas diseñadas para jugar de noche sin deslumbramiento",
            "Superficies oscuras profundas diseñadas para jugar de noche sin fatiga visual",
            "夜間のゲームプレイで目の負担を抑えるディープオブシディアン・ダーク外観",
            "रात में बिना चमक के गेमिंग के लिए डिज़ाइन की गई गहरी डार्क सरफ़ेस"
        ),
        "Custom Background Color" to MultiLangEntry(
            "Cor de Fundo Personalizada",
            "Warna Latar Belakang Kustom",
            "Màu Nền Tùy Chỉnh",
            "Custom na Kulay ng Background",
            "Color de Fondo Personalizado",
            "Color de Fondo Personalizado",
            "カスタム背景カラー",
            "कस्टम बैकग्राउंड रंग"
        ),
        "Pick any custom RGB color or curated preset for your Hub background" to MultiLangEntry(
            "Escolha qualquer cor Vermelha Verde e Azul personalizada ou predefinição para o fundo do Hub",
            "Pilih warna Cahaya Merah Hijau Biru kustom atau preset pilihan untuk latar belakang Hub Anda",
            "Chọn bất kỳ màu Ánh Sáng Đỏ Lục Lam tùy chỉnh hoặc mẫu có sẵn cho nền Hub của bạn",
            "Pumili ng anumang custom na kulay o preset para sa background ng iyong Hub",
            "Elige cualquier color personalizado o ajuste predefinido para el fondo de tu Hub",
            "Elige cualquier color personalizado o ajuste predefinido para el fondo de tu Hub",
            "Hubの背景にお好みのフルカラー色または厳選プリセットを設定します",
            "अपने हब बैकग्राउंड के लिए कोई भी कस्टम रंग या प्रीसेट चुनें"
        ),
        "Obsidian OLED" to MultiLangEntry("Obsidiana Escura", "Hitam Obsidian", "Đen Tuyền", "Obsidian na Itim", "Obsidiana Oscura", "Obsidiana Oscura", "オブシディアン・ブラック", "ओब्सीडियन ब्लैक"),
        "Deep Cyber Navy" to MultiLangEntry("Azul Marinho Cyber", "Biru Dongker Siber", "Xanh Hải Quân Sâu", "Malalim na Cyber Navy", "Azul Marino Cyber", "Azul Marino Cyber", "ディープサイバーネイビー", "डीप साइबर नेवी"),
        "Carbon Slate" to MultiLangEntry("Ardósia Carbono", "Abu-abu Karbon", "Xám Đá Carbon", "Carbon Slate", "Pizarra Carbón", "Pizarra Carbón", "カーボンスレート", "कार्बन स्लेट"),
        "Midnight Violet" to MultiLangEntry("Violeta da Meia-Noite", "Ungu Tengah Malam", "Tím Đêm Khuya", "Midnight Violet", "Violeta Medianoche", "Violeta Medianoche", "ミッドナイトバイオレット", "मिडनाइट वायलेट"),
        "Emerald Matrix" to MultiLangEntry("Matriz Esmeralda", "Hijau Zamrud", "Xanh Ngọc Lục Bảo", "Emerald Matrix", "Matriz Esmeralda", "Matriz Esmeralda", "エメラルドマトリックス", "एमराल्ड मैट्रिक्स"),
        "Crimson Pulse" to MultiLangEntry("Pulso Carmesim", "Merah Krimson", "Đỏ Thẫm", "Crimson Pulse", "Pulso Carmesí", "Pulso Carmesí", "クリムゾンパルス", "क्रिमसन पल्स"),
        "Arctic Frost" to MultiLangEntry("Gelo Ártico", "Putih Es Arktik", "Trắng Băng Bắc Cực", "Arctic Frost", "Escarcha Ártica", "Escarcha Ártica", "アークティックフロスト", "आर्कटिक फ़्रॉस्ट"),
        "Cloud Titanium" to MultiLangEntry("Titânio Nuvem", "Abu-abu Titanium", "Xám Titan Sáng", "Cloud Titanium", "Titanio Nube", "Titanio Nube", "クラウドチタニウム", "क्लाउड टाइटेनियम"),

        // AudioSourceMode (ScreenRecordScreen)
        "Internal Game Audio" to MultiLangEntry(
            "Áudio Interno do Jogo",
            "Audio Internal Game",
            "Âm Thanh Nội Bộ Trò Chơi",
            "Panloob na Audio ng Laro",
            "Audio Interno del Juego",
            "Audio Interno del Juego",
            "ゲーム内部音声のみ",
            "इंटरनल गेम ऑडियो"
        ),
        "Records pure internal game sound (Android 10+ AudioPlaybackCapture)" to MultiLangEntry(
            "Grava o som interno puro do jogo sem ruídos externos",
            "Merekam suara internal game murni tanpa suara luar",
            "Ghi lại âm thanh gốc bên trong trò chơi không lẫn tạp âm",
            "Nire-record ang purong tunog sa loob ng laro",
            "Graba el sonido interno puro del juego sin ruido ambiental",
            "Graba el sonido interno puro del juego sin ruido externo",
            "ゲームの内部音声のみをクリアに録音します（Android 10以降対応）",
            "बिना बाहरी शोर के शुद्ध इंटरनल गेम साउंड रिकॉर्ड करता है"
        ),
        "Game Audio + Mic" to MultiLangEntry(
            "Áudio do Jogo + Microfone",
            "Audio Game + Mikrofon",
            "Âm Thanh Trò Chơi + Micrô",
            "Audio ng Laro + Mikropono",
            "Audio del Juego + Micrófono",
            "Audio del Juego + Micrófono",
            "ゲーム音声＋マイク音声",
            "गेम ऑडियो + माइक्रोफ़ोन"
        ),
        "Mixes internal game sound with your live voice commentary" to MultiLangEntry(
            "Combina o som interno do jogo com seus comentários de voz ao vivo",
            "Menggabungkan suara internal game dengan komentar suara langsung Anda",
            "Kết hợp âm thanh trong trò chơi với giọng bình luận trực tiếp của bạn",
            "Pinaghahalo ang tunog ng laro at ang iyong boses sa mikropono",
            "Mezcla el sonido interno del juego con tu voz en vivo",
            "Mezcla el sonido interno del juego con tus comentarios de voz en vivo",
            "ゲームの内部音声とマイクの実況ボイスを同時にミックス録音します",
            "इंटरनल गेम साउंड को आपकी लाइव आवाज़ के साथ मिलाकर रिकॉर्ड करता है"
        ),
        "Microphone Only" to MultiLangEntry(
            "Apenas Microfone",
            "Hanya Mikrofon",
            "Chỉ Micrô",
            "Mikropono Lamang",
            "Solo Micrófono",
            "Solo Micrófono",
            "マイク音声のみ",
            "केवल माइक्रोफ़ोन"
        ),
        "Records voice & room audio through the device microphone" to MultiLangEntry(
            "Grava voz e áudio ambiente pelo microfone do aparelho",
            "Merekam suara & audio ruangan melalui mikrofon perangkat",
            "Ghi âm giọng nói qua micrô của thiết bị",
            "Nire-record ang boses sa pamamagitan ng mikropono ng device",
            "Graba voz y audio ambiental mediante el micrófono del dispositivo",
            "Graba voz y sonido ambiente mediante el micrófono del dispositivo",
            "端末のマイクを通じて声と周囲の音声を録音します",
            "डिवाइस माइक्रोफ़ोन के माध्यम से आवाज़ और ऑडियो रिकॉर्ड करता है"
        ),
        "Mute (Video Only)" to MultiLangEntry(
            "Mudo (Apenas Vídeo)",
            "Bisu (Hanya Video)",
            "Tắt Tiếng (Chỉ Video)",
            "Mute (Video Lamang)",
            "Silencio (Solo Video)",
            "Silencio (Solo Video)",
            "ミュート（映像のみ）",
            "म्यूट (केवल वीडियो)"
        ),
        "Records silent gameplay video with no audio track" to MultiLangEntry(
            "Grava vídeo silencioso da partida sem faixa de áudio",
            "Merekam video gameplay tanpa suara",
            "Ghi hình video trò chơi không có âm thanh",
            "Nagre-record ng tahimik na video ng laro nang walang audio",
            "Graba video silencioso de la partida sin pista de audio",
            "Graba video silencioso de la partida sin pista de audio",
            "音声トラックなしでゲームプレイ映像のみを録画します",
            "बिना किसी ऑडियो ट्रैक के साइलेंट गेमप्ले वीडियो रिकॉर्ड करता है"
        ),

        // Crosshair Styles & Colors
        "Classic Cross" to MultiLangEntry("Cruz Clássica", "Salib Klasik", "Chữ Thập Cổ Điển", "Klasikong Krus", "Cruz Clásica", "Cruz Clásica", "クラシッククロス", "क्लासिक क्रॉस"),
        "Precision Dot" to MultiLangEntry("Ponto de Precisão", "Titik Presisi", "Chấm Chính Xác", "Tumpak na Tuldok", "Punto de Precisión", "Punto de Precisión", "プレシジョンドット", "सटीक बिंदु"),
        "Circle + Dot" to MultiLangEntry("Círculo + Ponto", "Lingkaran + Titik", "Vòng Tròn + Chấm", "Bilog + Tuldok", "Círculo + Punto", "Círculo + Punto", "サークル＋ドット", "सर्कल + बिंदु"),
        "Tactical Chevron" to MultiLangEntry("Chevron Tático", "Chevron Taktis", "Mũi Tên Chiến Thuật", "Taktikal na Chevron", "Chevrón Táctico", "Chevrón Táctico", "タクティカルシェブロン", "टैक्टिकल शेवरॉन"),
        "T-Pose" to MultiLangEntry("Cruz em T", "Bentuk T", "Chữ T Chiến Thuật", "Hugis T", "Cruz en T", "Cruz en T", "T字レティクル", "टी-शेप रेटिकल"),
        "Sniper Mil-Dot" to MultiLangEntry("Sniper Mil-Dot", "Sniper Mil-Dot", "Tâm Bắn Tỉa Mil-Dot", "Sniper Mil-Dot", "Francotirador Mil-Dot", "Francotirador Mil-Dot", "スナイパー・ミルドット", "स्नाइपर मिल-डॉट"),
        "Diamond Box" to MultiLangEntry("Diamante Tático", "Kotak Berlian", "Hình Thoi Chiến Thuật", "Hugis Diyamante", "Rombo Táctico", "Rombo Táctico", "ダイヤモンドボックス", "डायमंड बॉक्स"),
        "Circle + Cross" to MultiLangEntry("Círculo + Cruz", "Lingkaran + Salib", "Vòng Tròn + Chữ Thập", "Bilog + Krus", "Círculo + Cruz", "Círculo + Cruz", "サークル＋クロス", "सर्कल + क्रॉस"),
        "Cyan" to MultiLangEntry("Ciano", "Sian", "Xanh Lơ", "Cyan", "Cian", "Cian", "シアン", "सियान"),
        "Green" to MultiLangEntry("Verde", "Hijau", "Xanh Lá", "Berde", "Verde", "Verde", "グリーン", "हरा"),
        "Crimson" to MultiLangEntry("Carmesim", "Merah Tua", "Đỏ Thẫm", "Pulang Crimson", "Carmesí", "Carmesí", "クリムゾン", "गहरा लाल"),
        "Gold" to MultiLangEntry("Dourado", "Emas", "Vàng Kim", "Ginto", "Dorado", "Dorado", "ゴールド", "सुनहरा"),
        "White" to MultiLangEntry("Branco", "Putih", "Trắng", "Puti", "Blanco", "Blanco", "ホワイト", "सफ़ेद"),
        "Purple" to MultiLangEntry("Roxo", "Ungu", "Tím", "Lila", "Morado", "Violeta", "パープル", "बैंगनी"),
        "Hot Pink" to MultiLangEntry("Rosa Neon", "Merah Muda Terang", "Hồng Neon", "Rosas", "Rosa Neón", "Rosa Intenso", "ホットピンク", "गहरा गुलाबी"),
        "Orange" to MultiLangEntry("Laranja", "Oranye", "Cam", "Kahel", "Naranja", "Naranja", "オレンジ", "नारंगी"),

        // AppLanguage Subtitles
        "Follows your device language automatically" to MultiLangEntry(
            "Segue automaticamente o idioma do seu dispositivo",
            "Mengikuti bahasa perangkat Anda secara otomatis",
            "Tự động theo ngôn ngữ thiết bị của bạn",
            "Awtomatikong sumusunod sa wika ng iyong device",
            "Sigue automáticamente el idioma de tu dispositivo",
            "Sigue automáticamente el idioma de tu dispositivo",
            "端末のシステム言語に自動で追従します",
            "आपके डिवाइस की भाषा का स्वतः पालन करता है"
        ),
        "English (US / Global)" to MultiLangEntry(
            "Inglês (EUA / Global)",
            "Bahasa Inggris (AS / Global)",
            "Tiếng Anh (Mỹ / Quốc tế)",
            "Ingles (US / Global)",
            "Inglés (EE. UU. / Global)",
            "Inglés (EE. UU. / Global)",
            "英語（米国 / グローバル）",
            "अंग्रेज़ी (यूएस / ग्लोबल)"
        ),
        "Rivals" to MultiLangEntry(
            "Rivais",
            "Rival",
            "Đối Thủ",
            "Karibal",
            "Rivales",
            "Rivales",
            "ライバル",
            "राइवल्स"
        ),
        "Create a Squad" to MultiLangEntry(
            "Criar um Esquadrão",
            "Buat Skuad",
            "Tạo Đội",
            "Gumawa ng Squad",
            "Crear un Escuadrón",
            "Crear un Escuadrón",
            "スクワッドを作成",
            "स्क्वाड बनाएं"
        ),
        "Join with Squad Code" to MultiLangEntry(
            "Entrar com Código do Esquadrão",
            "Gabung dengan Kode Skuad",
            "Tham Gia Bằng Mã Đội",
            "Sumali Gamit ang Squad Code",
            "Unirse con Código de Escuadrón",
            "Unirse con Código de Escuadrón",
            "スクワッドコードで参加",
            "स्क्वाड कोड से जुड़ें"
        ),
        "Squad Leaderboard (5 Ranks)" to MultiLangEntry(
            "Placar do Esquadrão (5 Posições)",
            "Papan Peringkat Skuad (5 Peringkat)",
            "Bảng Xếp Hạng Đội (5 Hạng)",
            "Leaderboard ng Squad (5 Ranggo)",
            "Tabla del Escuadrón (5 Rangos)",
            "Tabla del Escuadrón (5 Rangos)",
            "スクワッドランキング（全5位）",
            "स्क्वाड लीडरबोर्ड (5 रैंक)"
        ),
        "Invite a Squad & Compete in Rivals!" to MultiLangEntry(
            "Convide um Esquadrão e Compita no Rivais!",
            "Undang Skuad & Bersaing di Rival!",
            "Mời Đội & Đua Top Đối Thủ!",
            "Mag-imbita ng Squad at Makipaglaban sa Rivals!",
            "¡Invita a un Escuadrón y Compite en Rivales!",
            "¡Invita a un Escuadrón y Compite en Rivales!",
            "スクワッドを招待してライバルで競おう！",
            "स्क्वाड को आमंत्रित करें और राइवल्स में मुकाबला करें!"
        )
    )

    private val multiLangPhraseReplacements = listOf(
        // Referral & Squad Pass Phrases
        PhraseRule("Pro Guest Passes left to gift your squad", "Passes de Convidado Pro restantes para presentear seu esquadrão", "Pass Tamu Pro tersisa untuk dihadiahkan ke skuad Anda", "Thẻ Khách Pro còn lại để tặng đội của bạn", "Pro Guest Passes na natitira para sa squad mo", "枚のProゲストパスをスクワッドに贈れます", "प्रो गेस्ट पास आपके स्क्वाड को गिफ्ट करने के लिए बचे हैं", "Pases de Invitado Pro restantes para regalar a tu escuadrón"),
        PhraseRule("Pro Guest Passes left to gift the squad", "Passes de Convidado Pro restantes para o esquadrão", "Pass Tamu Pro tersisa untuk skuad", "Thẻ Khách Pro còn lại cho đội", "Pro Guest Passes na natitira para sa squad", "枚のProゲストパスをスクワッドに贈れます", "प्रो गेस्ट पास स्क्वाड के लिए बचे हैं", "Pases de Invitado Pro restantes para el escuadrón"),
        PhraseRule("Every claimed pass unlocks a permanent HUD module for you", "Cada passe resgatado desbloqueia um módulo HUD permanente para você", "Setiap pass yang diklaim membuka modul HUD permanen untuk Anda", "Mỗi thẻ được nhận mở khóa mô-đun HUD vĩnh viễn cho bạn", "Bawat pass na na-claim ay nagbubukas ng permanenteng HUD module para sa iyo", "パスが引き換えられるたびに永久HUDモジュールが解放されます", "प्रत्येक क्लेम किया गया पास आपके लिए स्थायी HUD मॉड्यूल अनलॉक करता है", "Cada pase canjeado desbloquea un módulo HUD permanente para ti"),
        PhraseRule("Google Sign-In Required", "Login com Google Necessário", "Wajib Login Google", "Yêu Cầu Đăng Nhập Google", "Kailangan ng Google Sign-In", "Googleサインインが必要です", "Google साइन-इन आवश्यक है", "Inicio de Sesión con Google Requerido"),
        PhraseRule("Referral URLs are disabled", "Links de indicação estão desativados", "URL referal dinonaktifkan", "Liên kết giới thiệu đã tắt", "Naka-disable ang mga referral URL", "紹介URLは無効化されています", "रेफ़रल URL बंद हैं", "Los enlaces URL de referido están desactivados"),
        PhraseRule("Anti-Cheat Block", "Bloqueio Anti-Cheat", "Blokir Anti-Cheat", "Chặn Gian Lận", "Anti-Cheat Block", "不正防止ブロック", "एंटी-चीट ब्लॉक", "Bloqueo Antitrampas"),
        PhraseRule("Verified Squadmates", "Membros Verificados", "Anggota Skuad Terverifikasi", "Đồng Đội Đã Xác Minh", "Verified na Ka-Squad", "認証済みスクワッドメンバー", "सत्यापित स्क्वाडमेट्स", "Compañeros Verificados"),
        PhraseRule("Focus Gameplay", "Jogo em Foco", "Gameplay Fokus", "Chơi Tập Trung", "Focus Gameplay", "フォーカスプレイ時間", "फ़ोकस गेमप्ले", "Juego en Enfoque"),
        PhraseRule("HUD Match", "Partida HUD", "Laga HUD", "Trận HUD", "HUD Match", "HUDマッチ", "HUD मैच", "Partida HUD"),
        PhraseRule("GUEST PASSES LEFT", "PASSES RESTANTES", "PASS TERSISA", "THẺ CÒN LẠI", "NATITIRANG PASS", "残りゲストパス", "गेस्ट पास बाकी", "PASES RESTANTES"),
        PhraseRule("SQUAD DUO PASS", "PASSE DUO DO ESQUADRÃO", "PASS DUO SKUAD", "THẺ ĐÔI ĐỘI", "SQUAD DUO PASS", "スクワッド・デュオパス", "स्क्वाड डुओ पास", "PASE DÚO DE ESCUADRÓN"),
        PhraseRule("Squad Message", "Mensagem do Esquadrão", "Pesan Skuad", "Tin Nhắn Đội", "Mensahe sa Squad", "スクワッド招待メッセージ", "स्क्वाड संदेश", "Mensaje de Escuadrón"),
        PhraseRule("TIER 1", "NÍVEL 1", "TINGKAT 1", "CẤP 1", "TIER 1", "ティア 1", "टियर 1", "NIVEL 1"),
        PhraseRule("TIER 2", "NÍVEL 2", "TINGKAT 2", "CẤP 2", "TIER 2", "ティア 2", "टियर 2", "NIVEL 2"),
        PhraseRule("TIER 3", "NÍVEL 3", "TINGKAT 3", "CẤP 3", "TIER 3", "ティア 3", "टियर 3", "NIVEL 3"),
        PhraseRule("TIER 4", "NÍVEL 4", "TINGKAT 4", "CẤP 4", "TIER 4", "ティア 4", "टियर 4", "NIVEL 4"),

        // Hardware, Controller, Performance & Tweaks Phrases
        PhraseRule("No controller detected", "Nenhum controle detectado", "Tidak ada stik terdeteksi", "Không phát hiện tay cầm", "Walang na-detect na controller", "コントローラー未検出", "कोई कंट्रोलर नहीं मिला", "No se detectó ningún mando"),
        PhraseRule("Controller Connected", "Controle Conectado", "Stik Terhubung", "Đã kết nối tay cầm", "Nakakonekta ang Controller", "コントローラー接続済み", "कंट्रोलर कनेक्टेड", "Mando Conectado"),
        PhraseRule("Controller Disconnected", "Controle Desconectado", "Stik Terputus", "Đã ngắt kết nối tay cầm", "Nadiskonekta ang Controller", "コントローラー切断", "कंट्रोलर डिस्कनेक्टेड", "Mando Desconectado"),
        PhraseRule("Controller Overlay", "Sobreposição de Controle", "Overlay Stik", "Lớp phủ tay cầm", "Controller Overlay", "コントローラーオーバーレイ", "कंट्रोलर ओवरले", "Superposición de Mando"),
        PhraseRule("Custom RGB Lightbar Mixer", "Mixer de Luz RGB Personalizado", "Mixer Lampu RGB Kustom", "Bộ trộn đèn RGB tùy chỉnh", "Custom RGB Lightbar Mixer", "カスタムRGBライトバー", "कस्टम RGB लाइटबार मिक्सर", "Mezclador de Barra RGB"),
        PhraseRule("Controller Battery Level", "Nível de Bateria do Controle", "Level Baterai Stik", "Mức pin tay cầm", "Antas ng Baterya ng Controller", "コントローラー電池残量", "कंट्रोलर बैटरी स्तर", "Nivel de Batería del Mando"),
        PhraseRule("Real Rumble Tester", "Testador de Vibração Real", "Penguji Getar Asli", "Kiểm tra rung thực tế", "Real Rumble Tester", "リアル振動テスター", "रियल वाइब्रेशन टेस्टर", "Probador de Vibración Real"),
        PhraseRule("Touch Pipeline Latency Test", "Teste de Latência de Toque", "Tes Latensi Sentuhan", "Kiểm tra độ trễ cảm ứng", "Touch Latency Test", "タッチ遅延テスト", "टच लेटेंसी टेस्ट", "Prueba de Latencia Táctil"),
        PhraseRule("Phone Hardware Specifications", "Especificações de Hardware", "Spesifikasi Perangkat Keras", "Thông số phần cứng", "Hardware Specifications", "ハードウェア仕様", "फ़ोन हार्डवेयर स्पेक्स", "Especificaciones de Hardware"),
        PhraseRule("Device & Operating System", "Dispositivo e Sistema", "Perangkat & OS", "Thiết bị & Hệ điều hành", "Device at OS", "デバイス＆OS", "डिवाइस और OS", "Dispositivo y Sistema"),
        PhraseRule("Memory & Storage", "Memória e Armazenamento", "Memori & Penyimpanan", "Bộ nhớ & Lưu trữ", "Memory at Storage", "メモリ＆ストレージ", "मेमोरी और स्टोरेज", "Memoria y Almacenamiento"),
        PhraseRule("Display & Graphics", "Tela e Gráficos", "Layar & Grafis", "Màn hình & Đồ họa", "Display at Graphics", "ディスプレイ＆グラフィックス", "डिस्प्ले और ग्राफ़िक्स", "Pantalla y Gráficos"),
        PhraseRule("Battery & Thermal", "Bateria e Temperatura", "Baterai & Suhu", "Pin & Nhiệt độ", "Baterya at Temperatura", "バッテリー＆温度", "बैटरी और तापमान", "Batería y Temperatura"),
        PhraseRule("Total Gaming Time", "Tempo Total de Jogo", "Total Waktu Main", "Tổng thời gian chơi", "Kabuuang Oras ng Laro", "合計プレイ時間", "कुल गेमिंग समय", "Tiempo Total de Juego"),
        PhraseRule("Most Played Games", "Jogos Mais Jogados", "Game Paling Sering Dimainkan", "Trò chơi chơi nhiều nhất", "Pinakamadalas Laruin", "最もプレイしたゲーム", "सबसे ज़्यादा खेले गए गेम्स", "Juegos Más Jugados"),
        PhraseRule("Gamerscore", "Pontuação Gamer", "Skor Gamer", "Điểm Gamer", "Gamerscore", "ゲーマースコア", "गेमरस्कोर", "Puntuación Gamer"),
        PhraseRule("Season Leaderboard", "Classificação da Temporada", "Papan Skor Musim", "Bảng xếp hạng mùa", "Season Leaderboard", "シーズンランキング", "सीज़न लीडरबोर्ड", "Tabla de Temporada"),
        PhraseRule("Tournament Rules", "Regras do Torneio", "Aturan Turnamen", "Luật giải đấu", "Mga Patakaran sa Tournament", "トーナメント規約", "टूर्नामेंट नियम", "Reglas del Torneo"),
        PhraseRule("Starting Focus Mode & Launching", "Ativando Modo Foco e Iniciando", "Memulai Mode Fokus & Game", "Đang bật Tập trung & Khởi chạy", "Sinisimulan ang Focus Mode at Laro", "フォーカスモード＆起動中", "फ़ोकस मोड और लॉन्च हो रहा है", "Activando Modo Enfoque e Iniciando"),
        PhraseRule("Screen Overlay HUD", "HUD de Sobreposição na Tela", "HUD Overlay Layar", "HUD Lớp phủ màn hình", "Screen Overlay HUD", "画面オーバーレイHUD", "स्क्रीन ओवरले HUD", "HUD en Pantalla"),
        PhraseRule("Clock Overlay", "Relógio na Sobreposição", "Overlay Jam", "Đồng hồ lớp phủ", "Clock Overlay", "時計オーバーレイ", "क्लॉक ओवरले", "Reloj en Pantalla"),
        PhraseRule("Screen Recorder", "Gravador de Tela", "Perekam Layar", "Trình ghi màn hình", "Screen Recorder", "画面レコーダー", "स्क्रीन रिकॉर्डर", "Grabador de Pantalla"),
        PhraseRule("High Impact", "Alto Impacto", "Dampak Tinggi", "Tác động cao", "Mataas na Epekto", "効果：大", "उच्च प्रभाव", "Impacto Alto"),
        PhraseRule("Medium Impact", "Médio Impacto", "Dampak Sedang", "Tác động vừa", "Katamtamang Epekto", "効果：中", "मध्यम प्रभाव", "Impacto Medio"),
        PhraseRule("Low Impact", "Baixo Impacto", "Dampak Rendah", "Tác động thấp", "Mababang Epekto", "効果：小", "कम प्रभाव", "Impacto Bajo"),
        PhraseRule("Best Value", "Melhor Custo-Benefício", "Nilai Terbaik", "Đáng tiền nhất", "Pinakasulit", "ベストバリュー", "सर्वोत्तम मूल्य", "Mejor Valor"),
        PhraseRule("Best Pick", "Melhor Escolha", "Pilihan Terbaik", "Lựa chọn tốt nhất", "Pinakamahusay na Pili", "ベストピック", "सर्वश्रेष्ठ चुनाव", "Mejor Opción"),
        PhraseRule("Editor's Pick", "Escolha do Editor", "Pilihan Editor", "Bình chọn của Biên tập", "Pili ng Editor", "編集部おすすめ", "एडिटर की पसंद", "Elección del Editor"),
        PhraseRule("Finger Sleeves", "Luvas de Dedo", "Sarung Jari", "Bao tay chơi game", "Finger Sleeves", "指サック", "फ़िंगर स्लीव्स", "Dedales Gamer"),
        PhraseRule("Permissions", "Permissões", "Izin", "Quyền", "Mga Pahintulot", "権限", "अनुमतियाँ", "Permisos"),
        PhraseRule("Settings", "Configurações", "Pengaturan", "Cài đặt", "Settings", "設定", "सेटिंग्स", "Configuración"),
        PhraseRule("available", "disponíveis", "tersedia", "khả dụng", "available", "利用可能", "उपलब्ध", "disponibles"),
        PhraseRule("freed", "liberados", "dibebaskan", "đã giải phóng", "napalaya", "解放済み", "मुक्त किया गया", "liberados"),
        PhraseRule("Optimized", "Otimizado", "Dioptimalkan", "Đã tối ưu", "Na-optimize", "最適化済み", "ऑप्टिमाइज़ किया गया", "Optimizado")
    )

    /**
     * Comprehensive token-level vocabulary for translating remaining English words in dynamic strings,
     * descriptions, status banners, and compound labels across all 8 languages.
     */
    private val multiLangWordVocabulary: Map<String, PhraseRule> = listOf(
        PhraseRule("game", "jogo", "game", "trò chơi", "laro", "ゲーム", "गेम", "juego"),
        PhraseRule("gaming", "jogos", "gaming", "chơi game", "paglalaro", "ゲーミング", "गेमिंग", "gaming"),
        PhraseRule("focus", "foco", "fokus", "tập trung", "focus", "フォーカス", "फ़ोकस", "enfoque"),
        PhraseRule("launch", "iniciar", "mulai", "khởi chạy", "ilunsad", "起動", "लॉन्च", "iniciar"),
        PhraseRule("memory", "memória", "memori", "bộ nhớ", "memorya", "メモリ", "मेमोरी", "memoria"),
        PhraseRule("storage", "armazenamento", "penyimpanan", "lưu trữ", "storage", "ストレージ", "स्टोरेज", "almacenamiento"),
        PhraseRule("battery", "bateria", "baterai", "pin", "baterya", "バッテリー", "बैटरी", "batería"),
        PhraseRule("temperature", "temperatura", "suhu", "nhiệt độ", "temperatura", "温度", "तापमान", "temperatura"),
        PhraseRule("thermal", "térmico", "termal", "nhiệt", "thermal", "熱制御", "थर्मल", "térmico"),
        PhraseRule("cooling", "resfriamento", "pendingin", "tản nhiệt", "pampalamig", "冷却", "कूलिंग", "enfriamiento"),
        PhraseRule("performance", "desempenho", "performa", "hiệu năng", "pagganap", "パフォーマンス", "परफॉर्मेंस", "rendimiento"),
        PhraseRule("frame", "quadro", "frame", "khung hình", "frame", "フレーム", "फ़्रेम", "cuadro"),
        PhraseRule("rate", "taxa", "laju", "tốc độ", "rate", "レート", "दर", "tasa"),
        PhraseRule("latency", "latência", "latensi", "độ trễ", "latency", "遅延", "लेटेंसी", "latencia"),
        PhraseRule("touch", "toque", "sentuhan", "cảm ứng", "touch", "タッチ", "टच", "táctil"),
        PhraseRule("sensitivity", "sensibilidade", "sensitivitas", "độ nhạy", "sensitibidad", "感度", "सेंसिटिविटी", "sensibilidad"),
        PhraseRule("response", "resposta", "respons", "phản hồi", "tugon", "応答", "प्रतिक्रिया", "respuesta"),
        PhraseRule("controller", "controle", "stik", "tay cầm", "controller", "コントローラー", "कंट्रोलर", "mando"),
        PhraseRule("console", "console", "konsol", "console", "console", "コンソール", "कंसोल", "consola"),
        PhraseRule("mode", "modo", "mode", "chế độ", "mode", "モード", "मोड", "modo"),
        PhraseRule("screen", "tela", "layar", "màn hình", "screen", "画面", "स्क्रीन", "pantalla"),
        PhraseRule("display", "exibição", "layar", "màn hình", "display", "ディスプレイ", "डिस्प्ले", "pantalla"),
        PhraseRule("overlay", "sobreposição", "overlay", "lớp phủ", "overlay", "オーバーレイ", "ओवरले", "superposición"),
        PhraseRule("crosshair", "mira", "crosshair", "tâm ngắm", "crosshair", "クロスヘア", "क्रॉसहेयर", "mira"),
        PhraseRule("recorder", "gravador", "perekam", "ghi hình", "recorder", "レコーダー", "रिकॉर्डर", "grabadora"),
        PhraseRule("recording", "gravação", "rekaman", "bản ghi", "recording", "録画", "रिकॉर्डिंग", "grabación"),
        PhraseRule("video", "vídeo", "video", "video", "video", "動画", "वीडियो", "video"),
        PhraseRule("audio", "áudio", "audio", "âm thanh", "audio", "音声", "ऑडियो", "audio"),
        PhraseRule("sound", "som", "suara", "âm thanh", "tunog", "サウンド", "ध्वनि", "sonido"),
        PhraseRule("haptic", "vibração", "getar", "rung", "haptic", "触覚振動", "हैप्टिक", "vibración"),
        PhraseRule("network", "rede", "jaringan", "mạng", "network", "ネットワーク", "नेटवर्क", "red"),
        PhraseRule("online", "online", "online", "trực tuyến", "online", "オンライン", "ऑनलाइन", "en línea"),
        PhraseRule("offline", "offline", "offline", "ngoại tuyến", "offline", "オフライン", "ऑफ़लाइन", "sin conexión"),
        PhraseRule("cloud", "nuvem", "cloud", "đám mây", "cloud", "クラウド", "क्लाउड", "nube"),
        PhraseRule("sync", "sincronização", "sinkron", "đồng bộ", "sync", "同期", "सिंक", "sincronización"),
        PhraseRule("synced", "sincronizado", "tersinkron", "đã đồng bộ", "na-sync", "同期済", "सिंक किया गया", "sincronizado"),
        PhraseRule("account", "conta", "akun", "tài khoản", "account", "アカウント", "खाता", "cuenta"),
        PhraseRule("profile", "perfil", "profil", "hồ sơ", "profile", "プロフィール", "प्रोफ़ाइल", "perfil"),
        PhraseRule("rank", "patente", "peringkat", "hạng", "ranggo", "ランク", "रैंक", "rango"),
        PhraseRule("ranking", "classificação", "peringkat", "xếp hạng", "ranking", "ランキング", "रैंकिंग", "clasificación"),
        PhraseRule("leaderboard", "classificação", "papan skor", "bảng xếp hạng", "leaderboard", "リーダーボード", "लीडरबोर्ड", "clasificación"),
        PhraseRule("score", "pontuação", "skor", "điểm", "puntos", "スコア", "स्कोर", "puntuación"),
        PhraseRule("season", "temporada", "musim", "mùa giải", "season", "シーズン", "सीज़न", "temporada"),
        PhraseRule("squad", "esquadrão", "skuad", "đội", "squad", "スクワッド", "स्क्वाड", "escuadrón"),
        PhraseRule("friend", "amigo", "teman", "bạn bè", "kaibigan", "フレンド", "मित्र", "amigo"),
        PhraseRule("invite", "convite", "undang", "mời", "imbita", "招待", "आमंत्रण", "invitación"),
        PhraseRule("referral", "indicação", "referal", "giới thiệu", "referral", "紹介", "रेफ़रल", "referido"),
        PhraseRule("code", "código", "kode", "mã", "code", "コード", "कोड", "código"),
        PhraseRule("pass", "passe", "pass", "thẻ", "pass", "パス", "पास", "pase"),
        PhraseRule("reward", "recompensa", "hadiah", "phần thưởng", "gantimpala", "報酬", "इनाम", "recompensa"),
        PhraseRule("unlocked", "desbloqueado", "terbuka", "đã mở khóa", "nabuksan", "解除済", "अनलॉक", "desbloqueado"),
        PhraseRule("locked", "bloqueado", "terkunci", "đã khóa", "naka-lock", "ロック中", "लॉक", "bloqueado"),
        PhraseRule("verified", "verificado", "terverifikasi", "đã xác minh", "verified", "認証済", "सत्यापित", "verificado"),
        PhraseRule("pending", "pendente", "menunggu", "đang chờ", "nakabinbin", "保留中", "लंबित", "pendiente"),
        PhraseRule("active", "ativo", "aktif", "hoạt động", "aktibo", "有効", "सक्रिय", "activo"),
        PhraseRule("inactive", "inativo", "nonaktif", "tắt", "hindi aktibo", "無効", "निष्क्रिय", "inactivo"),
        PhraseRule("enabled", "ativado", "diaktifkan", "đã bật", "naka-enable", "オン", "चालू", "activado"),
        PhraseRule("disabled", "desativado", "dinonaktifkan", "đã tắt", "naka-disable", "オフ", "बंद", "desactivado"),
        PhraseRule("connected", "conectado", "terhubung", "đã kết nối", "nakakonekta", "接続済", "कनेक्टेड", "conectado"),
        PhraseRule("disconnected", "desconectado", "terputus", "ngắt kết nối", "nadiskonekta", "未接続", "डिस्कनेक्टेड", "desconectado"),
        PhraseRule("device", "dispositivo", "perangkat", "thiết bị", "device", "端末", "डिवाइस", "dispositivo"),
        PhraseRule("hardware", "hardware", "perangkat keras", "phần cứng", "hardware", "ハードウェア", "हार्डवेयर", "hardware"),
        PhraseRule("system", "sistema", "sistem", "hệ thống", "sistema", "システム", "सिस्टम", "sistema"),
        PhraseRule("graphics", "gráficos", "grafis", "đồ họa", "graphics", "グラフィックス", "ग्राफ़िक्स", "gráficos"),
        PhraseRule("shader", "shader", "shader", "shader", "shader", "シェーダー", "शेडर", "shader"),
        PhraseRule("cache", "cache", "cache", "bộ nhớ đệm", "cache", "キャッシュ", "कैश", "caché"),
        PhraseRule("background", "segundo plano", "latar belakang", "nền", "background", "バックグラウンド", "बैकग्राउंड", "segundo plano"),
        PhraseRule("process", "processo", "proses", "tiến trình", "proseso", "プロセス", "प्रोसेस", "proceso"),
        PhraseRule("priority", "prioridade", "prioritas", "ưu tiên", "prayoridad", "優先度", "प्राथमिकता", "prioridad"),
        PhraseRule("high", "alto", "tinggi", "cao", "mataas", "高", "उच्च", "alto"),
        PhraseRule("medium", "médio", "sedang", "trung bình", "katamtaman", "中", "मध्यम", "medio"),
        PhraseRule("low", "baixo", "rendah", "thấp", "mababa", "低", "कम", "bajo"),
        PhraseRule("ultra", "ultra", "ultra", "siêu cấp", "ultra", "ウルトラ", "अल्ट्रा", "ultra"),
        PhraseRule("maximum", "máximo", "maksimum", "tối đa", "pinakamataas", "最大", "अधिकतम", "máximo"),
        PhraseRule("minimum", "mínimo", "minimum", "tối thiểu", "pinakamababa", "最小", "न्यूनतम", "mínimo"),
        PhraseRule("optimal", "ideal", "optimal", "tối ưu", "optimal", "最適", "उत्तम", "óptimo"),
        PhraseRule("stable", "estável", "stabil", "ổn định", "matatag", "安定", "स्थिर", "estable"),
        PhraseRule("stability", "estabilidade", "stabilitas", "độ ổn định", "katatagan", "安定性", "स्थिरता", "estabilidad"),
        PhraseRule("smooth", "fluido", "mulus", "mượt mà", "makinis", "滑らか", "स्मूथ", "fluido"),
        PhraseRule("fast", "rápido", "cepat", "nhanh", "mabilis", "高速", "तेज़", "rápido"),
        PhraseRule("instant", "instantâneo", "instan", "tức thì", "agad", "即時", "तुरंत", "instantáneo"),
        PhraseRule("automatic", "automático", "otomatis", "tự động", "awtomatiko", "自動", "स्वचालित", "automático"),
        PhraseRule("custom", "personalizado", "kustom", "tùy chỉnh", "custom", "カスタム", "कस्टम", "personalizado"),
        PhraseRule("default", "padrão", "bawaan", "mặc định", "default", "標準", "डिफ़ॉल्ट", "predeterminado"),
        PhraseRule("theme", "tema", "tema", "giao diện", "tema", "テーマ", "थीम", "tema"),
        PhraseRule("dark", "escuro", "gelap", "tối", "madilim", "ダーク", "डार्क", "oscuro"),
        PhraseRule("light", "claro", "terang", "sáng", "maliwanag", "ライト", "लाइट", "claro"),
        PhraseRule("color", "cor", "warna", "màu sắc", "kulay", "カラー", "रंग", "color"),
        PhraseRule("size", "tamanho", "ukuran", "kích thước", "laki", "サイズ", "आकार", "tamaño"),
        PhraseRule("opacity", "opacidade", "opasitas", "độ mờ", "opacity", "不透明度", "ओपैसिटी", "opacidad"),
        PhraseRule("position", "posição", "posisi", "vị trí", "posisyon", "位置", "स्थिति", "posición"),
        PhraseRule("style", "estilo", "gaya", "kiểu", "istilo", "スタイル", "स्टाइल", "estilo"),
        PhraseRule("preset", "predefinição", "preset", "cấu hình sẵn", "preset", "プリセット", "प्रीसेट", "ajuste"),
        PhraseRule("apply", "aplicar", "terapkan", "áp dụng", "i-apply", "適用", "लागू करें", "aplicar"),
        PhraseRule("applied", "aplicado", "diterapkan", "đã áp dụng", "na-apply", "適用済", "लागू किया गया", "aplicado"),
        PhraseRule("save", "salvar", "simpan", "lưu", "i-save", "保存", "सेव", "guardar"),
        PhraseRule("saved", "salvo", "disimpan", "đã lưu", "na-save", "保存済", "सेव किया गया", "guardado"),
        PhraseRule("reset", "redefinir", "atur ulang", "đặt lại", "i-reset", "リセット", "रीसेट", "restablecer"),
        PhraseRule("clear", "limpar", "bersihkan", "dọn dẹp", "linisin", "クリア", "साफ़ करें", "limpiar"),
        PhraseRule("copy", "copiar", "salin", "sao chép", "kopyahin", "コピー", "कॉपी", "copiar"),
        PhraseRule("copied", "copiado", "disalin", "đã sao chép", "nakopya", "コピー済", "कॉपी किया गया", "copiado"),
        PhraseRule("share", "compartilhar", "bagikan", "chia sẻ", "i-share", "共有", "शेयर", "compartir"),
        PhraseRule("claim", "resgatar", "klaim", "nhận", "i-claim", "受取", "क्लेम", "canjear"),
        PhraseRule("claimed", "resgatado", "diklaim", "đã nhận", "na-claim", "受取済", "क्लेम किया गया", "canjeado"),
        PhraseRule("hours", "horas", "jam", "giờ", "oras", "時間", "घंटे", "horas"),
        PhraseRule("minutes", "minutos", "menit", "phút", "minuto", "分", "मिनट", "minutos"),
        PhraseRule("seconds", "segundos", "detik", "giây", "segundo", "秒", "सेकंड", "segundos"),
        PhraseRule("days", "dias", "hari", "ngày", "araw", "日間", "दिन", "días"),
        PhraseRule("week", "semana", "minggu", "tuần", "linggo", "週間", "सप्ताह", "semana"),
        PhraseRule("match", "partida", "laga", "trận đấu", "laban", "マッチ", "मैच", "partida"),
        PhraseRule("session", "sessão", "sesi", "phiên chơi", "session", "セッション", "सेशन", "sesión"),
        PhraseRule("total", "total", "total", "tổng", "kabuuan", "合計", "कुल", "total"),
        PhraseRule("free", "livre", "kosong", "trống", "libre", "空き", "खाली", "libre"),
        PhraseRule("used", "usado", "terpakai", "đã dùng", "nagagamit", "使用中", "उपयोग में", "usado"),
        PhraseRule("left", "restantes", "tersisa", "còn lại", "natitira", "残り", "बाकी", "restantes"),
        PhraseRule("ready", "pronto", "siap", "sẵn sàng", "handa", "準備完了", "तैयार", "listo"),
        PhraseRule("completed", "concluído", "selesai", "hoàn thành", "tapos na", "完了", "पूर्ण", "completado"),
        PhraseRule("invalid", "inválido", "tidak valid", "không hợp lệ", "invalid", "無効な", "अमान्य", "inválido"),
        PhraseRule("error", "erro", "kesalahan", "lỗi", "error", "エラー", "त्रुटि", "error"),
        PhraseRule("warning", "aviso", "peringatan", "cảnh báo", "babala", "警告", "चेतावनी", "advertencia"),
        PhraseRule("security", "segurança", "keamanan", "bảo mật", "seguridad", "セキュリティ", "सुरक्षा", "seguridad"),
        PhraseRule("budget", "econômico", "hemat", "tiết kiệm", "budget", "エントリー", "बजट", "económico"),
        PhraseRule("premium", "premium", "premium", "cao cấp", "premium", "プレミアム", "प्रीमियम", "premium"),
        PhraseRule("gear", "equipamento", "gear", "thiết bị", "gear", "ギア", "गियर", "equipo"),
        PhraseRule("power", "energia", "daya", "nguồn điện", "power", "電源・充電", "पावर", "energía"),
        PhraseRule("all", "todos", "semua", "tất cả", "lahat", "すべて", "सभी", "todos")
    ).associateBy { it.en.lowercase(Locale.ROOT) }
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
    val active = currentAppLanguage()
    return active == AppLanguage.ES_MX || active == AppLanguage.ES_LA
}

@Composable
@ReadOnlyComposable
fun tr(
    en: String,
    es: String,
    ptBr: String? = null,
    id: String? = null,
    vi: String? = null,
    tl: String? = null,
    esLa: String? = null,
    ja: String? = null,
    hi: String? = null
): String {
    val active = currentAppLanguage()
    return AppStrings.translateForLanguage(
        en = en,
        es = es,
        ptBr = ptBr,
        id = id,
        vi = vi,
        tl = tl,
        active = active,
        esLa = esLa,
        ja = ja,
        hi = hi
    )
}
