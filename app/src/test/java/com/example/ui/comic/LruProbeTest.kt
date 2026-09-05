package com.example.ui.comic

import android.util.LruCache
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** android.util.LruCache 语义探针：get() 必须提升近期性（5.5 排查用） */
@RunWith(RobolectricTestRunner::class)
class LruProbeTest {
    @Test
    fun `get bumps recency`() {
        val evictedKeys = mutableListOf<String>()
        val c = object : LruCache<String, ByteArray>(3) {
            override fun sizeOf(key: String, value: ByteArray) = 1
            override fun entryRemoved(evicted: Boolean, key: String?, oldValue: ByteArray?, newValue: ByteArray?) {
                if (evicted) evictedKeys.add(key!!)
            }
        }
        c.put("a", ByteArray(1)); c.put("b", ByteArray(1)); c.put("c", ByteArray(1))
        c.get("a") // bump a → newest；顺序（旧→新）：b, c, a
        c.put("d", ByteArray(1)) // 应逐出 b（最旧），而不是 a
        assertEquals(listOf("b"), evictedKeys)
    }
}
