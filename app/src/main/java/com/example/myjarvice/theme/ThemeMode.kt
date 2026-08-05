package com.example.myjarvice.theme

/**
 * User-selectable theme modes.
 *
 * SYSTEM follows the OS light/dark setting; AMOLED is a pure-black dark variant
 * that saves power on OLED panels. Dynamic (Material You) color is an orthogonal
 * toggle applied on top of the chosen mode (Android 12+).
 */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED")
}
