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

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, finalHour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
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

    // --- Open app ---------------------------------------------------------
    private fun openApp(rawQuery: String) {
        val q = rawQuery.trim().lowercase()
        if (q.isBlank()) return
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
            return
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
