package com.example.myjarvice.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Executes phone-side directives from the JARVIC server (Phase 3): opening apps
 * and placing calls. All launches use FLAG_ACTIVITY_NEW_TASK because they may be
 * started from a non-Activity context.
 */
class DeviceActionExecutor(private val context: Context) {

    /** Common voice-name → package aliases for reliability when label matching is ambiguous. */
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
        "play store" to "com.android.vending"
    )

    fun execute(action: JarvisAction) {
        Log.i(TAG, "Executing ${action.type} -> '${action.query}'")

        when (action.type.uppercase()) {
            "CALL" -> placeCall(action.query)
            "OPEN_APP" -> openApp(action.query)
            "NAVIGATE" -> navigateTo(action.query)
            "FLASHLIGHT" -> toggleFlashlight(action.query)
            "SET_ALARM" -> setAlarm(action.query)
            "WHATSAPP" -> sendWhatsAppMessage(action.query)
            else -> Log.w(TAG, "Unknown action type: ${action.type}")
        }
    }

    // --- Flashlight --------------------------------------------------------
    private var isTorchOn = false

    private fun toggleFlashlight(command: String) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId == null) {
                toast("No camera flash available")
                return
            }
            val turnOn = command.lowercase().contains("on") || (!isTorchOn && !command.lowercase().contains("off"))
            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            toast("Flashlight ${if (turnOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
            toast("Flashlight control failed")
        }
    }

    // --- Alarm ------------------------------------------------------------
    private fun setAlarm(timeQuery: String) {
        try {
            val hour = Regex("""(\d{1,2})""").find(timeQuery)?.groupValues?.get(1)?.toIntOrNull() ?: 7
            val isPm = timeQuery.lowercase().contains("pm")
            val finalHour = if (isPm && hour < 12) hour + 12 else if (!isPm && hour == 12) 0 else hour
            val minute = Regex(""":(\d{2})""").find(timeQuery)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, finalHour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm")
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            }
            launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm: ${e.message}")
            toast("Failed to set alarm")
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
        }
    }

    // --- Navigate ---------------------------------------------------------
    /**
     * Starts turn-by-turn navigation to [destination] from wherever the phone is.
     *
     * This needs no Maps API key and no billing account: `google.navigation:` is a
     * plain intent handled by the installed Maps app, which does the routing itself.
     * A key would only be required to compute routes inside our own process.
     */
    private fun navigateTo(destination: String) {
        val target = destination.trim()
        if (target.isBlank()) return
        val encoded = Uri.encode(target)

        // Turn-by-turn in Google Maps, then plain map search, then any maps handler.
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                .setPackage(MAPS_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                .setPackage(MAPS_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        )

        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) != null) {
                Log.i(TAG, "Navigating to '$target' via ${intent.data}")
                launch(intent)
                return
            }
        }
        toast("No maps app available to navigate to \"$target\"")
    }

    // --- Open app ---------------------------------------------------------
    private fun openApp(rawQuery: String) {
        val q = rawQuery.trim().lowercase()
        if (q.isBlank()) return
        val pm = context.packageManager

        // 1) Known alias package
        appAliases[q]?.let { pkg ->
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                launch(intent); return
            }
            Log.w(TAG, "Alias '$q' -> $pkg but that package is not installed")
        }

        // 2) System destinations without a normal launcher entry
        when (q) {
            "settings" -> { launch(Intent(Settings.ACTION_SETTINGS)); return }
            "camera" -> { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); return }
        }

        // 3) Fuzzy-match against installed launchable apps by label
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        if (match != null) {
            pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let { launch(it); return }
        }

        Log.w(TAG, "No installed app matched '$q' (searched ${apps.size} launchable apps)")
        toast("Couldn't find an app called \"$rawQuery\"")
    }

    // --- Place call -------------------------------------------------------
    private fun placeCall(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) return

        // A raw phone number (mostly digits) is dialed directly; otherwise resolve a contact.
        val digits = query.filter { it.isDigit() || it == '+' }
        val looksLikeNumber = query.none { it.isLetter() } && digits.count { it.isDigit() } >= 3
        val number = if (looksLikeNumber) digits else resolveContactNumber(query)

        if (number.isNullOrBlank()) {
            toast("No number found for \"$rawQuery\"")
            return
        }

        val canCallDirectly = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        // With permission → place the call directly; otherwise open the dialer pre-filled.
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
