package com.rnusbcamera

import android.hardware.usb.UsbDevice
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
    fun updateResolution(width: Int, height: Int, promise: Promise) {
        try {
            val camera = CameraHolder.camera ?: run {
                promise.reject("ERR_NO_CAMERA", "Camera not opened")
                return
            }
            camera.updateResolution(width, height)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_RESOLUTION", e.message, e)
        }
    }

    // ── Image Capture ────────────────────────────────────────────────────

    @ReactMethod
    fun captureImage(path: String?, promise: Promise) {
        val camera = CameraHolder.camera ?: run {
            promise.reject("ERR_NO_CAMERA", "Camera not opened")
            return
        }
        camera.captureImage(object : ICaptureCallBack {
            override fun onBegin() {}
            override fun onError(error: String?) {
                promise.reject("ERR_CAPTURE", error ?: "Capture failed")
            }
            override fun onComplete(filePath: String?) {
                promise.resolve(filePath)
            }
        }, path)
    }

    // ── Video Recording ──────────────────────────────────────────────────

    @ReactMethod
    fun startRecording(path: String?, durationSec: Int, promise: Promise) {
        val camera = CameraHolder.camera ?: run {
            promise.reject("ERR_NO_CAMERA", "Camera not opened")
            return
        }
        camera.captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                promise.resolve(null)
            }
            override fun onError(error: String?) {
                promise.reject("ERR_RECORDING", error ?: "Recording failed")
            }
            override fun onComplete(filePath: String?) {
                sendJSEvent("onRecordingComplete", Arguments.createMap().apply {
                    putString("path", filePath)
                })
            }
        }, path, durationSec.toLong())
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
        val camera = CameraHolder.camera ?: run {
            promise.reject("ERR_NO_CAMERA", "Camera not opened")
            return
        }
        camera.captureAudioStart(object : ICaptureCallBack {
            override fun onBegin() {
                promise.resolve(null)
            }
            override fun onError(error: String?) {
                promise.reject("ERR_AUDIO", error ?: "Audio recording failed")
            }
            override fun onComplete(filePath: String?) {
                sendJSEvent("onAudioRecordingComplete", Arguments.createMap().apply {
                    putString("path", filePath)
                })
            }
        }, path)
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
