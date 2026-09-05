package com.akshay.musicplayer.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val OrangeAccent = Color(0xFFFF512F)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    cleanSession: Boolean = false,
    onDismiss: () -> Unit,
    onLoginSuccess: (cookieString: String, userName: String?) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect YouTube Music",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in to sync your Liked Songs & Playlists",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OrangeAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = {
                            val cm = CookieManager.getInstance()
                            cm.removeAllCookies {
                                cm.flush()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                webViewInstance?.clearCache(true)
                                webViewInstance?.clearHistory()
                                webViewInstance?.loadUrl("https://accounts.google.com/AddSession?continue=https://music.youtube.com/")
                            }
                            Toast.makeText(context, "Session cleared. Sign into another account", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Switch Account",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        super.onPageFinished(view, url)

                                        val currentUrl = url ?: ""
                                        if (currentUrl.contains("music.youtube.com")) {
                                            val ytmCookies = cookieManager.getCookie("https://music.youtube.com") ?: ""
                                            if (ytmCookies.contains("SAPISID") || ytmCookies.contains("__Secure-1PAPISID") || ytmCookies.contains("SID")) {
                                                Log.d("MUESO_YTM_LOGIN", "Authenticated cookies detected on music.youtube.com")
                                                Toast.makeText(context, "YouTube Music Connected Successfully!", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess(ytmCookies, null)
                                                onDismiss()
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val nextUrl = request?.url?.toString() ?: ""
                                        if (nextUrl.contains("music.youtube.com")) {
                                            val cookies = cookieManager.getCookie("https://music.youtube.com") ?: ""
                                            if (cookies.contains("SAPISID") || cookies.contains("__Secure-1PAPISID") || cookies.contains("SID")) {
                                                Log.d("MUESO_YTM_LOGIN", "Captured valid auth cookies before redirect to music.youtube.com")
                                                Toast.makeText(context, "YouTube Music Connected Successfully!", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess(cookies, null)
                                                onDismiss()
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }

                                if (cleanSession) {
                                    cookieManager.removeAllCookies {
                                        cookieManager.flush()
                                        android.webkit.WebStorage.getInstance().deleteAllData()
                                        loadUrl("https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com/")
                                    }
                                } else {
                                    loadUrl("https://accounts.google.com/AccountChooser?continue=https://music.youtube.com/")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
