package com.example.jonim

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*

data class Message(val text: String, val fromSelf: Boolean)

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = application.getSystemService(BluetoothManager::class.java)
        manager?.adapter
    }

    private var gatt: BluetoothGatt? = null
    private var messageChar: BluetoothGattCharacteristic? = null

    private val _devices = MutableLiveData<List<DeviceModel>>(emptyList())
    val devices: LiveData<List<DeviceModel>> = _devices
    private val found = mutableMapOf<String, DeviceModel>()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private var scanning = false

    // Chat UUIDs (server bilan mos bo‘lsin!)
    private val SERVICE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val MESSAGE_CHAR_UUID = UUID.fromString("0000dcba-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun addMsg(m: Message) {
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        list.add(m)
        _messages.postValue(list)
    }

    // ---------- SCAN ----------
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        found.clear()
        _devices.postValue(emptyList())

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        // ixtiyoriy: faqat bizning service UUID bilan advertising qilayotganlarni ko‘rsatmoqchi bo‘lsang, filter qo‘shish mumkin
        // lekin umumiy skan ham ishlaydi
        scanner.startScan(scanCallback)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val address = device.address
            val rssi = result.rssi
            found[address] = DeviceModel(name, address, rssi, device)
            _devices.postValue(found.values.toList())
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // ---------- CONNECT ----------
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        gatt?.close()
        _messages.postValue(emptyList())
        addMsg(Message("Connecting to ${device.address} ...", false))
        gatt = device.connectGatt(getApplication(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("CLIENT", "Connected, discovering services")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("CLIENT", "Disconnected")
                addMsg(Message("Disconnected", false))
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val svc = gatt.getService(SERVICE_UUID)
            messageChar = svc?.getCharacteristic(MESSAGE_CHAR_UUID)
            if (messageChar == null) {
                addMsg(Message("Chat characteristic not found", false))
                return
            }
            // NOTIFY yoqish: CCCD descriptorga yozish SHART!
            val cccd = messageChar!!.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                gatt.setCharacteristicNotification(messageChar, true)
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd) // -> server endi notify yubora oladi
            }
            addMsg(Message("Ready. You can send messages.", false))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == MESSAGE_CHAR_UUID) {
                val txt = characteristic.value?.decodeToString() ?: ""
                addMsg(Message(txt, false))
            }
        }
    }

    // ---------- SEND ----------
    @SuppressLint("MissingPermission")
    fun sendMessage(text: String) {
        val ch = messageChar ?: run {
            addMsg(Message("Not connected to chat characteristic", false))
            return
        }
        ch.value = text.toByteArray()
        val ok = gatt?.writeCharacteristic(ch) ?: false
        if (ok) addMsg(Message(text, true))
        else addMsg(Message("Send failed", false))
    }
}
