package cn.imaq.dstream

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.CaptureRequest
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Range
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import java.util.concurrent.TimeUnit

@ExperimentalCamera2Interop
@SuppressLint("ClickableViewAccessibility")
class LiveViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_view)

        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                startCameraPreview()
            }
        }.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        window.attributes.screenBrightness = 0f
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.systemBars())
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val previewView = findViewById<PreviewView>(R.id.previewView)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder).apply {
                this.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(60, 60))
                this.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
                if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    this.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    )
                } else {
                    this.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                }
            }
            val preview = previewBuilder.build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)

                val gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
                    var zoomState: LiveData<ZoomState>? = null

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (this.zoomState == null) {
                            this.zoomState = camera.cameraInfo.zoomState
                        }
                        when (this.zoomState?.value?.zoomRatio) {
                            1f -> camera.cameraControl.setZoomRatio(2f)
                            2f -> camera.cameraControl.setZoomRatio(4f)
                            else -> camera.cameraControl.setZoomRatio(1f)
                        }
                        return true
                    }
                })
                previewView.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    true
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open camera!", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
