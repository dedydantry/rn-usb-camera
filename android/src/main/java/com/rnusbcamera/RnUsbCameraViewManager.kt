package com.rnusbcamera

import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class RnUsbCameraViewManager : SimpleViewManager<RnUsbCameraView>() {

    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): RnUsbCameraView {
        return RnUsbCameraView(reactContext)
    }

    override fun onDropViewInstance(view: RnUsbCameraView) {
        view.onDropView()
        super.onDropViewInstance(view)
    }

    @ReactProp(name = "previewWidth", defaultInt = 640)
    fun setPreviewWidth(view: RnUsbCameraView, width: Int) {
        view.updatePreviewWidth(width)
    }

    @ReactProp(name = "previewHeight", defaultInt = 480)
    fun setPreviewHeight(view: RnUsbCameraView, height: Int) {
        view.updatePreviewHeight(height)
    }

    @ReactProp(name = "previewRotation")
    fun setPreviewRotation(view: RnUsbCameraView, rotation: String?) {
        view.setPreviewRotation(rotation)
    }

    @ReactProp(name = "resizeMode")
    fun setResizeMode(view: RnUsbCameraView, resizeMode: String?) {
        view.setResizeMode(resizeMode)
    }

    @ReactProp(name = "autoOpen", defaultBoolean = true)
    fun setAutoOpen(view: RnUsbCameraView, autoOpen: Boolean) {
        view.autoOpen = autoOpen
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any>? {
        return MapBuilder.builder<String, Any>()
            .put("onDeviceAttached", MapBuilder.of("registrationName", "onDeviceAttached"))
            .put("onDeviceDetached", MapBuilder.of("registrationName", "onDeviceDetached"))
            .put("onCameraOpened", MapBuilder.of("registrationName", "onCameraOpened"))
            .put("onCameraClosed", MapBuilder.of("registrationName", "onCameraClosed"))
            .put("onCameraLoading", MapBuilder.of("registrationName", "onCameraLoading"))
            .put("onError", MapBuilder.of("registrationName", "onError"))
            .build().toMutableMap()
    }

    companion object {
        const val REACT_CLASS = "RnUsbCameraView"
    }
}
