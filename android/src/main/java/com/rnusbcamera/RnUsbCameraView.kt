package com.rnusbcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
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
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.usb.USBMonitor

class RnUsbCameraView(private val reactContext: ThemedReactContext) : FrameLayout(reactContext) {

    private var textureView: AspectRatioTextureView? = null
    private var multiCameraClient: MultiCameraClient? = null
    private var camera: MultiCameraClient.ICamera? = null
    private val cameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private val mainHandler = Handler(Looper.getMainLooper())

    var previewWidth: Int = 640
    var previewHeight: Int = 480
    var autoOpen: Boolean = true

    init {
        textureView = AspectRatioTextureView(reactContext)
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setupTextureListener()
    }

    private fun setupTextureListener() {
        textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                registerUsbMonitor()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                camera?.setRenderSize(width, height)
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
                        .setDefaultRotateType(RotateType.ANGLE_0)
                        .setAudioSource(CameraRequest.AudioSource.SOURCE_AUTO)
                        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                        .create()

                    openCamera(textureView, request)
                    camera = this
                    CameraHolder.camera = this
                    CameraHolder.cameraView = this@RnUsbCameraView
                    CameraHolder.client = multiCameraClient
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                closeCurrentCamera()
            }

            override fun onCancelDev(device: UsbDevice?) {
                // User cancelled USB permission
            }
        })
        multiCameraClient?.register()
    }

    private fun closeCurrentCamera() {
        camera?.closeCamera()
        camera = null
        CameraHolder.camera = null
        CameraHolder.cameraView = null
    }

    private fun unregisterUsbMonitor() {
        cameraMap.values.forEach { it.closeCamera() }
        cameraMap.clear()
        camera = null
        CameraHolder.camera = null
        CameraHolder.cameraView = null
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

    fun getCamera(): MultiCameraClient.ICamera? = camera
    fun getClient(): MultiCameraClient? = multiCameraClient
}
