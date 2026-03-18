package com.rnusbcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import kotlin.math.abs

enum class PreviewResizeMode {
    COVER,
    CONTAIN;

    companion object {
        fun fromJsValue(value: String?): PreviewResizeMode {
            return when (value) {
                "contain" -> CONTAIN
                else -> COVER
            }
        }
    }
}

class PreviewTextureView(context: Context) : TextureView(context), IAspectRatio {

    private var aspectRatio: Double = -1.0

    var resizeMode: PreviewResizeMode = PreviewResizeMode.COVER
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun setAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        post {
            setAspectRatio(width.toDouble() / height.toDouble())
        }
    }

    override fun getSurfaceWidth(): Int = measuredWidth

    override fun getSurfaceHeight(): Int = measuredHeight

    override fun getSurface(): Surface? {
        val texture: SurfaceTexture = surfaceTexture ?: return null
        return try {
            Surface(texture)
        } catch (_: Exception) {
            null
        }
    }

    override fun postUITask(task: () -> Unit) {
        post { task() }
    }

    private fun setAspectRatio(ratio: Double) {
        if (ratio <= 0 || abs(aspectRatio - ratio) < 0.0001) return

        aspectRatio = ratio
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var measuredContentWidth = MeasureSpec.getSize(widthMeasureSpec)
        var measuredContentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom

        measuredContentWidth -= horizontalPadding
        measuredContentHeight -= verticalPadding

        if (aspectRatio > 0 && measuredContentWidth > 0 && measuredContentHeight > 0) {
            val availableRatio = measuredContentWidth.toDouble() / measuredContentHeight.toDouble()
            val ratioDelta = aspectRatio / availableRatio - 1.0

            if (abs(ratioDelta) > 0.01) {
                when (resizeMode) {
                    PreviewResizeMode.CONTAIN -> {
                        if (ratioDelta > 0) {
                            measuredContentHeight = (measuredContentWidth / aspectRatio).toInt()
                        } else {
                            measuredContentWidth = (measuredContentHeight * aspectRatio).toInt()
                        }
                    }

                    PreviewResizeMode.COVER -> {
                        if (ratioDelta > 0) {
                            measuredContentWidth = (measuredContentHeight * aspectRatio).toInt()
                        } else {
                            measuredContentHeight = (measuredContentWidth / aspectRatio).toInt()
                        }
                    }
                }
            }
        }

        val finalWidth = measuredContentWidth + horizontalPadding
        val finalHeight = measuredContentHeight + verticalPadding

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        )
    }
}