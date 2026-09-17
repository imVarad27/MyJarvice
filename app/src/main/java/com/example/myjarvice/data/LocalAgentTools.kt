package com.example.myjarvice.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** No network, writes, file paths, shell, intents, or arbitrary device actions. */
class LocalAgentTools(private val context: Context) {
    fun execute(call: LocalToolCall): LocalToolResult = when (call.name) {
        "calculate" -> LocalToolResult("${call.argument} = ${LocalCalculator.evaluate(call.argument)}")
        "search_library" -> excerpts(LocalKnowledgeStore(context).search(call.argument))
        "search_inbox" -> {
            val entries = RememberInboxStore(context).items().map { item ->
                KnowledgeEntry(item.id, item.title,
                    (item.searchableText.ifBlank { item.summary }).take(3000), false)
            }
            excerpts(LocalKnowledgeStore.rank(call.argument, entries).map { it.copy(source = "Saved inbox · ${it.source}") })
        }
        "clock" -> {
            val zone = TimeZone.getDefault()
            val date = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).apply { timeZone = zone }
            LocalToolResult("Phone clock: ${date.format(Date())}. Timezone: ${zone.id}. Depends on the phone's clock setting.")
        }
        else -> error("This tool is not available on the phone.")
    }

    private fun excerpts(hits: List<KnowledgeHit>): LocalToolResult = if (hits.isEmpty())
        LocalToolResult("I couldn't find matching text in your saved items or local library. Try a more specific keyword, or save/import the item first. I haven't searched your other phone files or the web.", hasData = false)
    else LocalToolResult(hits.mapIndexed { i, hit -> "[${i + 1}] ${hit.source}\n${hit.text}" }.joinToString("\n\n"),
        hits.map { it.source })
}
