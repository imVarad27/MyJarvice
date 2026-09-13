package com.example.myjarvice.ui.main

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Lightweight readable formatting. Text remains selectable and never becomes executable UI. */
internal fun formatReplyText(text: String): AnnotatedString = buildAnnotatedString {
    var code = false
    val inline = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    text.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        if (line.trimStart().startsWith("```")) {
            code = !code
        } else if (code) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(line) }
        } else {
            val heading = Regex("^#{1,6}\\s+").find(line)
            val content = if (heading != null) line.substring(heading.range.last + 1)
                else if (line.startsWith("- ")) "• " + line.drop(2) else line
            withStyle(SpanStyle(fontWeight = if (heading != null) FontWeight.SemiBold else FontWeight.Normal)) {
                var cursor = 0
                inline.findAll(content).forEach { match ->
                    append(content.substring(cursor, match.range.first))
                    val bold = match.value.startsWith("**")
                    withStyle(if (bold) SpanStyle(fontWeight = FontWeight.SemiBold) else SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(match.groupValues[if (bold) 1 else 2])
                    }
                    cursor = match.range.last + 1
                }
                append(content.substring(cursor))
            }
        }
    }
}
