package com.rnusbcamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.usb.USBMonitor

@ReactModule(name = RnUsbCameraModule.NAME)
class RnUsbCameraModule(reactContext: ReactApplicationContext) :
    NativeRnUsbCameraSpec(reactContext) {

    companion object {
        const val NAME = "RnUsbCamera"
        private const val EVENT_DEVICE_ATTACHED = "onDeviceAttached"
        private const val EVENT_DEVICE_DETACHED = "onDeviceDetached"
    }

    override fun getName(): String = NAME

    @Synchronized
    private fun destroyModuleClient() {
        CameraHolder.moduleClient?.let { client ->
            runCatching { client.unRegister() }
            runCatching { client.destroy() }
        }
        CameraHolder.moduleClient = null
    }

    private fun usbDeviceToMap(device: UsbDevice): WritableMap {
        return Arguments.createMap().apply {
            putInt("deviceId", device.deviceId)
            putInt("vendorId", device.vendorId)
            putInt("productId", device.productId)
            putString("deviceName", device.deviceName)
        }
    }

    @Synchronized
    private fun ensureModuleClient(): MultiCameraClient {
        CameraHolder.moduleClient?.let { return it }

        val client = MultiCameraClient(reactApplicationContext, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                sendJSEvent(EVENT_DEVICE_ATTACHED, usbDeviceToMap(device))
            }

            override fun onDetachDec(device: UsbDevice?) {
                device ?: return
                sendJSEvent(EVENT_DEVICE_DETACHED, Arguments.createMap().apply {
                    putInt("deviceId", device.deviceId)
                })
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) = Unit

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) = Unit

            override fun onCancelDev(device: UsbDevice?) = Unit
        })

        client.register()
        CameraHolder.moduleClient = client
        return client
    }

    private fun isDestroyedClientError(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("already destroyed", ignoreCase = true)
    }

    private inline fun <T> withFreshModuleClient(block: (MultiCameraClient) -> T): T {
        try {
            return block(ensureModuleClient())
        } catch (error: Exception) {
            if (!isDestroyedClientError(error)) {
                throw error
            }

            destroyModuleClient()
            return block(ensureModuleClient())
        }
    }

    private inline fun <T> withAvailablePermissionClient(block: (MultiCameraClient) -> T): T {
        val activeViewClient = CameraHolder.client
        if (activeViewClient != null) {
            try {
                return block(activeViewClient)
            } catch (error: Exception) {
                if (!isDestroyedClientError(error)) {
                    throw error
                }

                CameraHolder.client = null
            }
        }

        return withFreshModuleClient(block)
    }

    // ── Device List ──────────────────────────────────────────────────────

    @ReactMethod
    override fun getDeviceList(promise: Promise) {
        try {
            val devices = withFreshModuleClient { client ->
                client.getDeviceList(null) ?: emptyList<UsbDevice>()
            }
            val result = Arguments.createArray()
            for (device in devices) {
                result.pushMap(usbDeviceToMap(device))
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_DEVICE_LIST", e.message, e)
        }
    }

    @ReactMethod
    override fun requestPermission(deviceId: Double, promise: Promise) {
        try {
            val id = deviceId.toInt()
            val result = withAvailablePermissionClient { client ->
                val devices = client.getDeviceList(null) ?: emptyList()
                val device = devices.find { it.deviceId == id }
                    ?: throw IllegalStateException("Device with id $id not found")

                client.requestPermission(device)
            }
            promise.resolve(result)
        } catch (e: IllegalStateException) {
            promise.reject("ERR_DEVICE_NOT_FOUND", e.message, e)
        } catch (e: Exception) {
            promise.reject("ERR_PERMISSION", e.message, e)
        }
    }

    // ── Camera State ─────────────────────────────────────────────────────

    @ReactMethod
    override fun isCameraOpened(promise: Promise) {
        promise.resolve(CameraHolder.isReady())
    }

    @ReactMethod
    override fun openCamera(promise: Promise) {
        try {
            val view = CameraHolder.cameraView ?: run {
                promise.reject("ERR_NO_VIEW", "UsbCameraView not mounted")
                return
            }
            if (CameraHolder.isReady()) {
                promise.resolve(null)
                return
            }
            view.openCameraFromModule()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_OPEN_CAMERA", e.message, e)
        }
    }

    @ReactMethod
    override fun closeCamera(promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.resolve(null)
                return
            }
            camera.closeCamera()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_CLOSE_CAMERA", e.message, e)
        }
    }

    // ── Preview Sizes ────────────────────────────────────────────────────

    @ReactMethod
    override fun getAllPreviewSizes(promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.resolve(Arguments.createArray())
                return
            }
            val sizes = camera.getAllPreviewSizes()
            val result = Arguments.createArray()
            for (size in sizes) {
                result.pushMap(Arguments.createMap().apply {
                    putInt("width", size.width)
                    putInt("height", size.height)
                })
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_PREVIEW_SIZES", e.message, e)
        }
    }

    @ReactMethod
    override fun getCurrentResolution(promise: Promise) {
        try {
            val request = CameraHolder.camera?.getCameraRequest()
            if (request == null) {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            promise.resolve(Arguments.createMap().apply {
                putInt("width", request.previewWidth)
                putInt("height", request.previewHeight)
            })
        } catch (e: Exception) {
            promise.reject("ERR_RESOLUTION", e.message, e)
        }
    }

    @ReactMethod
    override fun updateResolution(width: Double, height: Double, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            val targetWidth = width.toInt()
            val targetHeight = height.toInt()
            // Fire loading event on the view before resolution change
            CameraHolder.cameraView?.sendLoadingEvent()
            camera.updateResolution(targetWidth, targetHeight)
            CameraHolder.setPreferredPreviewSize(targetWidth, targetHeight)
            CameraHolder.cameraView?.onResolutionUpdated(targetWidth, targetHeight)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_RESOLUTION", e.message, e)
        }
    }

    // ── Image Capture ────────────────────────────────────────────────────

    /**
     * Creates a temp output file following react-native-vision-camera pattern.
     * - Uses File.createTempFile() for atomic unique file creation
     * - If path is provided, treats it as the target directory
     * - If path is null, defaults to cacheDir
     * - Cache files are marked deleteOnExit() for auto-cleanup
     */
    private fun createOutputFile(customPath: String?, prefix: String, extension: String): File {
        val directory = if (customPath != null) {
            val dir = File(customPath)
            if (dir.isFile) dir.parentFile ?: reactApplicationContext.cacheDir
            else dir
        } else {
            reactApplicationContext.cacheDir
        }

        if (!directory.exists()) directory.mkdirs()

        val file = File.createTempFile(prefix, ".$extension", directory)

        // Auto-delete temp files in cache dir when app process exits (Vision Camera pattern)
        if (directory.absolutePath.contains(reactApplicationContext.cacheDir.absolutePath)) {
            file.deleteOnExit()
        }

        return file
    }

    private fun applyExifOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
    }

    private fun mirrorCapturedImage(filePath: String) {
        val sourceBitmap = BitmapFactory.decodeFile(filePath)
            ?: throw IllegalStateException("Failed to decode captured image")

        val matrix = Matrix()
        val exifOrientation = runCatching {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        applyExifOrientation(matrix, exifOrientation)
        matrix.postScale(-1f, 1f)

        val mirroredBitmap = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )

        if (mirroredBitmap != sourceBitmap) {
            sourceBitmap.recycle()
        }

        try {
            FileOutputStream(filePath, false).use { outputStream ->
                val didWrite = mirroredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                if (!didWrite) {
                    throw IllegalStateException("Failed to write mirrored capture")
                }
            }
            runCatching {
                ExifInterface(filePath).apply {
                    setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString()
                    )
                    saveAttributes()
                }
            }
        } finally {
            mirroredBitmap.recycle()
        }
    }

    @ReactMethod
    override fun captureImage(path: String?, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            val outputFile = createOutputFile(path, "IMG_", "jpg")
            val shouldMirrorCapture = CameraHolder.cameraView?.shouldMirrorCapture() == true
            camera.captureImage(object : ICaptureCallBack {
                override fun onBegin() {}
                override fun onError(error: String?) {
                    promise.reject("ERR_CAPTURE", error ?: "Capture failed")
                }
                override fun onComplete(filePath: String?) {
                    val resolvedPath = filePath ?: outputFile.absolutePath
                    if (shouldMirrorCapture) {
                        try {
                            mirrorCapturedImage(resolvedPath)
                        } catch (error: Exception) {
                            promise.reject("ERR_CAPTURE_MIRROR", error.message ?: "Capture mirror failed", error)
                            return
                        }
                    }
                    promise.resolve(resolvedPath)
                }
            }, outputFile.absolutePath)
        } catch (e: Exception) {
            promise.reject("ERR_CAPTURE", e.message ?: "Capture failed", e)
        }
    }

    // ── Video Recording ──────────────────────────────────────────────────

    @ReactMethod
    override fun startRecording(path: String?, durationSec: Double, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            val outputFile = createOutputFile(path, "VID_", "mp4")
            camera.captureVideoStart(object : ICaptureCallBack {
                override fun onBegin() {
                    promise.resolve(null)
                }
                override fun onError(error: String?) {
                    promise.reject("ERR_RECORDING", error ?: "Recording failed")
                }
                override fun onComplete(filePath: String?) {
                    sendJSEvent("onRecordingComplete", Arguments.createMap().apply {
                        putString("path", filePath ?: outputFile.absolutePath)
                    })
                }
            }, outputFile.absolutePath, durationSec.toLong())
        } catch (e: Exception) {
            promise.reject("ERR_RECORDING", e.message ?: "Recording failed", e)
        }
    }

    @ReactMethod
    override fun stopRecording(promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            camera.captureVideoStop()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_STOP_RECORDING", e.message, e)
        }
    }

    @ReactMethod
    override fun isRecording(promise: Promise) {
        promise.resolve(CameraHolder.camera?.isRecording() == true)
    }

    // ── Audio Recording ──────────────────────────────────────────────────

    @ReactMethod
    override fun startAudioRecording(path: String?, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            val outputFile = createOutputFile(path, "AUD_", "mp3")
            camera.captureAudioStart(object : ICaptureCallBack {
                override fun onBegin() {
                    promise.resolve(null)
                }
                override fun onError(error: String?) {
                    promise.reject("ERR_AUDIO", error ?: "Audio recording failed")
                }
                override fun onComplete(filePath: String?) {
                    sendJSEvent("onAudioRecordingComplete", Arguments.createMap().apply {
                        putString("path", filePath ?: outputFile.absolutePath)
                    })
                }
            }, outputFile.absolutePath)
        } catch (e: Exception) {
            promise.reject("ERR_AUDIO", e.message ?: "Audio recording failed", e)
        }
    }

    @ReactMethod
    override fun stopAudioRecording(promise: Promise) {
        try {
            CameraHolder.camera?.captureAudioStop()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_STOP_AUDIO", e.message, e)
        }
    }

    // ── UVC Camera Controls ──────────────────────────────────────────────

    @ReactMethod
    override fun getSupportedControls(promise: Promise) {
        val cam = CameraHolder.camera as? CameraUVC
        if (cam == null) {
            promise.reject("ERR_NO_CAMERA", "Camera not opened")
            return
        }

        fun controlInfo(min: Int?, max: Int?, current: Int?): WritableMap {
            val supported = (min != null && max != null && min != max)
            return Arguments.createMap().apply {
                putBoolean("supported", supported)
                putInt("min", min ?: 0)
                putInt("max", max ?: 0)
                putInt("current", current ?: 0)
            }
        }

        promise.resolve(Arguments.createMap().apply {
            putMap("brightness", controlInfo(cam.getBrightnessMin(), cam.getBrightnessMax(), cam.getBrightness()))
            putMap("contrast", controlInfo(cam.getContrastMin(), cam.getContrastMax(), cam.getContrast()))
            putMap("sharpness", controlInfo(cam.getSharpnessMin(), cam.getSharpnessMax(), cam.getSharpness()))
            putMap("gain", controlInfo(cam.getGainMin(), cam.getGainMax(), cam.getGain()))
            putMap("gamma", controlInfo(cam.getGammaMin(), cam.getGammaMax(), cam.getGamma()))
            putMap("saturation", controlInfo(cam.getSaturationMin(), cam.getSaturationMax(), cam.getSaturation()))
            putMap("hue", controlInfo(cam.getHueMin(), cam.getHueMax(), cam.getHue()))
            putMap("zoom", controlInfo(cam.getZoomMin(), cam.getZoomMax(), cam.getZoom()))
        })
    }

    @ReactMethod
    override fun setBrightness(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setBrightness(value.toInt())
    }

    @ReactMethod
    override fun getBrightness(promise: Promise) {
        val brightness = (CameraHolder.camera as? CameraUVC)?.getBrightness() ?: 0
        promise.resolve(brightness)
    }

    @ReactMethod
    override fun setContrast(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setContrast(value.toInt())
    }

    @ReactMethod
    override fun getContrast(promise: Promise) {
        val contrast = (CameraHolder.camera as? CameraUVC)?.getContrast() ?: 0
        promise.resolve(contrast)
    }

    @ReactMethod
    override fun setZoom(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setZoom(value.toInt())
    }

    @ReactMethod
    override fun getZoom(promise: Promise) {
        val zoom = (CameraHolder.camera as? CameraUVC)?.getZoom() ?: 0
        promise.resolve(zoom)
    }

    @ReactMethod
    override fun setSharpness(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setSharpness(value.toInt())
    }

    @ReactMethod
    override fun getSharpness(promise: Promise) {
        val sharpness = (CameraHolder.camera as? CameraUVC)?.getSharpness() ?: 0
        promise.resolve(sharpness)
    }

    @ReactMethod
    override fun setSaturation(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setSaturation(value.toInt())
    }

    @ReactMethod
    override fun getSaturation(promise: Promise) {
        val saturation = (CameraHolder.camera as? CameraUVC)?.getSaturation() ?: 0
        promise.resolve(saturation)
    }

    @ReactMethod
    override fun setHue(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setHue(value.toInt())
    }

    @ReactMethod
    override fun getHue(promise: Promise) {
        val hue = (CameraHolder.camera as? CameraUVC)?.getHue() ?: 0
        promise.resolve(hue)
    }

    @ReactMethod
    override fun setGamma(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setGamma(value.toInt())
    }

    @ReactMethod
    override fun getGamma(promise: Promise) {
        val gamma = (CameraHolder.camera as? CameraUVC)?.getGamma() ?: 0
        promise.resolve(gamma)
    }

    @ReactMethod
    override fun setGain(value: Double) {
        (CameraHolder.camera as? CameraUVC)?.setGain(value.toInt())
    }

    @ReactMethod
    override fun getGain(promise: Promise) {
        val gain = (CameraHolder.camera as? CameraUVC)?.getGain() ?: 0
        promise.resolve(gain)
    }

    @ReactMethod
    override fun setAutoFocus(enable: Boolean) {
        (CameraHolder.camera as? CameraUVC)?.setAutoFocus(enable)
    }

    @ReactMethod
    override fun setAutoWhiteBalance(enable: Boolean) {
        (CameraHolder.camera as? CameraUVC)?.setAutoWhiteBalance(enable)
    }

    @ReactMethod
    override fun resetAllControls() {
        (CameraHolder.camera as? CameraUVC)?.apply {
            resetBrightness()
            resetContrast()
            resetZoom()
            resetSharpness()
            resetSaturation()
            resetHue()
            resetGamma()
            resetGain()
            resetAutoFocus()
        }
    }

    // ── Event Helpers ────────────────────────────────────────────────────

    private fun sendJSEvent(eventName: String, params: WritableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    @ReactMethod
    override fun addListener(eventName: String) {
        if (eventName == EVENT_DEVICE_ATTACHED || eventName == EVENT_DEVICE_DETACHED) {
            runCatching { ensureModuleClient() }
                .onFailure { error ->
                    promiseRejectWarning("ERR_USB_CLIENT", error)
                }
        }
    }

    @ReactMethod
    override fun removeListeners(count: Double) {
        // Required for RN event emitter
    }

    private fun promiseRejectWarning(code: String, error: Throwable) {
        System.err.println("[$NAME] $code: ${error.message}")
    }
}
