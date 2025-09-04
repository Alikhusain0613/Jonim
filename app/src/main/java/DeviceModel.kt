package com.example.jonim

import android.bluetooth.BluetoothDevice

data class DeviceModel(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)
