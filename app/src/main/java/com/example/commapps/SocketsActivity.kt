@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.commapps

import android.Manifest
import android.os.Bundle
import android.telephony.CellInfoLte
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.commapps.ui.theme.CommAppsTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.zeromq.SocketType
import org.zeromq.ZContext

class SocketActivity : ComponentActivity() {
    private val logTag = "SOCKETSSS"
    private val uiText = mutableStateOf("Ожидание сообщений...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startSocketLoop()
        setContent {
            CommAppsTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Sockets(uiText)
                }
            }
        }
    }

    private fun startSocketLoop() {
        lifecycleScope.launch(Dispatchers.IO) @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]) {
            val ctx = ZContext()
            val socket = ctx.createSocket(SocketType.REQ)
            try {
                socket.connect("tcp://server.cloud-ip.cc:36513")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiText.value = "Ошибка подключения: ${e.message}"
                }
                return@launch
            }


            try {
                while (isActive) {
                    try {
                        var last_time: Long = 0L

                        val telemetry = collectTelemetry()
                        val timestamp = telemetry.optLong("timestamp", 0L)
                        val payload = collectTelemetry().toString()
                        if (timestamp == last_time) {
                        } else {
                            last_time = timestamp

                            socket.send(payload, 0)
                            val reply = socket.recvStr(0) ?: ""
                            withContext(Dispatchers.Main) {
                                uiText.value = "Ответ сервера: $reply"
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            uiText.value = "Ошибка отправки: ${e.message}"
                        }
                    }
                    delay(2500)
                }
            } finally {
                socket.close()
                ctx.close()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun collectTelemetry(): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@SocketActivity)
        val cts = CancellationTokenSource()

        val loc = try {
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()
        } catch (e: Exception) {
            null
        }

        result.put("latitude", loc?.latitude ?: JSONObject.NULL)
        result.put("longitude", loc?.longitude ?: JSONObject.NULL)
        result.put("altitude", loc?.altitude ?: JSONObject.NULL)
        result.put("timestamp", loc?.time ?: JSONObject.NULL)
        result.put("speed", loc?.speed ?: JSONObject.NULL)
        result.put("accuracy", loc?.accuracy ?: JSONObject.NULL)

        val telephony = getSystemService(android.telephony.TelephonyManager::class.java)
        val cellInfoList = telephony.allCellInfo
        var lte: CellInfoLte? = null
        for (ci in cellInfoList) {
            if (ci is CellInfoLte && ci.isRegistered) {
                lte = ci
                break
            }
        }

        if (lte != null) {
            val id = lte.cellIdentity
            val ss = lte.cellSignalStrength
            result.put("cellIdentity", JSONObject().apply {
                put("mcc", id.mccString ?: JSONObject.NULL)
                put("mnc", id.mncString ?: JSONObject.NULL)
                put("tac", id.tac)
                put("pci", id.pci)
                put("earfcn", id.earfcn)
            })
            result.put("band", id.bandwidth)
            result.put("rsrp", ss.rsrp)
            result.put("rsrq", ss.rsrq)
            result.put("rssnr", ss.rssnr)
            result.put("rssi", ss.dbm)
            result.put("cqi", ss.cqi)
            result.put("asuLevel", ss.asuLevel)
            result.put("timing_advance", ss.timingAdvance)
        } else {
            result.put("cellIdentity", JSONObject.NULL)
            result.put("band", JSONObject.NULL)
            result.put("rsrp", JSONObject.NULL)
            result.put("rsrq", JSONObject.NULL)
            result.put("rssnr", JSONObject.NULL)
            result.put("rssi", JSONObject.NULL)
            result.put("cqi", JSONObject.NULL)
            result.put("asuLevel", JSONObject.NULL)
            result.put("timing_advance", JSONObject.NULL)
        }

        result
    }



    @Composable
    fun Sockets(textState: MutableState<String>) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                textState.value,
                color = MaterialTheme.colorScheme.inversePrimary,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
