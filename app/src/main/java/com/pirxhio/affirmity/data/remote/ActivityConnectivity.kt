package com.pirxhio.affirmity.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** A wakeup hint only; upload acknowledgement, not connectivity, establishes saved state. */
fun observeActivityConnectivity(context: Context) = callbackFlow {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(Unit) }
    }
    try { manager.registerDefaultNetworkCallback(callback) }
    catch (_: SecurityException) { close(); return@callbackFlow }
    awaitClose { manager.unregisterNetworkCallback(callback) }
}
