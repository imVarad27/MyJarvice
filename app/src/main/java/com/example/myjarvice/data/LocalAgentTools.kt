package com.example.myjarvice.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Executes only schema-validated tools from [LocalAgentHarness]. */
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
        "phone_status" -> {
            val values = DeviceContextProvider(context).getDeviceContext()
            LocalToolResult(
                "Battery: ${values["battery_level"] ?: "unknown"}; " +
                    "charging: ${values["is_charging"] ?: "unknown"}; " +
                    "connection: ${values["connection_type"] ?: "unknown"}; " +
                    "phone time: ${values["time"] ?: "unknown"}."
            )
        }
        "remember" -> {
            LocalKnowledgeStore(context).remember(call.argument)
            LocalToolResult("Saved this fact in private local memory: ${call.argument}")
        }
        "list_memories" -> {
            val memories = LocalKnowledgeStore(context).entries().filter { it.memory }
            LocalToolResult(
                memories.joinToString("\n") { "• ${it.text}" }.ifBlank { "No facts are saved in local memory." },
                hasData = memories.isNotEmpty()
            )
        }
        "add_task" -> executePhone("ADD_LOCAL_TASK", call.argument)
        "list_tasks" -> executePhone("SHOW_LOCAL_TASKS", "")
        "open_app" -> executePhone("OPEN_APP", call.argument)
        "navigate" -> executePhone("NAVIGATE", call.argument)
        "flashlight" -> executePhone("FLASHLIGHT", call.argument)
        "set_alarm" -> executePhone("SET_ALARM", call.argument)
        "set_timer" -> executePhone("SET_TIMER", call.argument)
        else -> error("This tool is not available on the phone.")
    }

    private fun executePhone(type: String, argument: String): LocalToolResult {
        val action = PhoneActionPolicy.validate(type, argument)
            ?: error("The model proposed an invalid phone action.")
        check(!action.requiresConfirmation) { "This action needs confirmation and is unavailable in the local tool loop." }
        return LocalToolResult(DeviceActionExecutor(context).executeLocalSafe(action).getOrThrow())
    }

    private fun excerpts(hits: List<KnowledgeHit>): LocalToolResult = if (hits.isEmpty())
        LocalToolResult("I couldn't find matching text in your saved items or local library. Try a more specific keyword, or save/import the item first. I haven't searched your other phone files or the web.", hasData = false)
    else LocalToolResult(hits.mapIndexed { i, hit -> "[${i + 1}] ${hit.source}\n${hit.text}" }.joinToString("\n\n"),
        hits.map { it.source })
}
