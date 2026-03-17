package com.rnusbcamera

import com.jiangdg.ausbc.MultiCameraClient

/**
 * Singleton to share camera state between the View and the Module.
 */
object CameraHolder {
    var camera: MultiCameraClient.ICamera? = null
    var cameraView: RnUsbCameraView? = null
    var client: MultiCameraClient? = null

    fun isReady(): Boolean = camera?.isCameraOpened() == true
}
