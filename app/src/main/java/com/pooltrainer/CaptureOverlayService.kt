package com.pooltrainer

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer

class CaptureOverlayService : Service() {
    companion object { const val EXTRA_RESULT_CODE="result_code"; const val EXTRA_RESULT_DATA="result_data"; private const val CHANNEL="pool_trainer_capture"; private const val NOTIFICATION_ID=71 }
    private var projection: MediaProjection?=null; private var virtualDisplay: VirtualDisplay?=null; private var reader: ImageReader?=null; private var overlay: GuideOverlay?=null; private var windowManager: WindowManager?=null; private var worker: Handler?=null; private var workerThread: HandlerThread?=null; private var lastAnalysis=0L
    override fun onCreate() {
        super.onCreate(); createNotificationChannel()
        val notification=NotificationCompat.Builder(this,CHANNEL).setContentTitle("Pool Trainer is running").setContentText("Screen analysis is active").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()
        if (Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIFICATION_ID,notification)
        OpenCVLoader.initLocal(); workerThread=HandlerThread("pool-screen-analysis").also{it.start()}; worker=Handler(workerThread!!.looper)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code=intent?.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if(data==null)return START_NOT_STICKY
        val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=manager.getMediaProjection(code,data)
        projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},worker)
        startCapture(); return START_NOT_STICKY
    }
    private fun startCapture() {
        val metrics=DisplayMetrics(); @Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width=metrics.widthPixels; val height=metrics.heightPixels
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
        reader?.setOnImageAvailableListener({ source ->
            val image=source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { if(System.currentTimeMillis()-lastAnalysis>=350) { lastAnalysis=System.currentTimeMillis(); val bitmap=imageToBitmap(image); val detections=BallDetector.detect(bitmap); overlay?.post{overlay?.setDetections(detections)}; bitmap.recycle() } } catch (_:Exception) {} finally { image.close() }
        },worker)
        virtualDisplay=projection?.createVirtualDisplay("PoolTrainerCapture",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,worker)
        windowManager=getSystemService(WINDOW_SERVICE) as WindowManager; overlay=GuideOverlay(this)
        val params=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START}
        windowManager?.addView(overlay,params)
    }
    private fun imageToBitmap(image: Image): Bitmap {
        val plane=image.planes[0]; val buffer:ByteBuffer=plane.buffer; val pixelStride=plane.pixelStride; val rowPadding=plane.rowStride-pixelStride*image.width
        val padded=Bitmap.createBitmap(image.width+rowPadding/pixelStride,image.height,Bitmap.Config.ARGB_8888); padded.copyPixelsFromBuffer(buffer)
        val result=Bitmap.createBitmap(padded,0,0,image.width,image.height); padded.recycle(); return result
    }
    private fun createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){val channel=NotificationChannel(CHANNEL,"Pool Trainer",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(channel)}}
    override fun onDestroy(){try{windowManager?.removeView(overlay)}catch(_:Exception){};virtualDisplay?.release();reader?.close();projection?.stop();workerThread?.quitSafely();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
