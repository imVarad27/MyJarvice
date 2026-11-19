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
        "search_phone" -> {
            val hits = PhoneContextAggregator(context).search(call.argument)
            excerpts(hits)
        }
        "query_schedule" -> {
            val schedule = PhoneContextAggregator(context).querySchedule(call.argument)
            LocalToolResult(
                text = schedule,
                sources = listOf("Schedule · ${call.argument.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"),
                hasData = true
            )
        }
        "search_contacts" -> {
            val result = PhoneContextAggregator(context).searchContacts(call.argument)
            LocalToolResult(result, listOf("Contacts"), hasData = !result.startsWith("No contacts"))
        }
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
            val powerSaveStr = if (values["is_power_save"] == true) " (power saver on)" else ""
            val ringerStr = values["ringer_mode"]?.let { "; ringer: $it" } ?: ""
            val dndStr = if (values["dnd_active"] == true) "; DND: active" else ""
            LocalToolResult(
                "Battery: ${values["battery_level"] ?: "unknown"}; " +
                    "charging: ${values["is_charging"] ?: "unknown"}$powerSaveStr; " +
                    "connection: ${values["connection_type"] ?: "unknown"}$ringerStr$dndStr; " +
                    "storage: ${values["storage"] ?: "unknown"}; " +
                    "RAM: ${values["ram"] ?: "unknown"}; " +
                    "tasks: ${values["open_tasks_count"] ?: 0} open (${values["due_today_count"] ?: 0} due today); " +
                    "phone time: ${values["time"] ?: "unknown"}."
            )
        }
        "remember" -> {
            LocalKnowledgeStore(context).remember(call.argument)
            LocalToolResult("Saved this fact in private local memory: ${call.argument}")
        }
        "list_memories" -> {
            val memories = LocalKnowledgeStore(context).entries().filter { it.memory && it.enabled }
            LocalToolResult(
                memories.joinToString("\n") { "• ${it.text}" }.ifBlank { "No saved memories are enabled for phone-model use." },
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
        "open_dialer" -> executePhone("OPEN_DIALER", call.argument)
        "open_sms" -> executePhone("OPEN_SMS", call.argument)
        "list_apps" -> executePhone("LIST_APPS", if (call.argument.equals("all", ignoreCase = true)) "" else call.argument)
        "adjust_volume" -> executePhone("ADJUST_VOLUME", call.argument)
        "open_settings" -> executePhone("OPEN_SETTINGS", call.argument)
        "read_clipboard" -> executePhone("READ_CLIPBOARD", "")
        "copy_to_clipboard" -> executePhone("COPY_CLIPBOARD", call.argument)
        "open_url" -> executePhone("OPEN_URL", call.argument)
        "save_note" -> {
            val item = RememberInboxStore(context).addText(call.argument)
            LocalToolResult("Saved to private inbox: \"${item.title}\"")
        }
        "open_camera" -> executePhone("OPEN_CAMERA", "")
        "search_places" -> executePhone("SEARCH_PLACES", call.argument)
        "show_alarms" -> executePhone("SHOW_ALARMS", "")
        "show_timers" -> executePhone("SHOW_TIMERS", "")
        "complete_task" -> executePhone("COMPLETE_LOCAL_TASK", call.argument)
        "delete_task" -> executePhone("DELETE_LOCAL_TASK", call.argument)
        "battery_diagnostics" -> {
            val ctx = DeviceContextProvider(context).getDeviceContext()
            val level = ctx["battery_level"] ?: "unknown"
            val charging = if (ctx["is_charging"] == true) "Charging via ${ctx["battery_plugged"] ?: "charger"}" else "Discharging (unplugged)"
            val health = ctx["battery_health"] ?: "Normal"
            val temp = ctx["battery_temperature"] ?: "unknown"
            val powerSave = if (ctx["is_power_save"] == true) "On" else "Off"
            LocalToolResult("Battery: $level ($charging). Health: $health. Temperature: $temp. Power saver: $powerSave.")
        }
        "control_media" -> executePhone("CONTROL_MEDIA", call.argument)
        "set_ringer_mode" -> executePhone("SET_RINGER_MODE", call.argument)
        "open_whatsapp" -> executePhone("OPEN_WHATSAPP", call.argument)
        "web_search" -> executePhone("WEB_SEARCH", call.argument)
        "open_email" -> executePhone("OPEN_EMAIL", call.argument)
        "open_calendar_event" -> executePhone("OPEN_CALENDAR_EVENT", call.argument)
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
