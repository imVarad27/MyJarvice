package com.example.myjarvice.ui.main

internal sealed interface ReplyBlock {
    data class Prose(val text: String) : ReplyBlock
    data class Code(val language: String, val text: String) : ReplyBlock
}

/** Presentation-only fenced blocks, including unfinished streaming fences. Never executes content. */
internal fun replyBlocks(text: String): List<ReplyBlock> {
    val result = mutableListOf<ReplyBlock>()
    val prose = mutableListOf<String>()
    val code = mutableListOf<String>()
    val opener = Regex("^ {0,3}(`{3,}|~{3,})([A-Za-z0-9_+.#-]{0,32})[ \\t]*$")
    var fence: String? = null
    var language = ""
    fun flushProse() {
        if (prose.isNotEmpty()) { result += ReplyBlock.Prose(prose.joinToString("\n")); prose.clear() }
    }
    text.lineSequence().forEach { line ->
        val active = fence
        if (active == null) {
            val match = opener.matchEntire(line)
            if (match == null) prose += line else {
                flushProse(); fence = match.groupValues[1]; language = match.groupValues[2]
            }
        } else {
            val closing = line.trimStart(' ').trimEnd(' ', '\t')
            val indent = line.length - line.trimStart(' ').length
            if (indent <= 3 && closing.length >= active.length && closing.all { it == active.first() }) {
                result += ReplyBlock.Code(language, code.joinToString("\n")); code.clear(); fence = null
            } else code += line
        }
    }
    if (fence != null) result += ReplyBlock.Code(language, code.joinToString("\n")) else flushProse()
    return result
}
