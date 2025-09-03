package com.example.jonim

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = application.getSystemService(BluetoothManager::class.java)
        manager?.adapter
    }

    private val _devices = MutableLiveData<List<DeviceModel>>(emptyList())
    val devices: LiveData<List<DeviceModel>> = _devices

    private val found = mutableMapOf<String, DeviceModel>()
    private var scanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    // GATT UUID’lar
    private val GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    private val DEVICE_NAME_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val address = device.address
            val rssi = result.rssi

            found[address] = DeviceModel(name, address, rssi, device)
            _devices.postValue(found.values.toList())

            // Agar nom null bo‘lsa → GATT orqali olish
            if (name == "Unknown") {
                connectForName(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        found.clear()
        _devices.postValue(emptyList())
        scanner.startScan(scanCallback)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    // GATT orqali Device Name olish
    @SuppressLint("MissingPermission")
    private fun connectForName(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(getApplication(), false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("GATT", "Connected to ${device.address}, discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("GATT", "Disconnected from ${device.address}")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(GENERIC_ACCESS_SERVICE)
                    val characteristic = service?.getCharacteristic(DEVICE_NAME_CHARACTERISTIC)

                    if (characteristic != null) {
                        gatt.readCharacteristic(characteristic)
                    } else {
                        Log.e("GATT", "Device Name characteristic not found!")
                        gatt.disconnect()
                        gatt.close()
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS &&
                    characteristic.uuid == DEVICE_NAME_CHARACTERISTIC
                ) {
                    val name = characteristic.value?.decodeToString() ?: "Unknown"
                    val address = gatt.device.address
                    val rssi = 0

                    Log.d("GATT", "Device name from GATT: $name")

                    found[address] = DeviceModel(name, address, rssi, gatt.device)
                    _devices.postValue(found.values.toList())

                    gatt.disconnect()
                    gatt.close()
                }
            }
        })
    }
}
