package com.example.myjarvice.ui.main

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Lightweight readable formatting. Text remains selectable and never becomes executable UI. */
internal fun formatReplyText(text: String): AnnotatedString = buildAnnotatedString {
    var code = false
    val inline = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\*([^*\\n]+)\\*|_([^_\\n]+)_")
    text.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        if (line.trimStart().startsWith("```")) {
            code = !code
        } else if (code) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(line) }
        } else {
            val heading = Regex("^#{1,6}\\s+").find(line)
            val trimmed = line.trimStart()
            val content = when {
                heading != null -> line.substring(heading.range.last + 1)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> "• " + trimmed.drop(2)
                trimmed.startsWith("> ") -> "│ " + trimmed.drop(2)
                else -> line
            }
            withStyle(SpanStyle(fontWeight = if (heading != null) FontWeight.SemiBold else FontWeight.Normal)) {
                var cursor = 0
                inline.findAll(content).forEach { match ->
                    append(content.substring(cursor, match.range.first))
                    val (value, style) = when {
                        match.groupValues[1].isNotEmpty() -> match.groupValues[1] to SpanStyle(fontWeight = FontWeight.SemiBold)
                        match.groupValues[2].isNotEmpty() -> match.groupValues[2] to SpanStyle(fontFamily = FontFamily.Monospace)
                        match.groupValues[3].isNotEmpty() -> match.groupValues[3] to SpanStyle(fontStyle = FontStyle.Italic)
                        else -> match.groupValues[4] to SpanStyle(fontStyle = FontStyle.Italic)
                    }
                    withStyle(style) {
                        append(value)
                    }
                    cursor = match.range.last + 1
                }
                append(content.substring(cursor))
            }
        }
    }
}
