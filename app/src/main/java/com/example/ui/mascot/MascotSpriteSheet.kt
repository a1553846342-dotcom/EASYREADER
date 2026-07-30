package com.example.ui.mascot

import com.example.R

/**
 * Asset declarations for Roxy Mascot character system (RoxyBase).
 * Vector drawables with 100% transparent backgrounds.
 */
object MascotSpriteSheet {
    // Roxy Vector Drawables (Clean Alpha Transparency)
    val idleDrawable = R.drawable.roxy_idle
    val sadDrawable = R.drawable.roxy_sad
    val happyDrawable = R.drawable.roxy_happy
    val moveDrawable = R.drawable.roxy_move
    val bookmarkDrawable = R.drawable.roxy_bookmark
}

/**
 * Mascot Asset Mapping table for character poses.
 */
object MascotAssetMap {
    val idle = MascotSpriteSheet.idleDrawable
    val sad = MascotSpriteSheet.sadDrawable
    val happy = MascotSpriteSheet.happyDrawable
    val move = MascotSpriteSheet.moveDrawable
    val bookmark = MascotSpriteSheet.bookmarkDrawable
}
