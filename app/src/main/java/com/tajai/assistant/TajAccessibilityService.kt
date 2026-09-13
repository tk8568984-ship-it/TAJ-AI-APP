package com.tajai.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * This is TAJ's "hands". It can:
 *  - open any installed app by name, or search+play something inside YouTube
 *  - place a call (falls back to opening the dialer pre-filled if direct-call
 *    permission isn't granted, so it never silently does nothing)
 *  - type text into whatever text field is currently focused (e.g. a WhatsApp chat box)
 *  - tap a button by its visible text/description (e.g. the WhatsApp "Send" button)
 *  - toggle the flashlight
 *  - open the system Wi-Fi panel (Android does not allow silently toggling Wi-Fi
 *    programmatically since Android 10 — this opens the quick panel instead)
 *
 * IMPORTANT: automating taps inside third-party apps (WhatsApp, Instagram, etc.) depends on
 * the internal screen layout of THAT app. If WhatsApp changes its UI in an update, the
 * "find and tap Send" step may need to be adjusted here.
 */
class TajAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TajAccessibility"
        var instance: TajAccessibilityService? = null
    }

    private var flashlightOn = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TAJ accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reserved for future use: e.g. detecting which app/screen is currently open,
        // so TAJ can give context-aware trading commentary without the user saying
        // which app they're looking at.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ---------- App launching ----------

    fun openApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    /** Opens YouTube directly on the search results for [query] (e.g. a song name/singer). */
    fun searchYoutube(query: String) {
        try {
            val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.youtube")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "searchYoutube failed, falling back to browser", e)
            try {
                val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Throwable) {
                Log.e(TAG, "searchYoutube fallback also failed", e2)
            }
        }
    }

    // ---------- Contacts lookup (call/message someone by NAME, not just number) ----------

    /**
     * Looks up a saved contact's phone number by name (partial match, case-insensitive).
     * Returns null if nothing matches — caller should then ask the user for the number
     * instead of guessing.
     */
    fun resolveContactNumber(name: String): String? {
        return try {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    if (numberIndex >= 0) cursor.getString(numberIndex)?.replace(" ", "") else null
                } else null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "resolveContactNumber failed — is READ_CONTACTS permission granted?", e)
            null
        }
    }

    // ---------- Calling ----------

    /**
     * Tries to place the call directly. If the CALL_PHONE permission isn't granted
     * (SecurityException) it falls back to opening the dialer with the number
     * pre-filled — the user just has to tap the call button themselves, but it never
     * silently fails without doing anything.
     */
    fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Direct call failed (permission?), opening dialer instead", e)
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Throwable) {
                Log.e(TAG, "Dialer fallback also failed", e2)
            }
        }
    }

    // ---------- WhatsApp message (open chat pre-filled + auto-tap send) ----------

    fun sendWhatsAppMessage(phoneNumberWithCountryCode: String, message: String) {
        val uri = Uri.parse(
            "https://wa.me/$phoneNumberWithCountryCode?text=${Uri.encode(message)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Give WhatsApp a moment to open the chat, then try to tap "Send".
        // This is best-effort: it depends on WhatsApp's current UI.
        android.os.Handler(mainLooper).postDelayed({
            tapButtonByDescription("Send")
        }, 3500)
    }

    // ---------- Flashlight ----------

    fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            flashlightOn = !flashlightOn
            cameraManager.setTorchMode(camId, flashlightOn)
        } catch (e: Throwable) {
            Log.e(TAG, "toggleFlashlight failed", e)
        }
    }

    // ---------- Generic screen actions ----------

    /** Types text into whatever EditText is currently focused on screen. */
    fun typeIntoFocusedField(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Finds a button/view by its visible text or content-description and taps it. */
    fun tapButtonByDescription(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, label) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true ||
            root.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
        ) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    fun openWifiPanel() {
        val intent = Intent(Settings.Panel.ACTION_WIFI)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun openSettingsScreen(action: String = Settings.ACTION_SETTINGS) {
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
