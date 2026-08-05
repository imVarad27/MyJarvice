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

    fun execute(action: JarviceAction) {
        when (action.type.uppercase()) {
            "CALL" -> placeCall(action.query)
            "OPEN_APP" -> openApp(action.query)
            else -> Log.w("JarviceAction", "Unknown action type: ${action.type}")
        }
    }

    // --- Open app ---------------------------------------------------------
    private fun openApp(rawQuery: String) {
        val q = rawQuery.trim().lowercase()
        if (q.isBlank()) return
        val pm = context.packageManager

        // 1) Known alias package
        appAliases[q]?.let { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { launch(it); return }
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
        } catch (e: Exception) {
            Log.e("JarviceAction", "Failed to launch: ${e.message}", e)
            toast("Couldn't complete that action")
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
