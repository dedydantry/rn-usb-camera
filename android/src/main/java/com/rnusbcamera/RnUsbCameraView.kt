package com.rnusbcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
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
import kotlin.math.abs

class RnUsbCameraView(private val reactContext: ThemedReactContext) : FrameLayout(reactContext) {

    companion object {
        private const val TAG = "RnUsbCameraView"
        private const val REFRESH_HEALTH_CHECK_DELAY_MS = 2500L
    }

    private var textureView: PreviewTextureView? = null
    private var multiCameraClient: MultiCameraClient? = null
    private var camera: MultiCameraClient.ICamera? = null
    private val cameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previewRotation: RotateType = RotateType.ANGLE_0
    private var manualPreviewRotation: RotateType? = null
    private var liveViewMirrorEnabled: Boolean = false
    private var captureMirrorEnabled: Boolean = false
    private var resizeMode: PreviewResizeMode = PreviewResizeMode.COVER
    private var reopenPreviewRunnable: Runnable? = null
    private var isPreviewRefreshInProgress: Boolean = false
    private var isShuttingDown: Boolean = false
    private var refreshHealthCheckRunnable: Runnable? = null
    private val displayManager = reactContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private var isDisplayListenerRegistered = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (manualPreviewRotation != null) return
            val currentDisplay = resolveDisplay() ?: return
            if (currentDisplay.displayId != displayId) return
            Log.d(TAG, "onDisplayChanged displayId=$displayId rotation=${currentDisplay.rotation}")
            applyPreviewRotation(getDisplayRotateType(currentDisplay), true)
        }
    }

    private var requestedPreviewWidth: Int = 640
    private var requestedPreviewHeight: Int = 480
    private var activePreviewWidth: Int = 640
    private var activePreviewHeight: Int = 480
    var autoOpen: Boolean = true

    fun getTextureView(): PreviewTextureView? = textureView

    init {
        textureView = PreviewTextureView(reactContext).apply {
            this.resizeMode = this@RnUsbCameraView.resizeMode
        }
        applyLiveViewMirror()
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        })
        updateAutoPreviewRotation(false)
        updatePreviewAspectRatio()
        setupTextureListener()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isShuttingDown = false
        Log.d(TAG, "onAttachedToWindow")
        registerDisplayListener()
        updateAutoPreviewRotation(false)
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow")
        unregisterDisplayListener()
        super.onDetachedFromWindow()
    }

    fun updatePreviewWidth(width: Int) {
        requestedPreviewWidth = width
        if (CameraHolder.getPreferredPreviewSize() == null && camera == null) {
            activePreviewWidth = width
        }
        updatePreviewAspectRatio()
    }

    fun updatePreviewHeight(height: Int) {
        requestedPreviewHeight = height
        if (CameraHolder.getPreferredPreviewSize() == null && camera == null) {
            activePreviewHeight = height
        }
        updatePreviewAspectRatio()
    }

    fun onResolutionUpdated(width: Int, height: Int) {
        applyActivePreviewSize(width, height, rememberPreference = true)
    }

    fun setPreviewRotation(rotation: String?) {
        Log.d(TAG, "setPreviewRotation rotation=$rotation")
        if (rotation.isNullOrBlank() || rotation == "auto") {
            manualPreviewRotation = null
            updateAutoPreviewRotation(true)
            return
        }

        manualPreviewRotation = parseRotateType(rotation)
        applyPreviewRotation(manualPreviewRotation ?: RotateType.ANGLE_0, true)
    }

    fun setLiveViewMirror(enabled: Boolean) {
        if (liveViewMirrorEnabled == enabled) return
        liveViewMirrorEnabled = enabled
        applyLiveViewMirror()
    }

    fun setCaptureMirror(enabled: Boolean) {
        captureMirrorEnabled = enabled
    }

    fun shouldMirrorCapture(): Boolean = captureMirrorEnabled

    fun setResizeMode(mode: String?) {
        resizeMode = PreviewResizeMode.fromJsValue(mode)
        textureView?.resizeMode = resizeMode
        updatePreviewAspectRatio()
    }

    private fun applyLiveViewMirror() {
        textureView?.scaleX = if (liveViewMirrorEnabled) -1f else 1f
        textureView?.scaleY = 1f
    }

    private fun setupTextureListener() {
        textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceTextureAvailable width=$width height=$height")
                registerDisplayListener()
                updateAutoPreviewRotation(false)
                reopenCameraForSurface()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceTextureSizeChanged width=$width height=$height")
                camera?.setRenderSize(width, height)
                updatePreviewAspectRatio()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d(TAG, "onSurfaceTextureDestroyed")
                releasePreviewSurface()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun registerUsbMonitor() {
        if (multiCameraClient != null) return

        Log.d(TAG, "registerUsbMonitor")

        // Destroy module-level client so it doesn't hold exclusive USB access
        CameraHolder.moduleClient?.let { moduleClient ->
            Log.d(TAG, "registerUsbMonitor: destroying moduleClient to release USB")
            runCatching { moduleClient.unRegister() }
            runCatching { moduleClient.destroy() }
            CameraHolder.moduleClient = null
        }

        multiCameraClient = MultiCameraClient(reactContext, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                if (isShuttingDown) return
                if (cameraMap.containsKey(device.deviceId)) return

                Log.d(TAG, "onAttachDev deviceId=${device.deviceId}")

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
                Log.d(TAG, "onDetachDec deviceId=${device.deviceId}")
                cameraMap.remove(device.deviceId)?.setUsbControlBlock(null)

                sendEvent("onDeviceDetached", Arguments.createMap().apply {
                    putInt("deviceId", device.deviceId)
                })
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                if (isShuttingDown) return

                Log.d(TAG, "onConnectDev deviceId=${device.deviceId}")

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
                                        Log.d(TAG, "cameraState OPENED")
                                        isPreviewRefreshInProgress = false
                                        clearRefreshHealthCheck()
                                        // Re-set CameraHolder on every open (including after updateResolution)
                                        camera = self
                                        CameraHolder.camera = self
                                        CameraHolder.cameraView = this@RnUsbCameraView
                                        syncPreviewSizeFromCamera(self)
                                        sendEvent("onCameraOpened", Arguments.createMap())
                                    }
                                    ICameraStateCallBack.State.CLOSED -> {
                                        Log.d(TAG, "cameraState CLOSED")
                                        sendEvent("onCameraClosed", Arguments.createMap())
                                    }
                                    ICameraStateCallBack.State.ERROR -> {
                                        Log.e(TAG, "cameraState ERROR msg=${msg ?: "Unknown error"}")
                                        isPreviewRefreshInProgress = false
                                        clearRefreshHealthCheck()
                                        sendEvent("onError", Arguments.createMap().apply {
                                            putString("message", msg ?: "Unknown error")
                                        })
                                    }
                                }
                            }
                        }
                    })

                    val request = buildCameraRequest(this)

                    Log.d(TAG, "openCamera from onConnectDev preview=${request.previewWidth}x${request.previewHeight} rotation=$previewRotation")
                    openCamera(textureView, request)
                    setRotateType(previewRotation)
                    camera = this
                    CameraHolder.camera = this
                    CameraHolder.cameraView = this@RnUsbCameraView
                    CameraHolder.client = multiCameraClient
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.d(TAG, "onDisConnectDec deviceId=${device?.deviceId}")
                if (shouldIgnoreDisconnectCallback(device)) {
                    Log.d(TAG, "onDisConnectDec ignored during preview refresh")
                    return
                }
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
        Log.d(TAG, "closeCurrentCamera hasCamera=${camera != null}")
        camera?.closeCamera()
        // Don't null out camera/CameraHolder here — updateResolution calls
        // closeCamera() internally then re-opens. We only clear on disconnect.
    }

    private fun releasePreviewSurface() {
        Log.d(TAG, "releasePreviewSurface")
        if (isShuttingDown) {
            isPreviewRefreshInProgress = false
        } else {
            isPreviewRefreshInProgress = true
        }
        clearRefreshHealthCheck()
        reopenPreviewRunnable?.let(mainHandler::removeCallbacks)
        reopenPreviewRunnable = null
        closeCurrentCamera()
        clearCameraReferences()
    }

    /** Called when USB device is fully disconnected */
    private fun clearCameraState() {
        clearCameraReferences()
        cameraMap.clear()
    }

    private fun clearCameraReferences() {
        camera = null
        CameraHolder.camera = null
        CameraHolder.cameraView = null
    }

    private fun unregisterUsbMonitor() {
        Log.d(TAG, "unregisterUsbMonitor")
        isShuttingDown = true
        isPreviewRefreshInProgress = false
        clearRefreshHealthCheck()
        reopenPreviewRunnable?.let(mainHandler::removeCallbacks)
        reopenPreviewRunnable = null
        unregisterDisplayListener()

        camera?.let { currentCamera ->
            runCatching { currentCamera.closeCamera() }
            runCatching { currentCamera.setUsbControlBlock(null) }
        }

        cameraMap.values.forEach { targetCamera ->
            runCatching { targetCamera.closeCamera() }
            runCatching { targetCamera.setUsbControlBlock(null) }
        }

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
        isShuttingDown = true
        unregisterUsbMonitor()
    }

    fun sendLoadingEvent() {
        mainHandler.post {
            sendEvent("onCameraLoading", Arguments.createMap())
        }
    }

    fun openCameraFromModule() {
        val cam = camera ?: cameraMap.values.firstOrNull() ?: return
        val request = buildCameraRequest(cam)
        Log.d(TAG, "openCameraFromModule preview=${request.previewWidth}x${request.previewHeight} rotation=$previewRotation")
        cam.openCamera(textureView, request)
        cam.setRotateType(previewRotation)
        syncRenderSize(cam)
        camera = cam
        CameraHolder.camera = cam
    }

    private fun reopenCameraForSurface() {
        Log.d(TAG, "reopenCameraForSurface hasClient=${multiCameraClient != null} cameraMapSize=${cameraMap.size}")
        if (multiCameraClient == null) {
            registerUsbMonitor()
            return
        }

        val currentTextureView = textureView ?: return
        val cam = cameraMap.values.firstOrNull() ?: run {
            registerUsbMonitor()
            return
        }

        try {
            isPreviewRefreshInProgress = true
            val request = buildCameraRequest(cam)
            Log.d(TAG, "reopenCameraForSurface openCamera preview=${request.previewWidth}x${request.previewHeight} rotation=$previewRotation")
            cam.openCamera(currentTextureView, request)
            cam.setRotateType(previewRotation)
            syncRenderSize(cam)
            camera = cam
            CameraHolder.camera = cam
            CameraHolder.cameraView = this
            CameraHolder.client = multiCameraClient
        } catch (error: Exception) {
            isPreviewRefreshInProgress = false
            Log.e(TAG, "reopenCameraForSurface failed: ${error.message}", error)
            registerUsbMonitor()
        }
    }

    private fun refreshPreviewAfterRotationChange() {
        val cam = camera ?: return
        if (!cam.isCameraOpened()) return

        Log.d(TAG, "refreshPreviewAfterRotationChange rotation=$previewRotation")
        isPreviewRefreshInProgress = true
        clearRefreshHealthCheck()

        reopenPreviewRunnable?.let(mainHandler::removeCallbacks)
        sendLoadingEvent()

        reopenPreviewRunnable = Runnable {
            val currentTextureView = textureView ?: return@Runnable

            try {
                val request = buildCameraRequest(cam)
                Log.d(TAG, "refreshPreviewAfterRotationChange reopenCamera preview=${request.previewWidth}x${request.previewHeight} rotation=$previewRotation")
                cam.openCamera(currentTextureView, request)
                cam.setRotateType(previewRotation)
                syncRenderSize(cam)
                camera = cam
                CameraHolder.camera = cam
                CameraHolder.cameraView = this
                CameraHolder.client = multiCameraClient
                scheduleRefreshHealthCheck(cam)
            } finally {
                Log.d(TAG, "refreshPreviewAfterRotationChange completed")
                reopenPreviewRunnable = null
            }
        }

        try {
            Log.d(TAG, "refreshPreviewAfterRotationChange closing current camera")
            cam.closeCamera()
        } finally {
            reopenPreviewRunnable?.let {
                mainHandler.postDelayed(it, 180)
            }
        }
    }

    private fun scheduleRefreshHealthCheck(targetCamera: MultiCameraClient.ICamera) {
        clearRefreshHealthCheck()
        refreshHealthCheckRunnable = Runnable {
            if (!isPreviewRefreshInProgress) return@Runnable
            if (targetCamera.isCameraOpened()) {
                Log.d(TAG, "refreshHealthCheck skipped because camera is already opened")
                return@Runnable
            }

            val currentTextureView = textureView ?: return@Runnable

            try {
                val request = buildCameraRequest(targetCamera)
                Log.d(TAG, "refreshHealthCheck reopenCamera preview=${request.previewWidth}x${request.previewHeight} rotation=$previewRotation")
                targetCamera.openCamera(currentTextureView, request)
                targetCamera.setRotateType(previewRotation)
                syncRenderSize(targetCamera)
                camera = targetCamera
                CameraHolder.camera = targetCamera
                CameraHolder.cameraView = this
                CameraHolder.client = multiCameraClient
            } catch (error: Exception) {
                Log.e(TAG, "refreshHealthCheck failed: ${error.message}", error)
            }
        }
        mainHandler.postDelayed(refreshHealthCheckRunnable!!, REFRESH_HEALTH_CHECK_DELAY_MS)
    }

    private fun clearRefreshHealthCheck() {
        refreshHealthCheckRunnable?.let(mainHandler::removeCallbacks)
        refreshHealthCheckRunnable = null
    }

    private fun buildCameraRequest(targetCamera: MultiCameraClient.ICamera? = camera): CameraRequest {
        val (resolvedWidth, resolvedHeight) = resolveRequestedPreviewSize(targetCamera)
        applyActivePreviewSize(resolvedWidth, resolvedHeight, rememberPreference = false)

        return CameraRequest.Builder()
            .setPreviewWidth(activePreviewWidth)
            .setPreviewHeight(activePreviewHeight)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(previewRotation)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .create()
    }

    private fun resolveRequestedPreviewSize(targetCamera: MultiCameraClient.ICamera?): Pair<Int, Int> {
        val preferredSize = CameraHolder.getPreferredPreviewSize()
        val desiredWidth = preferredSize?.first ?: requestedPreviewWidth
        val desiredHeight = preferredSize?.second ?: requestedPreviewHeight
        val supportedSizes = runCatching {
            targetCamera?.getAllPreviewSizes()?.toList().orEmpty()
        }.getOrElse {
            emptyList()
        }

        if (supportedSizes.isEmpty()) {
            return desiredWidth to desiredHeight
        }

        supportedSizes.firstOrNull { it.width == desiredWidth && it.height == desiredHeight }?.let {
            return it.width to it.height
        }

        supportedSizes.firstOrNull { it.width == requestedPreviewWidth && it.height == requestedPreviewHeight }?.let {
            return it.width to it.height
        }

        val desiredAspectRatio = desiredWidth.toDouble() / desiredHeight.coerceAtLeast(1)
        val fallback = supportedSizes.minWithOrNull(
            compareBy<com.jiangdg.ausbc.camera.bean.PreviewSize> {
                abs((it.width.toDouble() / it.height.coerceAtLeast(1)) - desiredAspectRatio)
            }.thenBy {
                abs((it.width * it.height) - (desiredWidth * desiredHeight))
            }
        ) ?: supportedSizes.first()

        return fallback.width to fallback.height
    }

    private fun applyActivePreviewSize(width: Int, height: Int, rememberPreference: Boolean) {
        if (width <= 0 || height <= 0) return

        activePreviewWidth = width
        activePreviewHeight = height

        if (rememberPreference) {
            CameraHolder.setPreferredPreviewSize(width, height)
        }

        updatePreviewAspectRatio()
    }

    private fun syncPreviewSizeFromCamera(targetCamera: MultiCameraClient.ICamera) {
        val request = runCatching { targetCamera.getCameraRequest() }.getOrNull() ?: return
        applyActivePreviewSize(request.previewWidth, request.previewHeight, rememberPreference = true)
    }

    private fun syncRenderSize(targetCamera: MultiCameraClient.ICamera) {
        val currentTextureView = textureView ?: return
        val renderWidth = currentTextureView.width
        val renderHeight = currentTextureView.height

        if (renderWidth > 0 && renderHeight > 0) {
            targetCamera.setRenderSize(renderWidth, renderHeight)
        }
    }

    private fun updateAutoPreviewRotation(refreshPreview: Boolean) {
        if (manualPreviewRotation != null) return
        val nextRotation = getDisplayRotateType(resolveDisplay())
        Log.d(TAG, "updateAutoPreviewRotation rotation=$nextRotation refreshPreview=$refreshPreview")
        applyPreviewRotation(nextRotation, refreshPreview)
    }

    private fun applyPreviewRotation(nextRotation: RotateType, refreshPreview: Boolean) {
        val rotationChanged = previewRotation != nextRotation
        Log.d(TAG, "applyPreviewRotation from=$previewRotation to=$nextRotation changed=$rotationChanged refreshPreview=$refreshPreview")
        previewRotation = nextRotation
        updatePreviewAspectRatio()
        camera?.let { currentCamera ->
            currentCamera.setRotateType(previewRotation)
            syncRenderSize(currentCamera)
        }

        if (rotationChanged && refreshPreview) {
            refreshPreviewAfterRotationChange()
        }
    }

    private fun registerDisplayListener() {
        if (isDisplayListenerRegistered) return
        Log.d(TAG, "registerDisplayListener")
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListener() {
        if (!isDisplayListenerRegistered) return
        Log.d(TAG, "unregisterDisplayListener")
        displayManager?.unregisterDisplayListener(displayListener)
        isDisplayListenerRegistered = false
    }

    private fun resolveDisplay(): Display? {
        return display ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private fun shouldIgnoreDisconnectCallback(device: UsbDevice?): Boolean {
        val deviceId = device?.deviceId ?: return false
        if (isShuttingDown) return false
        return isPreviewRefreshInProgress && cameraMap.containsKey(deviceId)
    }

    private fun getDisplayRotateType(targetDisplay: Display?): RotateType {
        return when (targetDisplay?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> RotateType.ANGLE_90
            Surface.ROTATION_180 -> RotateType.ANGLE_180
            Surface.ROTATION_270 -> RotateType.ANGLE_270
            else -> RotateType.ANGLE_0
        }
    }

    private fun updatePreviewAspectRatio() {
        val view = textureView ?: return
        val shouldSwapDimensions = previewRotation == RotateType.ANGLE_90 || previewRotation == RotateType.ANGLE_270
        val aspectWidth = if (shouldSwapDimensions) activePreviewHeight else activePreviewWidth
        val aspectHeight = if (shouldSwapDimensions) activePreviewWidth else activePreviewHeight
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
