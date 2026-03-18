package com.rnusbcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.usb.USBMonitor

class RnUsbCameraView(private val reactContext: ThemedReactContext) : FrameLayout(reactContext) {

    private var textureView: PreviewTextureView? = null
    private var multiCameraClient: MultiCameraClient? = null
    private var camera: MultiCameraClient.ICamera? = null
    private val cameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previewRotation: RotateType = RotateType.ANGLE_0
    private var resizeMode: PreviewResizeMode = PreviewResizeMode.COVER

    var previewWidth: Int = 640
    var previewHeight: Int = 480
    var autoOpen: Boolean = true

    fun getTextureView(): PreviewTextureView? = textureView

    init {
        textureView = PreviewTextureView(reactContext).apply {
            this.resizeMode = this@RnUsbCameraView.resizeMode
        }
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        })
        updatePreviewAspectRatio()
        setupTextureListener()
    }

    fun updatePreviewWidth(width: Int) {
        previewWidth = width
        updatePreviewAspectRatio()
    }

    fun updatePreviewHeight(height: Int) {
        previewHeight = height
        updatePreviewAspectRatio()
    }

    fun setPreviewRotation(rotation: String?) {
        previewRotation = parseRotateType(rotation)
        updatePreviewAspectRatio()
        camera?.setRotateType(previewRotation)
    }

    fun setResizeMode(mode: String?) {
        resizeMode = PreviewResizeMode.fromJsValue(mode)
        textureView?.resizeMode = resizeMode
        updatePreviewAspectRatio()
    }

    private fun setupTextureListener() {
        textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                registerUsbMonitor()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                camera?.setRenderSize(width, height)
                updatePreviewAspectRatio()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                unregisterUsbMonitor()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun registerUsbMonitor() {
        multiCameraClient = MultiCameraClient(reactContext, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                if (cameraMap.containsKey(device.deviceId)) return

                cameraMap[device.deviceId] = CameraUVC(reactContext, device)

                sendEvent("onDeviceAttached", Arguments.createMap().apply {
                    putInt("deviceId", device.deviceId)
                    putInt("vendorId", device.vendorId)
                    putInt("productId", device.productId)
                    putString("deviceName", device.deviceName)
                })

                if (autoOpen) {
                    multiCameraClient?.requestPermission(device)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                device ?: return
                cameraMap.remove(device.deviceId)?.setUsbControlBlock(null)

                sendEvent("onDeviceDetached", Arguments.createMap().apply {
                    putInt("deviceId", device.deviceId)
                })
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return

                cameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                    setCameraStateCallBack(object : ICameraStateCallBack {
                        override fun onCameraState(
                            self: MultiCameraClient.ICamera,
                            code: ICameraStateCallBack.State,
                            msg: String?
                        ) {
                            mainHandler.post {
                                when (code) {
                                    ICameraStateCallBack.State.OPENED -> {
                                        // Re-set CameraHolder on every open (including after updateResolution)
                                        camera = self
                                        CameraHolder.camera = self
                                        CameraHolder.cameraView = this@RnUsbCameraView
                                        sendEvent("onCameraOpened", Arguments.createMap())
                                    }
                                    ICameraStateCallBack.State.CLOSED -> {
                                        sendEvent("onCameraClosed", Arguments.createMap())
                                    }
                                    ICameraStateCallBack.State.ERROR -> {
                                        sendEvent("onError", Arguments.createMap().apply {
                                            putString("message", msg ?: "Unknown error")
                                        })
                                    }
                                }
                            }
                        }
                    })

                    val request = CameraRequest.Builder()
                        .setPreviewWidth(previewWidth)
                        .setPreviewHeight(previewHeight)
                        .setRenderMode(CameraRequest.RenderMode.OPENGL)
                        .setDefaultRotateType(previewRotation)
                        .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
                        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                        .create()

                    openCamera(textureView, request)
                    setRotateType(previewRotation)
                    camera = this
                    CameraHolder.camera = this
                    CameraHolder.cameraView = this@RnUsbCameraView
                    CameraHolder.client = multiCameraClient
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                closeCurrentCamera()
                clearCameraState()
            }

            override fun onCancelDev(device: UsbDevice?) {
                // User cancelled USB permission
            }
        })
        multiCameraClient?.register()
    }

    private fun closeCurrentCamera() {
        camera?.closeCamera()
        // Don't null out camera/CameraHolder here — updateResolution calls
        // closeCamera() internally then re-opens. We only clear on disconnect.
    }

    /** Called when USB device is fully disconnected */
    private fun clearCameraState() {
        camera = null
        CameraHolder.camera = null
        CameraHolder.cameraView = null
    }

    private fun unregisterUsbMonitor() {
        cameraMap.values.forEach { it.closeCamera() }
        cameraMap.clear()
        clearCameraState()
        CameraHolder.client = null
        multiCameraClient?.unRegister()
        multiCameraClient?.destroy()
        multiCameraClient = null
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactContext.getJSModule(RCTEventEmitter::class.java)
            ?.receiveEvent(id, eventName, params)
    }

    fun onDropView() {
        unregisterUsbMonitor()
    }

    fun sendLoadingEvent() {
        mainHandler.post {
            sendEvent("onCameraLoading", Arguments.createMap())
        }
    }

    fun openCameraFromModule() {
        val cam = camera ?: cameraMap.values.firstOrNull() ?: return
        val request = CameraRequest.Builder()
            .setPreviewWidth(previewWidth)
            .setPreviewHeight(previewHeight)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(previewRotation)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .create()
        cam.openCamera(textureView, request)
        cam.setRotateType(previewRotation)
        camera = cam
        CameraHolder.camera = cam
    }

    private fun updatePreviewAspectRatio() {
        val view = textureView ?: return
        val shouldSwapDimensions = previewRotation == RotateType.ANGLE_90 || previewRotation == RotateType.ANGLE_270
        val aspectWidth = if (shouldSwapDimensions) previewHeight else previewWidth
        val aspectHeight = if (shouldSwapDimensions) previewWidth else previewHeight
        view.resizeMode = resizeMode
        view.setAspectRatio(aspectWidth, aspectHeight)
        view.requestLayout()
    }

    private fun parseRotateType(rotation: String?): RotateType {
        return when (rotation) {
            "90" -> RotateType.ANGLE_90
            "180" -> RotateType.ANGLE_180
            "270" -> RotateType.ANGLE_270
            "flipVertical" -> RotateType.FLIP_UP_DOWN
            "flipHorizontal" -> RotateType.FLIP_LEFT_RIGHT
            else -> RotateType.ANGLE_0
        }
    }

    fun getCamera(): MultiCameraClient.ICamera? = camera
    fun getClient(): MultiCameraClient? = multiCameraClient
}
