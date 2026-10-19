package com.example.ui.theme

/**
 * Represents the application background theme selection for DataGuard.
 * Users can choose Dark, White (Light), or System default.
 */
enum class AppThemeMode(val key: String, val title: String, val subtitle: String) {
    SYSTEM("system", "System", "Follows device dark/light theme"),
    LIGHT("light", "White", "Crisp white background"),
    DARK("dark", "Dark", "Deep dark background");

    companion object {
        fun fromKey(key: String): AppThemeMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}
