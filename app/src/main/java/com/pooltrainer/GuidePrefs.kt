package com.pooltrainer

import android.content.Context

object GuidePrefs {
    private const val FILE = "aim_tool_settings"
    const val AI_GUIDE = "ai_guide"
    const val LINE_GUIDES = "line_guides"
    const val EXTENDED_LINES = "extended_lines"
    const val BANK_SHOTS = "bank_shots"
    fun enabled(context: Context, key: String, fallback: Boolean = true): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(key, fallback)
    fun set(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}
