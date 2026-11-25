package com.hazel.android.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Wi-Fi or Ethernet: where the "Wi-Fi only" update setting lets a download run. */
internal fun onUnmeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
