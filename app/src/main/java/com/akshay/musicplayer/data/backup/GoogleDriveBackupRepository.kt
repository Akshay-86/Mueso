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
                ex.printStackTrace()
            }
            throw Exception("Google account authorization pending. Please check consent dialog.")
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            android.util.Log.e("MUESO_AUTH", "GoogleAuthException: ${e.message}", e)
            if (e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true) {
                throw Exception("Google Drive API Client configuration required for package com.akshay.musicplayer.")
            }
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MUESO_AUTH", "GoogleAuthUtil.getToken failed for $email: ${e.message}", e)
            throw e
        }
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? {
        return getAccessToken(account.email ?: "")
    }

    suspend fun findBackupFile(accessToken: String): DriveBackupInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='mueso_backup.json'&fields=files(id,name,modifiedTime,size)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files") ?: return@withContext null
                if (files.length() == 0) return@withContext null

                val fileObj = files.getJSONObject(0)
                DriveBackupInfo(
                    fileId = fileObj.getString("id"),
                    modifiedTime = fileObj.optString("modifiedTime", ""),
                    sizeBytes = fileObj.optLong("size", 0L)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadBackup(email: String, data: MuesoBackupData): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken(email) ?: return@withContext Result.failure(Exception("Failed to get Google OAuth Access Token"))
            val existingFile = findBackupFile(token)

            val jsonContent = backupAdapter.toJson(data)
            val jsonRequestBody = jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

            if (existingFile != null) {
                // Update existing file in appDataFolder
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/${existingFile.fileId}?uploadType=media"
                val request = Request.Builder()
                    .url(updateUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(jsonRequestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(true)
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        android.util.Log.e("MUESO_AUTH", "Upload PATCH failed: code=${response.code}, body=$errorBody")
                        Result.failure(Exception("Upload failed with code ${response.code}: $errorBody"))
                    }
                }
            } else {
                // Create new file metadata in appDataFolder
                val metadataJson = JSONObject().apply {
                    put("name", "mueso_backup.json")
                    put("parents", org.json.JSONArray().put("appDataFolder"))
                }.toString()

                val createMetadataRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .addHeader("Authorization", "Bearer $token")
                    .post(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val fileId = httpClient.newCall(createMetadataRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        android.util.Log.e("MUESO_AUTH", "Upload CREATE METADATA failed: code=${response.code}, body=$errorBody")
                        return@withContext Result.failure(Exception("Create metadata failed with code ${response.code}: $errorBody"))
                    }
                    val respBody = response.body?.string() ?: ""
                    JSONObject(respBody).optString("id", "")
                }

                if (fileId.isEmpty()) {
                    return@withContext Result.failure(Exception("Failed to extract file ID after creation"))
                }

                // Upload content to the newly created file
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val uploadRequest = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(jsonRequestBody)
                    .build()

                httpClient.newCall(uploadRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(true)
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        android.util.Log.e("MUESO_AUTH", "Upload CREATE MEDIA failed: code=${response.code}, body=$errorBody")
                        Result.failure(Exception("Create media failed with code ${response.code}: $errorBody"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(account: GoogleSignInAccount, data: MuesoBackupData): Result<Boolean> {
        return uploadBackup(account.email ?: "", data)
    }

    suspend fun downloadBackup(email: String): Result<MuesoBackupData> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken(email) ?: return@withContext Result.failure(Exception("Failed to get Google OAuth Access Token"))
            val existingFile = findBackupFile(token) ?: return@withContext Result.failure(Exception("No backup file found in Google Drive"))

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/${existingFile.fileId}?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("MUESO_BACKUP", "Download file failed: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("Download failed with code ${response.code}"))
                }
                val jsonStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty backup file body"))
                android.util.Log.d("MUESO_BACKUP", "Downloaded JSON string from Drive (${jsonStr.length} bytes): $jsonStr")
                val parsed = backupAdapter.fromJson(jsonStr)
                    ?: return@withContext Result.failure(Exception("Failed to parse backup JSON"))
                android.util.Log.d("MUESO_BACKUP", "Successfully parsed backup object: $parsed")
                Result.success(parsed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(account: GoogleSignInAccount): Result<MuesoBackupData> {
        return downloadBackup(account.email ?: "")
    }
}
