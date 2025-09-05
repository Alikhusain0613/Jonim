package com.example.jonim

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

@SuppressLint("NewApi")
class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val btManager: BluetoothManager by lazy {
        application.getSystemService(BluetoothManager::class.java)
    }
    private val btAdapter: BluetoothAdapter by lazy {
        btManager.adapter
    }

    private var gattServer: BluetoothGattServer? = null
    private val subscribers = CopyOnWriteArraySet<BluetoothDevice>() // NOTIFY uchun obunachilar

    private val _isAdvertising = MutableLiveData(false)
    val isAdvertising: LiveData<Boolean> = _isAdvertising

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private fun addMsg(m: Message) {
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        list.add(m)
        _messages.postValue(list)
    }

    // UUID lar — client bilan bir xil bo‘lsin
    private val SERVICE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val MESSAGE_CHAR_UUID = UUID.fromString("0000dcba-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --------- Serverni ishga tushirish ----------
    @SuppressLint("MissingPermission")
    fun startServer() {
        stopServer() // tozalab ol

        // 1) GATT server ochish
        gattServer = btManager.openGattServer(getApplication(), serverCallback)
        if (gattServer == null) {
            addMsg(Message("Failed to open GATT server", false))
            return
        }

        // 2) Service va Characteristic yaratish
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val msgChar = BluetoothGattCharacteristic(
            MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // 3) CCCD descriptor — NOTIFY uchun majburiy
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        msgChar.addDescriptor(cccd)
        service.addCharacteristic(msgChar)

        val ok = gattServer!!.addService(service)
        if (!ok) {
            addMsg(Message("addService failed", false))
            stopServer()
            return
        }

        // 4) Advertising boshlash
        val advertiser = btAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            addMsg(Message("BLE Advertiser not available", false))
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // qurilma nomini ko‘rsatadi
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        _isAdvertising.postValue(true)
        addMsg(Message("Server started & advertising", false))
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            btAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) { }
        subscribers.clear()
        gattServer?.close()
        gattServer = null
        _isAdvertising.postValue(false)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("SERVER", "Advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("SERVER", "Advertising failed: $errorCode")
            addMsg(Message("Advertising failed: $errorCode", false))
            _isAdvertising.postValue(false)
        }
    }

    // --------- Server Callback ----------
    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("SERVER", "Conn state change: ${device.address} -> $newState")
            if (newState != BluetoothProfile.STATE_CONNECTED) {
                subscribers.remove(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                // Client NOTIFY yoqdi/ochirdi
                val enable = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                if (enable) subscribers.add(device) else subscribers.remove(device)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                Log.d("SERVER", "CCCD write: enable=$enable by ${device.address}")
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESSAGE_CHAR_UUID) {
                val txt = value.decodeToString()
                addMsg(Message(" $txt",false))
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                // istasang echo qaytarish (notify) ham mumkin:
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }

    // --------- Serverdan xabar yuborish (NOTIFY) ----------
    @SuppressLint("MissingPermission")
    fun sendToSubscribers(text: String) {
        val service = gattServer?.getService(SERVICE_UUID) ?: return
        val ch = service.getCharacteristic(MESSAGE_CHAR_UUID) ?: return
        ch.value = text.toByteArray()
        // barcha obunachilarga notify
        for (d in subscribers) {
            gattServer?.notifyCharacteristicChanged(d, ch, false)
        }
        addMsg(Message(" $text", true))
    }
}
