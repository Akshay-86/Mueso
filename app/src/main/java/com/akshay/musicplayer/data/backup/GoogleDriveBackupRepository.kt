@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.data.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveBackupInfo(
    val fileId: String,
    val modifiedTime: String,
    val sizeBytes: Long
)

class GoogleDriveBackupRepository(private val context: Context) {

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.appdata")

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(MuesoBackupData::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(context: Context): Intent {
        return getGoogleSignInClient(context).signInIntent
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun getAccessToken(email: String): String? = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            android.util.Log.e("MUESO_AUTH", "getAccessToken: email is blank")
            throw Exception("Email address is required")
        }
        val scopeStr = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        val androidAccount = android.accounts.Account(email, "com.google")
        android.util.Log.d("MUESO_AUTH", "Requesting OAuth token for account: $email")
        try {
            GoogleAuthUtil.getToken(context, androidAccount, scopeStr)
        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
            android.util.Log.w("MUESO_AUTH", "UserRecoverableAuthException: launching consent intent for $email")
            try {
                e.intent?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (ex: Exception) {
                android.util.Log.w("MUESO_AUTH", "Failed to launch consent intent: ${ex.message}")
            }
            throw Exception("Google account authorization pending. Please check consent dialog.")
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            android.util.Log.e("MUESO_AUTH", "GoogleAuthException: ${e.message}", e)
            if (e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true) {
                throw Exception("Google Drive API Client configuration required for package com.akshay.musicplayer.")
            }
            throw Exception("Google authentication error: ${e.message ?: "Auth failed"}")
        } catch (e: java.io.IOException) {
            android.util.Log.e("MUESO_AUTH", "Network error during GoogleAuthUtil.getToken: ${e.message}", e)
            throw Exception("Network error connecting to Google services: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("MUESO_AUTH", "GoogleAuthUtil.getToken failed for $email: ${e.message}", e)
            throw Exception("Google authorization failed: ${e.message}")
        }
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? {
        return getAccessToken(account.email ?: "")
    }

    fun invalidateToken(token: String) {
        if (token.isBlank()) return
        try {
            GoogleAuthUtil.clearToken(context, token)
            android.util.Log.d("MUESO_AUTH", "Cleared expired OAuth token from GoogleAuthUtil cache")
        } catch (e: Exception) {
            android.util.Log.w("MUESO_AUTH", "Failed to clear token from GoogleAuthUtil cache: ${e.message}")
        }
    }

    suspend fun deleteBackupFile(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || fileId.isBlank()) return@withContext false
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    invalidateToken(accessToken)
                }
                response.isSuccessful || response.code == 404
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun findBackupFile(accessToken: String): DriveBackupInfo? = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) return@withContext null
        try {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name='mueso_backup.json' and trashed=false")
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("fields", "files(id,name,modifiedTime,size)")
                .build()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    invalidateToken(accessToken)
                    android.util.Log.w("MUESO_AUTH", "findBackupFile: token expired (401), invalidated token")
                    return@withContext null
                }
                if (!response.isSuccessful) {
                    android.util.Log.w("MUESO_AUTH", "findBackupFile: non-successful response code ${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = try {
                    JSONObject(bodyStr)
                } catch (e: Exception) {
                    android.util.Log.e("MUESO_AUTH", "findBackupFile: failed to parse JSON response", e)
                    return@withContext null
                }
                val files = json.optJSONArray("files") ?: return@withContext null
                if (files.length() == 0) return@withContext null

                // Find best backup file: prefer non-empty files
                var bestIndex = 0
                for (i in 0 until files.length()) {
                    val f = files.optJSONObject(i) ?: continue
                    if (f.optLong("size", 0L) > 0L) {
                        bestIndex = i
                        break
                    }
                }

                // Purge older duplicate backup files or 0-byte corrupt files in appDataFolder
                if (files.length() > 1) {
                    for (i in 0 until files.length()) {
                        if (i != bestIndex) {
                            val staleId = files.optJSONObject(i)?.optString("id")
                            if (!staleId.isNullOrEmpty()) {
                                deleteBackupFile(accessToken, staleId)
                            }
                        }
                    }
                }

                val fileObj = files.optJSONObject(bestIndex) ?: return@withContext null
                val fileId = fileObj.optString("id")
                if (fileId.isBlank()) return@withContext null
                DriveBackupInfo(
                    fileId = fileId,
                    modifiedTime = fileObj.optString("modifiedTime", ""),
                    sizeBytes = fileObj.optLong("size", 0L)
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MUESO_AUTH", "findBackupFile exception: ${e.message}")
            null
        }
    }

    suspend fun uploadBackup(email: String, data: MuesoBackupData): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var token = getAccessToken(email) ?: return@withContext Result.failure(Exception("Failed to get Google OAuth Access Token"))
            var existingFile = findBackupFile(token)

            val jsonContent = backupAdapter.toJson(data)
            val jsonRequestBody = jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

            suspend fun doUpload(currentToken: String, file: DriveBackupInfo?): Pair<Int, Boolean> {
                return if (file != null) {
                    val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/${file.fileId}?uploadType=media"
                    val request = Request.Builder()
                        .url(updateUrl)
                        .addHeader("Authorization", "Bearer $currentToken")
                        .patch(jsonRequestBody)
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.code == 401) {
                            invalidateToken(currentToken)
                            Pair(401, false)
                        } else if (response.isSuccessful) {
                            Pair(response.code, true)
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("MUESO_AUTH", "Upload PATCH failed: code=${response.code}, body=$errorBody")
                            Pair(response.code, false)
                        }
                    }
                } else {
                    val metadataJson = JSONObject().apply {
                        put("name", "mueso_backup.json")
                        put("parents", org.json.JSONArray().put("appDataFolder"))
                    }.toString()

                    val multipartBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(jsonRequestBody)
                        .build()

                    val createMultipartRequest = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .addHeader("Authorization", "Bearer $currentToken")
                        .post(multipartBody)
                        .build()

                    httpClient.newCall(createMultipartRequest).execute().use { response ->
                        if (response.code == 401) {
                            invalidateToken(currentToken)
                            Pair(401, false)
                        } else if (response.isSuccessful) {
                            Pair(response.code, true)
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("MUESO_AUTH", "Upload CREATE MULTIPART failed: code=${response.code}, body=$errorBody")
                            Pair(response.code, false)
                        }
                    }
                }
            }

            var (code, success) = doUpload(token, existingFile)
            if (code == 401) {
                // Token expired - try once with fresh token
                val freshToken = try { getAccessToken(email) } catch (e: Exception) { null }
                if (!freshToken.isNullOrBlank() && freshToken != token) {
                    token = freshToken
                    existingFile = findBackupFile(token)
                    val retry = doUpload(token, existingFile)
                    code = retry.first
                    success = retry.second
                }
            }

            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Upload backup failed with HTTP code $code"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MUESO_AUTH", "uploadBackup exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(account: GoogleSignInAccount, data: MuesoBackupData): Result<Boolean> {
        return uploadBackup(account.email ?: "", data)
    }

    suspend fun downloadBackup(email: String): Result<MuesoBackupData> = withContext(Dispatchers.IO) {
        try {
            var token = getAccessToken(email) ?: return@withContext Result.failure(Exception("Failed to get Google OAuth Access Token"))
            var existingFile = findBackupFile(token)
            if (existingFile == null) {
                val freshToken = try { getAccessToken(email) } catch (e: Exception) { null }
                if (!freshToken.isNullOrBlank() && freshToken != token) {
                    token = freshToken
                    existingFile = findBackupFile(token)
                }
            }
            if (existingFile == null) {
                return@withContext Result.failure(Exception("No backup file found in Google Drive"))
            }

            suspend fun doDownload(currentToken: String, fileId: String): Triple<Int, Boolean, String?> {
                val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $currentToken")
                    .get()
                    .build()

                return httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        invalidateToken(currentToken)
                        Triple(401, false, null)
                    } else if (!response.isSuccessful) {
                        android.util.Log.e("MUESO_BACKUP", "Download file failed: HTTP ${response.code}")
                        Triple(response.code, false, null)
                    } else {
                        val jsonStr = response.body?.string()
                        Triple(200, true, jsonStr)
                    }
                }
            }

            var (code, success, jsonStr) = doDownload(token, existingFile.fileId)
            if (code == 401) {
                val freshToken = try { getAccessToken(email) } catch (e: Exception) { null }
                if (!freshToken.isNullOrBlank() && freshToken != token) {
                    token = freshToken
                    val retry = doDownload(token, existingFile.fileId)
                    code = retry.first
                    success = retry.second
                    jsonStr = retry.third
                }
            }

            if (!success || jsonStr.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Download failed with code $code"))
            }

            android.util.Log.d("MUESO_BACKUP", "Downloaded JSON string from Drive (${jsonStr.length} bytes)")
            val parsed = try {
                backupAdapter.fromJson(jsonStr)
            } catch (e: Exception) {
                android.util.Log.e("MUESO_BACKUP", "Failed to parse backup JSON: ${e.message}", e)
                null
            } ?: return@withContext Result.failure(Exception("Failed to parse backup JSON"))

            Result.success(parsed)
        } catch (e: Exception) {
            android.util.Log.e("MUESO_BACKUP", "downloadBackup exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(account: GoogleSignInAccount): Result<MuesoBackupData> {
        return downloadBackup(account.email ?: "")
    }

    suspend fun deleteBackup(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var token = getAccessToken(email)
                ?: return@withContext Result.failure(Exception("Failed to get Google OAuth Access Token"))
            var existingFile = findBackupFile(token)
            if (existingFile == null) {
                val freshToken = try { getAccessToken(email) } catch (e: Exception) { null }
                if (!freshToken.isNullOrBlank() && freshToken != token) {
                    token = freshToken
                    existingFile = findBackupFile(token)
                }
            }
            if (existingFile == null) {
                return@withContext Result.success(true)
            }

            suspend fun doDelete(currentToken: String, fileId: String): Int {
                val url = "https://www.googleapis.com/drive/v3/files/$fileId"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $currentToken")
                    .delete()
                    .build()

                return httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        invalidateToken(currentToken)
                    }
                    response.code
                }
            }

            var code = doDelete(token, existingFile.fileId)
            if (code == 401) {
                val freshToken = try { getAccessToken(email) } catch (e: Exception) { null }
                if (!freshToken.isNullOrBlank() && freshToken != token) {
                    token = freshToken
                    code = doDelete(token, existingFile.fileId)
                }
            }

            if (code == 200 || code == 204 || code == 404) {
                Result.success(true)
            } else {
                Result.failure(Exception("Delete backup failed with code $code"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MUESO_AUTH", "deleteBackup exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(account: GoogleSignInAccount): Result<Boolean> {
        return deleteBackup(account.email ?: "")
    }
}
