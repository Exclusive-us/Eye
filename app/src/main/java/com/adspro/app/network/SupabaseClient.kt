package com.adspro.app.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SupabaseClient(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val SUPABASE_URL = "https://bqddohfszkqpvncpbrun.supabase.co"
        const val REST_URL = "https://bqddohfszkqpvncpbrun.supabase.co/rest/v1"
        const val STORAGE_URL = "https://bqddohfszkqpvncpbrun.supabase.co/storage/v1"
        const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJxZGRvaGZzemtxcHZuY3BicnVuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI2MDAwNjIsImV4cCI6MjEwODE3NjA2Mn0.J0uq4JPpPxoU0hc4ry5WglCV_HcTqshcHsl3oCJ7upA"
        const val MEDIA_BUCKET = "adspro-media"
    }

    suspend fun insert(table: String, json: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject(json).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$REST_URL/$table")
            .post(body)
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $ANON_KEY")
            .header("Content-Type", "application/json")
            .build()

        runCatching {
            val response = httpClient.newCall(request).execute()
            val ok = response.isSuccessful
            response.close()
            ok
        }.getOrDefault(false)
    }

    suspend fun uploadFile(path: String, file: File, mimeType: String = "application/octet-stream"): String =
        withContext(Dispatchers.IO) {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("$STORAGE_URL/object/$MEDIA_BUCKET/$path")
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .post(requestBody)
                .build()

            runCatching {
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()
                if (response.isSuccessful) {
                    "$STORAGE_URL/object/public/$MEDIA_BUCKET/$path"
                } else {
                    Log.e("SupabaseClient", "Upload failed: $body")
                    ""
                }
            }.getOrDefault("")
        }

    suspend fun listMedia(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$STORAGE_URL/object/list/$MEDIA_BUCKET?prefix=${prefix.trim('/')}" )
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $ANON_KEY")
            .get()
            .build()

        runCatching {
            val response = httpClient.newCall(request).execute()
            val jsonText = response.body?.string() ?: "[]"
            response.close()
            if (!response.isSuccessful) return@runCatching emptyList<String>()

            val array = JSONArray(jsonText)
            val items = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val objectJson = array.getJSONObject(i)
                val name = objectJson.optString("name").ifBlank { "" }
                if (name.isNotBlank()) {
                    items.add("$STORAGE_URL/object/public/$MEDIA_BUCKET/$name")
                }
            }
            items
        }.getOrDefault(emptyList())
    }
}
