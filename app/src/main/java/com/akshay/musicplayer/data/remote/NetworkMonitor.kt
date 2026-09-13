package com.akshay.musicplayer.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

object NetworkMonitor {
    private const val TAG = "MUESO_NET"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectivityManager: ConnectivityManager? = null
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkRestoredListeners = CopyOnWriteArrayList<() -> Unit>()
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext ?: context
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm

        val initialOnline = checkCurrentOnline(cm)
        _isOnline.value = initialOnline
        Log.d(TAG, "NetworkMonitor initialized, initial isOnline: $initialOnline")

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network onAvailable: $network")
                mainHandler.post {
                    val online = checkCurrentOnline(connectivityManager)
                    updateOnlineStatus(online)
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network onLost: $network")
                mainHandler.post {
                    val online = checkCurrentOnline(connectivityManager)
                    updateOnlineStatus(online)
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val online = hasInternet && isValidated
                Log.d(TAG, "Network onCapabilitiesChanged: hasInternet=$hasInternet, isValidated=$isValidated -> online=$online")
                mainHandler.post {
                    updateOnlineStatus(online)
                }
            }
        }

        try {
            cm?.registerDefaultNetworkCallback(callback)
            Log.d(TAG, "Successfully registered default network callback")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to registerDefaultNetworkCallback, falling back to NetworkRequest", e)
            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm?.registerNetworkCallback(request, callback)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to registerNetworkCallback", ex)
            }
        }
    }

    private fun checkCurrentOnline(cm: ConnectivityManager?): Boolean {
        if (cm == null) return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isConnected(): Boolean {
        val current = checkCurrentOnline(connectivityManager)
        if (current != _isOnline.value) {
            _isOnline.value = current
        }
        return current
    }

    private fun updateOnlineStatus(newStatus: Boolean) {
        val oldStatus = _isOnline.value
        _isOnline.value = newStatus
        if (!oldStatus && newStatus) {
            Log.i(TAG, ">>> INTERNET RESTORED! Triggering ${networkRestoredListeners.size} listeners <<<")
            for (listener in networkRestoredListeners) {
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in network restored listener", e)
                }
            }
        }
    }

    fun addOnNetworkRestoredListener(listener: () -> Unit) {
        if (!networkRestoredListeners.contains(listener)) {
            networkRestoredListeners.add(listener)
        }
    }

    fun removeOnNetworkRestoredListener(listener: () -> Unit) {
        networkRestoredListeners.remove(listener)
    }
}
