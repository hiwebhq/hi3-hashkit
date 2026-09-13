package hi3.hashkit.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Languages the app ships translations for. Keep in sync with res/xml/locales_config.xml
 * and the res/values-XX folders.
 *
 * [nativeName] is deliberately NOT a string resource: each language always shows in its own
 * script (a Russian speaker stuck in Chinese must still be able to find "Русский").
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    CHINESE("zh-CN", "中文"),
    RUSSIAN("ru", "Русский"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    PORTUGUESE("pt-BR", "Português (BR)"),
    ;

    companion object {
        /** The currently applied per-app language (SYSTEM when the user never picked one). */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val applied = locales[0] ?: return SYSTEM
            return entries.filter { it != SYSTEM }.firstOrNull {
                applied.language == LocaleListCompat.forLanguageTags(it.tag)[0]?.language
            } ?: SYSTEM
        }

        /**
         * Apply (and persist) a language app-wide. AppCompat recreates any live activities so
         * the change is immediate; autoStoreLocales / Android 13+ restore it on next launch.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
