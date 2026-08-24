package me.trishiraj.shadowglow

import android.graphics.BlurMaskFilter

/**
 * Defines the style of the blur effect for the shadow, corresponding to `BlurMaskFilter.Blur`.
 */
enum class ShadowBlurStyle {
    NORMAL, SOLID, OUTER, INNER
}

/** Converts [ShadowBlurStyle] to the Android-specific `BlurMaskFilter.Blur`. */
internal fun ShadowBlurStyle.toAndroidBlurStyle(): BlurMaskFilter.Blur {
    return when (this) {
        ShadowBlurStyle.NORMAL -> BlurMaskFilter.Blur.NORMAL
        ShadowBlurStyle.SOLID -> BlurMaskFilter.Blur.SOLID
        ShadowBlurStyle.OUTER -> BlurMaskFilter.Blur.OUTER
        ShadowBlurStyle.INNER -> BlurMaskFilter.Blur.INNER
    }
}
