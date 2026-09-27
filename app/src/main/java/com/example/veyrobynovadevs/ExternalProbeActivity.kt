package com.example.veyrobynovadevs

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class ExternalProbeActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external_probe)

        statusText = findViewById(R.id.tv_status)
        val btnProbe = findViewById<Button>(R.id.btn_probe)

        btnProbe.setOnClickListener {
            statusText.text = "Probing..."
            CoroutineScope(Dispatchers.IO).launch {
                val result1 = probeUrl("https://1.1.1.1/")
                val result2 = probeUrl("https://www.google.com/")

                withContext(Dispatchers.Main) {
                    statusText.text = "1.1.1.1: $result1\nGoogle: $result2"
                }
            }
        }
    }

    private fun probeUrl(urlString: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            var responseCode = 0
            val time = measureTimeMillis {
                connection = url.openConnection() as HttpURLConnection
                connection?.requestMethod = "GET"
                connection?.connectTimeout = 5000
                connection?.readTimeout = 5000
                responseCode = connection?.responseCode ?: 0
            }
            "Success ($responseCode) in ${time}ms"
        } catch (e: Exception) {
            Log.e("ExternalProbe", "Failed to probe $urlString", e)
            "Failed: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }
}
