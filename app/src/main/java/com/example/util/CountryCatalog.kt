package com.example.util

import java.util.Locale

data class CountryOption(
    val code: String,
    val name: String,
    val flagEmoji: String
)

object CountryCatalog {

    fun codeToFlagEmoji(countryCode: String): String {
        val clean = countryCode.trim().uppercase(Locale.ROOT)
        if (clean.length != 2 || !clean[0].isLetter() || !clean[1].isLetter()) {
            return "🌐"
        }
        val firstChar = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    val ALL_COUNTRIES: List<CountryOption> = listOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "AU" to "Australia",
        "BR" to "Brazil",
        "DE" to "Germany",
        "FR" to "France",
        "JP" to "Japan",
        "KR" to "South Korea",
        "IN" to "India",
        "MX" to "Mexico",
        "ES" to "Spain",
        "IT" to "Italy",
        "PH" to "Philippines",
        "ID" to "Indonesia",
        "TR" to "Turkey",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
        "NG" to "Nigeria",
        "ZA" to "South Africa",
        "AR" to "Argentina",
        "CO" to "Colombia",
        "CL" to "Chile",
        "PE" to "Peru",
        "NL" to "Netherlands",
        "SE" to "Sweden",
        "NO" to "Norway",
        "DK" to "Denmark",
        "FI" to "Finland",
        "PL" to "Poland",
        "SG" to "Singapore",
        "MY" to "Malaysia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "NZ" to "New Zealand",
        "IE" to "Ireland",
        "CH" to "Switzerland",
        "AT" to "Austria",
        "BE" to "Belgium",
        "PT" to "Portugal",
        "GR" to "Greece",
        "EG" to "Egypt",
        "KE" to "Kenya",
        "GH" to "Ghana",
        "MA" to "Morocco",
        "PK" to "Pakistan",
        "BD" to "Bangladesh",
        "UA" to "Ukraine",
        "RO" to "Romania",
        "CZ" to "Czechia",
        "HU" to "Hungary",
        "IL" to "Israel",
        "QA" to "Qatar",
        "KW" to "Kuwait",
        "JM" to "Jamaica",
        "TT" to "Trinidad & Tobago",
        "DO" to "Dominican Republic",
        "PR" to "Puerto Rico",
        "CR" to "Costa Rica",
        "PA" to "Panama",
        "EC" to "Ecuador",
        "UY" to "Uruguay",
        "KZ" to "Kazakhstan"
    ).map { (code, name) ->
        CountryOption(
            code = code,
            name = name,
            flagEmoji = codeToFlagEmoji(code)
        )
    }

    private val BY_CODE: Map<String, CountryOption> = ALL_COUNTRIES.associateBy { it.code.uppercase(Locale.ROOT) }

    fun getCountryByCode(code: String?): CountryOption? {
        if (code.isNullOrBlank()) return null
        val normalized = code.trim().uppercase(Locale.ROOT)
        return BY_CODE[normalized] ?: if (normalized.length == 2 && normalized.all { it.isLetter() }) {
            val localeName = Locale("", normalized).displayCountry.takeIf { it.isNotBlank() } ?: normalized
            CountryOption(normalized, localeName, codeToFlagEmoji(normalized))
        } else {
            null
        }
    }

    fun getDefaultCountry(): CountryOption {
        val localeCode = try {
            Locale.getDefault().country.uppercase(Locale.ROOT)
        } catch (_: Exception) {
            "US"
        }
        return getCountryByCode(localeCode) ?: BY_CODE["US"]!!
    }

    fun searchCountries(query: String): List<CountryOption> {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return ALL_COUNTRIES
        return ALL_COUNTRIES.filter {
            it.name.lowercase(Locale.ROOT).contains(trimmed) ||
                it.code.lowercase(Locale.ROOT).contains(trimmed)
        }
    }
}
