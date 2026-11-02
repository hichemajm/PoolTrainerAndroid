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
    private lateinit var status: TextView

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
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(8, 8, 9))
        }
        val brand = TextView(this).apply {
            text = "8"
            textSize = 46f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.rgb(20, 105, 62))
                setStroke(dp(3), Color.rgb(180, 28, 36))
            }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(14)
            }
        }
        val title = TextView(this).apply {
            text = "AIM TOOL FOR 8 BALL"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.045f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PRACTICE ASSISTANT  •  VERSION 1.0.0"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(205, 45, 51))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
        val description = TextView(this).apply {
            text = "Automatic aiming guides for 8 Ball Pool. Screen analysis runs locally and only after your permission."
            textSize = 14f
            setTextColor(Color.rgb(195, 195, 198))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(20))
        }
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(225, 225, 225))
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = panel()
        }
        root.addView(brand)
        root.addView(title)
        root.addView(subtitle)
        root.addView(description)
        root.addView(status)
        root.addView(spacer(18))
        root.addView(sectionLabel("PERMISSIONS"))
        root.addView(actionButton("Allow display over other apps") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            }
        })
        root.addView(actionButton("Allow usage access") {
            if (!ForegroundGameGate.hasUsageAccess(this)) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(spacer(12))
        root.addView(sectionLabel("AIM ASSIST"))
        root.addView(actionButton("START AIM GUIDE") { beginCaptureFlow() }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(7) })
        root.addView(actionButton("STOP AIM GUIDE") {
            stopService(Intent(this, CaptureOverlayService::class.java))
            toast("Aim guide stopped.")
        })
        root.addView(actionButton("OPEN 8 BALL POOL") {
            val launch = packageManager.getLaunchIntentForPackage(ForegroundGameGate.GAME_PACKAGE)
            if (launch != null) startActivity(launch)
            else toast("8 Ball Pool was not found on this device.")
        })
        val note = TextView(this).apply {
            text = "Detection is experimental. Ball recognition and bank-shot paths must be calibrated against real practice-game screenshots. This app never controls the cue or plays shots for you."
            textSize = 11f
            setTextColor(Color.rgb(130, 130, 135))
            setPadding(dp(4), dp(22), dp(4), 0)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        status.text = "OVERLAY PERMISSION     ${if (Settings.canDrawOverlays(this)) "READY" else "REQUIRED"}" +
            "\nUSAGE ACCESS                 ${if (ForegroundGameGate.hasUsageAccess(this)) "READY" else "REQUIRED"}" +
            "\nTARGET                                  8 BALL POOL"
    }

    private fun beginCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow display over other apps first.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        if (!ForegroundGameGate.hasUsageAccess(this)) {
            toast("Allow usage access first so the overlay can stay hidden outside the game.")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Legacy activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, CaptureOverlayService::class.java).apply {
                    putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data)
                }
                ContextCompat.startForegroundService(this, service)
                toast("Screen analysis started. Open 8 Ball Pool.")
            } else {
                toast("Screen capture was not allowed.")
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
    }

    private fun actionButton(label: String, action: () -> Unit, params: LinearLayout.LayoutParams? = null): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(if (label == "START AIM GUIDE") Color.rgb(145, 22, 30) else Color.rgb(30, 30, 33))
                setStroke(dp(1), Color.rgb(54, 54, 58))
            }
            setOnClickListener { action() }
            layoutParams = params ?: LinearLayout.LayoutParams(-1, dp(49)).apply { topMargin = dp(7) }
        }
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 10f
        letterSpacing = 0.13f
        setTextColor(Color.rgb(160, 160, 164))
        setPadding(dp(3), dp(5), 0, dp(4))
    }

    private fun panel() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(Color.rgb(24, 24, 27))
        setStroke(dp(1), Color.rgb(47, 47, 51))
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
