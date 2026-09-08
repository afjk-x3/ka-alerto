package com.macci.kaalerto.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Filipino is the app's base language — every screen was written Filipino-first, per the
 * artboards and PRD. English is a toggle, not a second base: [tr] takes the Filipino
 * string as the copy of record and an English string beside it at the call site, so a
 * translation is never more than a few lines from the text it translates and can't drift
 * out of sync the way a separate resources file would.
 *
 * Many screens already showed both languages stacked (a bold Filipino line, a smaller
 * English line underneath) because that's how the artboards were drawn — this toggle
 * replaces that permanent stacking with a choice: one language on screen at a time.
 */
enum class AppLanguage { FIL, EN }

val LocalAppLanguage = compositionLocalOf { AppLanguage.FIL }

/** Returns [fil] or [en] depending on [LocalAppLanguage]. */
@Composable
fun tr(fil: String, en: String): String = if (LocalAppLanguage.current == AppLanguage.EN) en else fil

/** Non-composable form, for labels computed outside a @Composable (e.g. inside a lambda passed a plain AppLanguage). */
fun tr(language: AppLanguage, fil: String, en: String): String = if (language == AppLanguage.EN) en else fil

/**
 * Persisted across launches — a resident who picks English does not want to repick it
 * every cold start. Same SharedPreferences pattern as identity/LocalIdentity.kt.
 */
object LanguagePrefs {
    private const val PREFS = "kaalerto_language"
    private const val KEY_LANGUAGE = "language"

    fun get(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getString(KEY_LANGUAGE, null) == AppLanguage.EN.name) AppLanguage.EN else AppLanguage.FIL
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}
