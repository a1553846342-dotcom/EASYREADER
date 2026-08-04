package com.example.source.zlibrary.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/**
 * Resolves the system HTTP proxy (the same one browsers/WebView use).
 *
 * In CN networks with a proxy tool (Clash etc.), WebView can reach Z-Library while a plain
 * OkHttp client cannot, because OkHttp's JVM/Android default does not reliably pick up the
 * system proxy. Explicitly applying it makes the app's HTTP path behave like the WebView.
 */
object SystemProxyResolver {

    fun resolve(context: Context?): Proxy? {
        return resolveInternal(context)
    }

    private fun resolveInternal(context: Context?): Proxy? {
        // Android: the real system proxy used by browsers/WebView
        if (context != null && Build.VERSION.SDK_INT >= 23) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val info = cm?.defaultProxy
                if (info != null && !info.host.isNullOrBlank() && info.port > 0) {
                    return Proxy(Proxy.Type.HTTP, InetSocketAddress(info.host, info.port))
                }
            } catch (e: Exception) {
                // fall through to the java default selector
            }
        }

        // Fallback (JVM tests / older Android): java default proxy selector.
        // Note: on the JVM this honors the OS proxy only when
        // "java.net.useSystemProxies" is enabled.
        return try {
            ProxySelector.getDefault()
                ?.select(URI("https://zlib.invalid"))
                ?.firstOrNull { it.type() == Proxy.Type.HTTP }
        } catch (e: Exception) {
            null
        }
    }
}
