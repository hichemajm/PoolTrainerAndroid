package com.pooltrainer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val captureRequest = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(10, 10, 12))
        }
        val title = TextView(this).apply {
            text = "POOL TRAINER"
            textSize = 27f
            setTextColor(Color.WHITE)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PRACTICE ASSISTANT  •  VERSION 0.2.0"
            textSize = 11f
            setTextColor(Color.rgb(0, 170, 230))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
        val description = TextView(this).apply {
            text = "Screen analysis is limited to the 8 Ball Pool app. The guide stays hidden on your home screen and in other apps."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(22))
        }
        val status = TextView(this).apply {
            tag = "status"
            textSize = 13f
            setTextColor(Color.rgb(210, 210, 210))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(25, 25, 29))
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(description)
        root.addView(status)
        root.addView(spacer(18))
        root.addView(actionButton("1. Allow display over other apps") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            }
        })
        root.addView(actionButton("2. Allow usage access") {
            if (!ForegroundGameGate.hasUsageAccess(this)) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(actionButton("3. Start screen analysis") {
            beginCaptureFlow()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        root.addView(actionButton("Stop training overlay") {
            stopService(Intent(this, CaptureOverlayService::class.java))
        })
        root.addView(actionButton("Open 8 Ball Pool") {
            val launch = packageManager.getLaunchIntentForPackage(ForegroundGameGate.GAME_PACKAGE)
            if (launch != null) startActivity(launch)
            else showMessage("8 Ball Pool was not found. Install it from Google Play first.")
        })
        val note = TextView(this).apply {
            text = "Important: detection is experimental and may need calibration for your screen layout. Use for offline practice only. It does not tap, move the cue or automate shots."
            textSize = 12f
            setTextColor(Color.rgb(145, 145, 150))
            setPadding(dp(4), dp(22), dp(4), 0)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refreshStatus() {
        val status = findViewWithTag(window.decorView, "status") as? TextView ?: return
        val overlay = Settings.canDrawOverlays(this)
        val usage = ForegroundGameGate.hasUsageAccess(this)
        status.text = "Overlay permission: " + (if (overlay) "READY" else "REQUIRED") +
            "\nUsage access: " + (if (usage) "READY" else "REQUIRED") +
            "\nTarget game: 8 Ball Pool"
    }

    private fun findViewWithTag(view: View, tag: Any): View? {
        if (view.tag == tag) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findViewWithTag(view.getChildAt(i), tag)
                if (found != null) return found
            }
        }
        return null
    }

    private fun beginCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            showMessage("First allow display over other apps.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        if (!ForegroundGameGate.hasUsageAccess(this)) {
            showMessage("First allow usage access so the overlay can stay hidden outside the game.")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Legacy activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, service)
            showMessage("Screen analysis started. Open 8 Ball Pool; the guide appears only there.")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(42, 42, 48))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) }
        }
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
