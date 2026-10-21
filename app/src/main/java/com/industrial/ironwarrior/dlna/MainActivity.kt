package com.industrial.ironwarrior.dlna

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var ssdpDiscovery: SsdpDiscovery
    private lateinit var adapter: DeviceAdapter
    private val deviceList = mutableListOf<DlnaDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val btnScan = findViewById<Button>(R.id.btnScan)

        // Configuración ligera con RecyclerView tradicional (cero Compose overhead)
        adapter = DeviceAdapter(deviceList) { device -> showDeviceOptions(device) }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        ssdpDiscovery = SsdpDiscovery { device ->
            runOnUiThread {
                if (!deviceList.any { it.locationUrl == device.locationUrl }) {
                    deviceList.add(device)
                    adapter.notifyItemInserted(deviceList.size - 1)
                }
            }
        }

        btnScan.setOnClickListener {
            deviceList.clear()
            adapter.notifyDataSetChanged()
            ssdpDiscovery.startDiscovery()
        }

        // Inicio de escaneo automático sin bloquear hilo principal
        ssdpDiscovery.startDiscovery()
    }

    /**
     * Al tocar un dispositivo encontrado, pide la URL del video/audio y
     * ofrece: si es un MediaRenderer, hacer Cast real (UPnP AVTransport);
     * si es un MediaServer (o no se pudo identificar), reproducirlo local
     * en el celular con PlayerActivity.
     */
    private fun showDeviceOptions(device: DlnaDevice) {
        val input = EditText(this).apply {
            hint = "URL directa del video/audio (http://...)"
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(device.name)
            .setMessage("Tipo detectado: ${device.type}")
            .setView(input)
            .setNegativeButton("Cancelar", null)

        if (device.type == DlnaDeviceType.RENDERER || device.type == DlnaDeviceType.UNKNOWN) {
            builder.setPositiveButton("Transmitir (Cast)") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "Pegá una URL de video primero", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, "Enviando a ${device.name}…", Toast.LENGTH_SHORT).show()
                UpnpCastController.cast(device, url, "Ferro Stream") { _, message ->
                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
                }
            }
        }

        if (device.type == DlnaDeviceType.SERVER || device.type == DlnaDeviceType.UNKNOWN) {
            builder.setNeutralButton("Reproducir local") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "Pegá una URL de video primero", Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                startActivity(Intent(this, PlayerActivity::class.java).apply {
                    putExtra("MEDIA_URL", url)
                    putExtra("DEVICE_NAME", device.name)
                })
            }
        }

        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ssdpDiscovery.stop()
    }
}
