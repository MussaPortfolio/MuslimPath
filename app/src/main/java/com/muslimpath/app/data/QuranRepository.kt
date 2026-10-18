package com.muslimpath.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class Sura(
    val number: Int,
    val name: String,       // транслитерация: «Аль-Фатиха»
    val meaning: String,    // значение названия: «Открывающая»
    val arabic: String,
    val ayahCount: Int,
    val meccan: Boolean,
)

data class Ayah(val sura: Int, val number: Int, val arabic: String, val translation: String)

/** A bundled translation. New licensed translations (e.g. Kuliev) are added here as another asset file. */
data class QuranTranslation(val id: String, val title: String, val asset: String, val credit: String)

/**
 * Quran text bundled in assets, used offline.
 * Arabic: Tanzil Quran Text (Uthmani) — © Tanzil Project, CC BY 3.0; the files are kept verbatim
 * with their copyright block, as the license requires.
 * Translation: И. Ю. Крачковский (public domain), digital text from Tanzil.net.
 */
class QuranRepository(private val context: Context) {

    val translations = listOf(
        QuranTranslation(
            id = "ru.krachkovsky",
            title = "Крачковский",
            asset = "quran/ru.krachkovsky.txt",
            credit = "Перевод смыслов: И. Ю. Крачковский. Электронный текст: Tanzil.net",
        ),
    )

    val suras: List<Sura> by lazy {
        val json = context.assets.open("quran/suras.json").bufferedReader().use { it.readText() }
        JSONArray(json).objects().map {
            Sura(
                number = it.getInt("n"),
                name = it.getString("name"),
                meaning = it.getString("meaning"),
                arabic = it.getString("arabic"),
                ayahCount = it.getInt("ayahs"),
                meccan = it.getString("type") == "meccan",
            )
        }
    }

    /** Loads all ayahs grouped by sura (≈2.7 MB of text; parsed once off the main thread). */
    suspend fun load(translation: QuranTranslation = translations.first()): Map<Int, List<Ayah>> = withContext(Dispatchers.IO) {
        val arabic = readTanzil("quran/quran-uthmani.txt")
        val trans = readTanzil(translation.asset)
        arabic.map { (key, text) -> Ayah(key.first, key.second, text, trans[key].orEmpty()) }
            .groupBy { it.sura }
    }

    /** Tanzil "txt-2" format: `sura|ayah|text`, comment lines start with `#`. */
    private fun readTanzil(asset: String): LinkedHashMap<Pair<Int, Int>, String> {
        val result = LinkedHashMap<Pair<Int, Int>, String>(6300)
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val a = line.indexOf('|')
                val b = line.indexOf('|', a + 1)
                if (a < 0 || b < 0) return@forEach
                result[line.substring(0, a).trim().toInt() to line.substring(a + 1, b).trim().toInt()] = line.substring(b + 1).trim()
            }
        }
        return result
    }

    companion object {
        const val ATTRIBUTION =
            "Арабский текст Корана: Tanzil Project (tanzil.net), лицензия Creative Commons Attribution 3.0. " +
                "Перевод смыслов: И. Ю. Крачковский. Перевод смыслов не является Кораном — это попытка передать его значение на русском языке."
    }
}
