package com.example.library

/**
 * 当前使用的 Z-Library 节点（可在诊断页一键切换）。
 * 书库隐藏会话实时读取该值，切换后立即生效。
 */
object ZLibraryNodeConfig {
    @Volatile
    var domain: String = "1lib.sk"
}
