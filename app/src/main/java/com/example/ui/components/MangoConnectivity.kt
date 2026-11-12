package com.example.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Connection state is advisory; local records remain available without a network. */
@Composable
fun rememberMangoOnline(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    fun connected(): Boolean = runCatching {
        val network = manager.activeNetwork ?: return@runCatching false
        val caps = manager.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
    var online by remember(manager) { mutableStateOf(connected()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = connected() }
            override fun onLost(network: Network) { online = connected() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online = connected()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        online = connected()
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}
