package com.example.phoneactivity

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

object NetworkHelper {
    private const val SERVER_URL = "https://tools-ios-01.onrender.com/log"

    private val client = OkHttpClient()

    fun sendLogToServer(log: String) {
        val json = """{"log":"$log"}"""
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)

        val request = Request.Builder()
            .url(SERVER_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkHelper", "❌ Failed to send log: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("NetworkHelper", "✅ Log sent successfully")
                } else {
                    Log.e("NetworkHelper", "⚠️ Server error: ${response.code}")
                }
                response.close()
            }
        })
    }
}
