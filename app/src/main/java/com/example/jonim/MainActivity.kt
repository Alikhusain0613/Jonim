package com.example.jonim

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val clientVM: BluetoothViewModel by viewModels()
    private val serverVM: ServerViewModel by viewModels()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DeviceAdapter

    private lateinit var btnScan: Button
    private lateinit var btnStartServer: Button
    private lateinit var btnStopServer: Button
    private lateinit var etClientMsg: EditText
    private lateinit var btnSendClient: Button
    private lateinit var tvClientChat: TextView

    private lateinit var etServerMsg: EditText
    private lateinit var btnSendServer: Button
    private lateinit var tvServerChat: TextView
    private lateinit var tvAdvState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recyclerView)
        btnScan = findViewById(R.id.btnScan)
        btnStartServer = findViewById(R.id.btnStartServer)
        btnStopServer = findViewById(R.id.btnStopServer)

        etClientMsg = findViewById(R.id.etClientMsg)
        btnSendClient = findViewById(R.id.btnSendClient)
        tvClientChat = findViewById(R.id.tvClientChat)

        etServerMsg = findViewById(R.id.etServerMsg)
        btnSendServer = findViewById(R.id.btnSendServer)
        tvServerChat = findViewById(R.id.tvServerChat)
        tvAdvState = findViewById(R.id.tvAdvState)

        adapter = DeviceAdapter { device ->
            // ro‘yxatdan qurilma bosilganda client ulansin
            if (checkClientPermissions()) {
                clientVM.connectToDevice(device.device)
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnScan.setOnClickListener {
            if (!clientVM.isBluetoothEnabled()) {
                Toast.makeText(this, "Bluetooth yoqilmagan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkClientPermissions()) clientVM.startScan()
        }

        btnStartServer.setOnClickListener {
            if (checkServerPermissions()) serverVM.startServer()
        }
        btnStopServer.setOnClickListener {
            serverVM.stopServer()
        }

        btnSendClient.setOnClickListener {
            val txt = etClientMsg.text.toString()
            if (txt.isNotBlank()) {
                clientVM.sendMessage(txt)
                etClientMsg.text.clear()
            }
        }

        btnSendServer.setOnClickListener {
            val txt = etServerMsg.text.toString()
            if (txt.isNotBlank()) {
                serverVM.sendToSubscribers(txt)
                etServerMsg.text.clear()
            }
        }

        // Observers
        clientVM.devices.observe(this) { list -> adapter.submitList(list) }
        clientVM.messages.observe(this) { msgs ->
            tvClientChat.text = msgs.joinToString("\n") { msg ->
                if (msg.fromSelf) {
                    "You: ${msg.text}"
                } else {
                    "Server: ${msg.text}"
                }
            }
        }


        serverVM.messages.observe(this) { msgs ->
            tvServerChat.text = msgs.joinToString("\n") { msg ->
                if (msg.fromSelf) {
                    "You: ${msg.text}"   // serverning o‘zi yuborgan xabar
                } else {
                    "Client: ${msg.text}" // clientdan kelgan xabar
                }
            }
        }

        serverVM.isAdvertising.observe(this) { adv ->
            tvAdvState.text = if (adv) "Advertising: ON" else "Advertising: OFF"
        }
    }

    // ---- Permissions ----
    private fun checkClientPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return requestIfNeeded(needed)
    }

    private fun checkServerPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return requestIfNeeded(needed)
    }

    private fun requestIfNeeded(needed: List<String>): Boolean {
        return if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            false
        } else true
    }

    override fun onDestroy() {
        super.onDestroy()
        clientVM.stopScan()
        serverVM.stopServer()
    }
}
