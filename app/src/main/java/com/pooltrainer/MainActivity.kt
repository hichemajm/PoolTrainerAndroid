package com.pooltrainer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    private val captureRequest = 4102
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32,32,32,32); setBackgroundColor(Color.rgb(16,16,16)) }
        val title = TextView(this).apply { text = "POOL TRAINER"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val info = TextView(this).apply { text = "Automatic ball detection prototype\nScreen analysis + practice guides"; textSize = 15f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0,20,0,28) }
        val start = Button(this).apply { text = "Enable training overlay"; setOnClickListener { beginCaptureFlow() } }
        val stop = Button(this).apply { text = "Stop overlay"; setOnClickListener { stopService(Intent(this@MainActivity, CaptureOverlayService::class.java)) } }
        layout.addView(title); layout.addView(info); layout.addView(start); layout.addView(stop); setContentView(layout)
    }
    private fun beginCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }
    @Deprecated("Legacy activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureOverlayService::class.java).apply { putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode); putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data) }
            startForegroundService(service)
        }
    }
}
