package com.muslimpath.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads learning content. MVP ships verified content bundled in assets (works offline);
 * the same JSON shape is produced by the admin panel export / Supabase `content_bundle` view.
 */
class ContentRepository(private val context: Context) {

    fun load(): Content {
        val json = context.assets.open("content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(JSONObject(json))
    }

    companion object {
        fun parse(root: JSONObject): Content = Content(
            routes = root.getJSONArray("routes").objects().map {
                LearningRoute(
                    id = it.getString("id"),
                    title = it.getString("title"),
                    description = it.getString("description"),
                    icon = it.optString("icon", "route"),
                    lessonIds = it.getJSONArray("lessons").strings(),
                )
            },
            categories = root.getJSONArray("categories").objects().map {
                Category(it.getString("id"), it.getString("title"), it.optString("icon", "book"))
            },
            lessons = root.getJSONArray("lessons").objects().map(::parseLesson),
            faqs = root.optJSONArray("faqs")?.objects()?.map {
                Faq(
                    id = it.getString("id"),
                    question = it.getString("question"),
                    answer = it.getString("answer"),
                    categoryId = it.optString("category", "faq"),
                    popular = it.optBoolean("popular"),
                    sources = parseSources(it.optJSONArray("sources")),
                    review = parseReview(it.optJSONObject("review")),
                )
            } ?: emptyList(),
        )

        private fun parseLesson(o: JSONObject) = Lesson(
            id = o.getString("id"),
            categoryId = o.getString("category"),
            title = o.getString("title"),
            summary = o.getString("summary"),
            minutes = o.optInt("minutes", 5),
            blocks = o.getJSONArray("blocks").objects().mapNotNull(::parseBlock),
            quiz = o.optJSONArray("quiz")?.objects()?.map {
                QuizQuestion(
                    question = it.getString("q"),
                    options = it.getJSONArray("options").strings(),
                    answer = it.getInt("answer"),
                    explanation = it.optStringOrNull("explanation"),
                )
            } ?: emptyList(),
            sources = parseSources(o.optJSONArray("sources")),
            review = parseReview(o.optJSONObject("review")),
            updatedAt = o.optString("updated"),
            related = o.optJSONArray("related")?.strings() ?: emptyList(),
        )

        private fun parseBlock(o: JSONObject): LessonBlock? = when (o.getString("type")) {
            "text" -> LessonBlock.Text(o.getString("text"))
            "heading" -> LessonBlock.Heading(o.getString("text"))
            "steps" -> LessonBlock.Steps(o.getJSONArray("items").strings())
            "bullets" -> LessonBlock.Bullets(o.getJSONArray("items").strings())
            "note" -> LessonBlock.Note(o.getString("text"))
            "arabic" -> LessonBlock.Arabic(
                o.getString("arabic"),
                o.optStringOrNull("translit"),
                o.optStringOrNull("translation"),
            )
            else -> null // unknown block types from newer content are skipped
        }

        private fun parseSources(a: JSONArray?) =
            a?.objects()?.map { Source(it.getString("title"), it.optString("ref")) } ?: emptyList()

        private fun parseReview(o: JSONObject?) = Review(
            status = ReviewStatus.parse(o?.optStringOrNull("status")),
            reviewer = o?.optStringOrNull("reviewer"),
            reviewedAt = o?.optStringOrNull("date"),
            editor = o?.optStringOrNull("editor"),
        )
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).ifEmpty { null }
