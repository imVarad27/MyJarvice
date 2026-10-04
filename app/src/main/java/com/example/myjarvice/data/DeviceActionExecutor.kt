package com.example.myjarvice.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Executes phone-side directives from the JARVIS server: opening apps,
 * launching camera, navigation, placing calls, flashlight, alarms.
 */
class DeviceActionExecutor(private val context: Context) {

    /** Executes only an action accepted by [PhoneActionPolicy]. */
    fun executeLocalSafe(action: SafePhoneAction): Result<String> = runCatching {
        when (action.type) {
            "DEVICE_STATUS" -> {
                val values = DeviceContextProvider(context).getDeviceContext()
                "Battery ${values["battery_level"] ?: "unknown"} · " +
                    (if (values["is_charging"] == true) "charging" else "not charging") +
                    " · ${values["connection_type"] ?: "connection unknown"} · ${values["time"] ?: "time unavailable"}"
            }
            "FLASHLIGHT" -> { toggleFlashlight(action.query); "Flashlight command completed (${action.query})." }
            "OPEN_APP" -> { openApp(action.query); "Opening ${action.query}." }
            "NAVIGATE" -> { navigateTo(action.query); "Opening directions to ${action.query}." }
            "SET_ALARM" -> { setAlarm(action.query); "Opening the alarm confirmation for ${action.query}." }
            "SET_TIMER" -> { setTimer(action.query); "Opening the timer confirmation for ${action.query}." }
            "ADD_LOCAL_TASK" -> {
                LocalTaskStore(context).saveTask(title = action.query)
                "Added “${action.query}” to Your tasks on this phone."
            }
            "SHOW_LOCAL_TASKS" -> {
                val open = TaskAgenda.from(LocalTaskStore(context).tasks(), System.currentTimeMillis()).open
                if (open.isEmpty()) "Your phone task list is clear."
                else open.take(10).joinToString("\n") { "• ${it.title}${taskDueSuffix(it.dueAt)}" }
            }
            else -> error("This phone action is not allowlisted.")
        }
    }

    private fun taskDueSuffix(dueAt: Long?): String = dueAt?.let {
        " · due ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it))}"
    }.orEmpty()

    /** Common voice-name → package aliases for reliability. */
    private val appAliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "telegram" to "org.telegram.messenger",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "play store" to "com.android.vending",
        "calculator" to "com.google.android.calculator",
        "photos" to "com.google.android.apps.photos",
        "gallery" to "com.coloros.gallery3d"
    )

    fun execute(action: JarvisAction, approved: Boolean = false): Result<String> = runCatching {
        val validated = PhoneActionPolicy.validate(action.type, action.query) ?: error("Invalid phone action.")
        check(!validated.requiresConfirmation || approved) { "This phone action needs your explicit approval." }
        when (validated.type) {
            "CALL" -> { placeCall(validated.query); "Requested the approved call flow." }
            "WHATSAPP" -> { sendWhatsAppMessage(validated.query); "Opened the approved WhatsApp draft. Review it in WhatsApp before sending." }
            else -> executeLocalSafe(validated).getOrThrow()
        }
    }

    // --- Camera -----------------------------------------------------------
    private fun openCamera() {
        val pm = context.packageManager
        Log.i(TAG, "Triggering camera launch sequence...")

        // 1. Try standard camera capture intents
        val standardIntents = listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE")
        )
        for (intent in standardIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    Log.i(TAG, "Camera launched via standard intent: ${intent.action}")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standard camera intent failed: ${e.message}")
            }
        }

        // 2. Try OEM Camera package launch intents (Realme / Oppo / AOSP / Google)
        val cameraPkgs = listOf(
            "com.oplus.camera",
            "com.oppo.camera",
            "com.realme.camera",
            "com.android.camera",
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera"
        )
        for (pkg in cameraPkgs) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launch(launchIntent)
                return
            }
        }

        // 3. Fallback direct intent launch
        launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    // --- Maps & Navigation ------------------------------------------------
    private fun openMaps(destination: String = "") {
        val target = destination.trim()
        val pm = context.packageManager

        if (target.isNotBlank()) {
            val encoded = Uri.encode(target)
            val candidates = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).setPackage(MAPS_PACKAGE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded")),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).setPackage(MAPS_PACKAGE),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
            )
            for (intent in candidates) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent.resolveActivity(pm) != null) {
                        context.startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Navigation candidate failed: ${e.message}")
                }
            }
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded")))
            return
        }

        // Open Maps app directly
        val mapsIntent = pm.getLaunchIntentForPackage(MAPS_PACKAGE)
        if (mapsIntent != null) {
            launch(mapsIntent)
            return
        }

        // Fallback geo intent
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
        if (geoIntent.resolveActivity(pm) != null) {
            launch(geoIntent)
            return
        }
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")))
    }

    private fun navigateTo(destination: String) {
        openMaps(destination)
    }

    // --- Flashlight --------------------------------------------------------
    private var isTorchOn = false

    private fun toggleFlashlight(command: String) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId == null) {
                toast("No camera flash available")
                error("No camera flash available.")
            }
            val turnOn = command.lowercase().contains("on") || (!isTorchOn && !command.lowercase().contains("off"))
            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            toast("Flashlight ${if (turnOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
            toast("Flashlight control failed")
            throw e
        }
    }

    // --- Alarm ------------------------------------------------------------
    private fun setAlarm(timeQuery: String) {
        try {
            val alarm = ClockActionParameters.alarm(timeQuery)

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm: ${e.message}")
            toast("Failed to set alarm")
            throw e
        }
    }

    private fun setTimer(durationQuery: String) {
        try {
            val seconds = ClockActionParameters.timerSeconds(durationQuery)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting timer: ${e.message}")
            toast(e.message ?: "Failed to set timer")
            throw e
        }
    }

    // --- WhatsApp ---------------------------------------------------------
    private fun sendWhatsAppMessage(messageText: String) {
        try {
            val encodedText = Uri.encode(messageText)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=$encodedText")).apply {
                setPackage("com.whatsapp")
            }
            launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp: ${e.message}")
            toast("WhatsApp not available")
            throw e
        }
    }

    // --- Open app ---------------------------------------------------------
    private fun openApp(rawQuery: String) {
        val q = rawQuery.trim().lowercase()
        require(q.isNotBlank()) { "Supply an app name." }
        val pm = context.packageManager

        // 1) Specialized System Targets
        when {
            q == "camera" || q.contains("camera") || q.contains("photo") || q.contains("picture") -> {
                openCamera()
                return
            }
            q == "maps" || q.contains("maps") || q.contains("google maps") -> {
                openMaps()
                return
            }
            q == "settings" -> {
                launch(Intent(Settings.ACTION_SETTINGS))
                return
            }
        }

        // 2) Known alias package
        appAliases[q]?.let { pkg ->
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                launch(intent); return
            }
            Log.w(TAG, "Alias '$q' -> $pkg but that package is not installed")
        }

        // 3) Fuzzy-match against installed launchable apps by label
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(main, 0)
            val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            if (match != null) {
                pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let { launch(it); return }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query intent activities error: ${e.message}")
        }

        Log.w(TAG, "No installed app matched '$q'")
        toast("Couldn't find an app called \"$rawQuery\"")
        error("Couldn't find an installed app called $rawQuery.")
    }

    // --- Place call -------------------------------------------------------
    private fun placeCall(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) return

        val digits = query.filter { it.isDigit() || it == '+' }
        val looksLikeNumber = query.none { it.isLetter() } && digits.count { it.isDigit() } >= 3
        val number = if (looksLikeNumber) digits else resolveContactNumber(query)

        if (number.isNullOrBlank()) {
            toast("No number found for \"$rawQuery\"")
            error("No phone number found for $rawQuery.")
        }

        val canCallDirectly = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = Intent(
            if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:$number")
        )
        launch(intent)
    }

    private fun resolveContactNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf("%$name%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    // --- Helpers ----------------------------------------------------------
    private fun launch(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Launched ${intent.`package` ?: intent.data ?: intent.action}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch: ${e.message}", e)
            toast("Couldn't complete that action")
            throw e
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "JarvisAction"
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
