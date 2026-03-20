package com.rnusbcamera

import com.jiangdg.ausbc.MultiCameraClient

/**
 * Singleton to share camera state between the View and the Module.
 */
object CameraHolder {
    var camera: MultiCameraClient.ICamera? = null
    var cameraView: RnUsbCameraView? = null
    var client: MultiCameraClient? = null
    var moduleClient: MultiCameraClient? = null
    private var preferredPreviewWidth: Int? = null
    private var preferredPreviewHeight: Int? = null

    fun isReady(): Boolean = camera?.isCameraOpened() == true

    fun setPreferredPreviewSize(width: Int, height: Int) {
        preferredPreviewWidth = width
        preferredPreviewHeight = height
    }

    fun getPreferredPreviewSize(): Pair<Int, Int>? {
        val width = preferredPreviewWidth ?: return null
        val height = preferredPreviewHeight ?: return null
        return width to height
    }
}
