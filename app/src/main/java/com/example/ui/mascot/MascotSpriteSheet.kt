package com.example.ui.mascot

import com.example.R

/**
 * Roxy 吉祥物素材（新 5 张统一形象：蓝发双马尾巫师少女）。
 * 场景绑定已整体重置，按“动作语义”重新分配：
 *  - idle      待机站姿（默认/常驻/欢迎）
 *  - celebrate 举杖欢呼（成就/成功/奖励）
 *  - run       奔跑（加载/过渡/跳转）
 *  - reading   捧书阅读（阅读相关）
 *  - sad       低落站姿（空状态/异常/久未使用）
 * 旧属性名保留为兼容别名，但语义已映射到新素材。
 */
object MascotSpriteSheet {
    val idleDrawable = R.drawable.mascot_idle
    val sadDrawable = R.drawable.mascot_sad
    val happyDrawable = R.drawable.mascot_celebrate
    val moveDrawable = R.drawable.mascot_run
    val readingDrawable = R.drawable.mascot_reading
    val celebrateDrawable = R.drawable.mascot_celebrate
    val runDrawable = R.drawable.mascot_run
    // 兼容旧引用：书签成功 = 欢呼
    val bookmarkDrawable = R.drawable.mascot_celebrate
}

/** 场景 -> 素材映射表（新绑定，旧匹配逻辑不再作为默认）。 */
object MascotAssetMap {
    val idle = MascotSpriteSheet.idleDrawable
    val sad = MascotSpriteSheet.sadDrawable
    val happy = MascotSpriteSheet.happyDrawable
    val move = MascotSpriteSheet.moveDrawable
    val reading = MascotSpriteSheet.readingDrawable
    val celebrate = MascotSpriteSheet.celebrateDrawable
    val run = MascotSpriteSheet.runDrawable
}

/** 吉祥物当前“情绪/动作”，用于静态图上的微动效。 */
enum class MascotMood { IDLE, READING, RUN, HAPPY, SAD }

/** 由素材 id 推断情绪，供空状态等组件选择动效。 */
fun mascotMoodOf(drawableId: Int): MascotMood = when (drawableId) {
    MascotSpriteSheet.readingDrawable -> MascotMood.READING
    MascotSpriteSheet.celebrateDrawable -> MascotMood.HAPPY
    MascotSpriteSheet.runDrawable -> MascotMood.RUN
    MascotSpriteSheet.sadDrawable -> MascotMood.SAD
    else -> MascotMood.IDLE
}
