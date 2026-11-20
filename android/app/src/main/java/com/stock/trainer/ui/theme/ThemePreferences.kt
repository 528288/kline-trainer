package com.stock.trainer.ui.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(themeStyleFromId(prefs.getString(KEY_STYLE, null)))
    val style: StateFlow<AppThemeStyle> = _style.asStateFlow()

    fun setStyle(style: AppThemeStyle) {
        _style.value = style
        prefs.edit().putString(KEY_STYLE, style.id).apply()
    }

    private companion object {
        const val KEY_STYLE = "theme_style"
    }
}
