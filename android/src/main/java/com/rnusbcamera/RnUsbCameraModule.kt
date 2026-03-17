package com.rnusbcamera

import android.hardware.usb.UsbDevice
import java.io.File
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.camera.CameraUVC

class RnUsbCameraModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "RnUsbCamera"

    // ── Device List ──────────────────────────────────────────────────────

    @ReactMethod
    fun getDeviceList(promise: Promise) {
        try {
            val client = CameraHolder.client
            if (client == null) {
                promise.resolve(Arguments.createArray())
                return
            }
            val devices = client.getDeviceList(null) ?: emptyList<UsbDevice>()
            val result = Arguments.createArray()
            for (device in devices) {
                result.pushMap(Arguments.createMap().apply {
                    putInt("deviceId", device.deviceId)
                    putInt("vendorId", device.vendorId)
                    putInt("productId", device.productId)
                    putString("deviceName", device.deviceName)
                })
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_DEVICE_LIST", e.message, e)
        }
    }

    @ReactMethod
    fun requestPermission(deviceId: Int, promise: Promise) {
        try {
            val client = CameraHolder.client ?: run {
                promise.reject("ERR_NO_CLIENT", "USB client not initialized. Mount UsbCameraView first.")
                return
            }
            val devices = client.getDeviceList(null) ?: emptyList()
            val device = devices.find { it.deviceId == deviceId }
            if (device == null) {
                promise.reject("ERR_DEVICE_NOT_FOUND", "Device with id $deviceId not found")
                return
            }
            val result = client.requestPermission(device)
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_PERMISSION", e.message, e)
        }
    }

    // ── Camera State ─────────────────────────────────────────────────────

    @ReactMethod
    fun isCameraOpened(promise: Promise) {
        promise.resolve(CameraHolder.isReady())
    }

    @ReactMethod
    fun openCamera(promise: Promise) {
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
    fun closeCamera(promise: Promise) {
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
    fun getAllPreviewSizes(promise: Promise) {
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
    fun getCurrentResolution(promise: Promise) {
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
    fun updateResolution(width: Int, height: Int, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            // Fire loading event on the view before resolution change
            CameraHolder.cameraView?.sendLoadingEvent()
            camera.updateResolution(width, height)
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

    @ReactMethod
    fun captureImage(path: String?, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            val outputFile = createOutputFile(path, "IMG_", "jpg")
            camera.captureImage(object : ICaptureCallBack {
                override fun onBegin() {}
                override fun onError(error: String?) {
                    promise.reject("ERR_CAPTURE", error ?: "Capture failed")
                }
                override fun onComplete(filePath: String?) {
                    promise.resolve(filePath ?: outputFile.absolutePath)
                }
            }, outputFile.absolutePath)
        } catch (e: Exception) {
            promise.reject("ERR_CAPTURE", e.message ?: "Capture failed", e)
        }
    }

    // ── Video Recording ──────────────────────────────────────────────────

    @ReactMethod
    fun startRecording(path: String?, durationSec: Int, promise: Promise) {
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
    fun stopRecording(promise: Promise) {
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
    fun isRecording(promise: Promise) {
        promise.resolve(CameraHolder.camera?.isRecording() == true)
    }

    // ── Audio Recording ──────────────────────────────────────────────────

    @ReactMethod
    fun startAudioRecording(path: String?, promise: Promise) {
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
    fun stopAudioRecording(promise: Promise) {
        try {
            CameraHolder.camera?.captureAudioStop()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_STOP_AUDIO", e.message, e)
        }
    }

    // ── UVC Camera Controls ──────────────────────────────────────────────

    @ReactMethod
    fun getSupportedControls(promise: Promise) {
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
    fun setBrightness(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setBrightness(value)
    }

    @ReactMethod
    fun getBrightness(promise: Promise) {
        val brightness = (CameraHolder.camera as? CameraUVC)?.getBrightness() ?: 0
        promise.resolve(brightness)
    }

    @ReactMethod
    fun setContrast(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setContrast(value)
    }

    @ReactMethod
    fun getContrast(promise: Promise) {
        val contrast = (CameraHolder.camera as? CameraUVC)?.getContrast() ?: 0
        promise.resolve(contrast)
    }

    @ReactMethod
    fun setZoom(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setZoom(value)
    }

    @ReactMethod
    fun getZoom(promise: Promise) {
        val zoom = (CameraHolder.camera as? CameraUVC)?.getZoom() ?: 0
        promise.resolve(zoom)
    }

    @ReactMethod
    fun setSharpness(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setSharpness(value)
    }

    @ReactMethod
    fun getSharpness(promise: Promise) {
        val sharpness = (CameraHolder.camera as? CameraUVC)?.getSharpness() ?: 0
        promise.resolve(sharpness)
    }

    @ReactMethod
    fun setSaturation(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setSaturation(value)
    }

    @ReactMethod
    fun getSaturation(promise: Promise) {
        val saturation = (CameraHolder.camera as? CameraUVC)?.getSaturation() ?: 0
        promise.resolve(saturation)
    }

    @ReactMethod
    fun setHue(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setHue(value)
    }

    @ReactMethod
    fun getHue(promise: Promise) {
        val hue = (CameraHolder.camera as? CameraUVC)?.getHue() ?: 0
        promise.resolve(hue)
    }

    @ReactMethod
    fun setGamma(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setGamma(value)
    }

    @ReactMethod
    fun getGamma(promise: Promise) {
        val gamma = (CameraHolder.camera as? CameraUVC)?.getGamma() ?: 0
        promise.resolve(gamma)
    }

    @ReactMethod
    fun setGain(value: Int) {
        (CameraHolder.camera as? CameraUVC)?.setGain(value)
    }

    @ReactMethod
    fun getGain(promise: Promise) {
        val gain = (CameraHolder.camera as? CameraUVC)?.getGain() ?: 0
        promise.resolve(gain)
    }

    @ReactMethod
    fun setAutoFocus(enable: Boolean) {
        (CameraHolder.camera as? CameraUVC)?.setAutoFocus(enable)
    }

    @ReactMethod
    fun setAutoWhiteBalance(enable: Boolean) {
        (CameraHolder.camera as? CameraUVC)?.setAutoWhiteBalance(enable)
    }

    @ReactMethod
    fun resetAllControls() {
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
    fun addListener(eventName: String) {
        // Required for RN event emitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RN event emitter
    }
}
