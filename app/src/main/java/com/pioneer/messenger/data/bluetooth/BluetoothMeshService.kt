package com.pioneer.messenger.data.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import com.pioneer.messenger.data.crypto.CryptoManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.*
import javax.inject.Inject

/**
 * Bluetooth Mesh для оффлайн общения
 * Позволяет передавать сообщения между устройствами без интернета
 */
@AndroidEntryPoint
class BluetoothMeshService : Service() {
    
    @Inject lateinit var cryptoManager: CryptoManager
    
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()
    private val messageQueue = mutableListOf<MeshMessage>()
    
    private val _incomingMessages = MutableSharedFlow<MeshMessage>()
    val incomingMessages: SharedFlow<MeshMessage> = _incomingMessages
    
    private val _nearbyDevices = MutableSharedFlow<List<NearbyDevice>>()
    val nearbyDevices: SharedFlow<List<NearbyDevice>> = _nearbyDevices
    
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val MESSAGE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")
        val DEVICE_INFO_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892")
        
        private const val MAX_HOP_COUNT = 5
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothMeshService = this@BluetoothMeshService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        initBluetooth()
    }
    
    @SuppressLint("MissingPermission")
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }
    
    @SuppressLint("MissingPermission")
    fun startMesh(userId: String) {
        startAdvertising(userId)
        startScanning()
        setupGattServer()
    }
    
    @SuppressLint("MissingPermission")
    private fun startAdvertising(userId: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // Advertising started
        }
        
        override fun onStartFailure(errorCode: Int) {
            // Handle error
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            
            scope.launch {
                val nearbyDevice = NearbyDevice(
                    address = device.address,
                    name = device.name ?: "Unknown",
                    rssi = rssi,
                    lastSeen = System.currentTimeMillis()
                )
                
                // Подключаемся к новым устройствам
                if (!connectedDevices.containsKey(device.address)) {
                    connectToDevice(device)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(this, false, gattCallback)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[gatt.device.address] = gatt.device
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(gatt.device.address)
                    gatt.close()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Подписываемся на характеристику сообщений
                val service = gatt.getService(SERVICE_UUID)
                val messageChar = service?.getCharacteristic(MESSAGE_CHAR_UUID)
                messageChar?.let {
                    gatt.setCharacteristicNotification(it, true)
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESSAGE_CHAR_UUID) {
                scope.launch {
                    handleIncomingMessage(value)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val messageChar = BluetoothGattCharacteristic(
            MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or 
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        service.addCharacteristic(messageChar)
        gattServer?.addService(service)
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
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
                scope.launch {
                    handleIncomingMessage(value)
                }
            }
            
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }
    
    private suspend fun handleIncomingMessage(data: ByteArray) {
        try {
            val message = MeshMessage.fromBytes(data)
            
            // Проверяем, не обрабатывали ли мы это сообщение
            if (messageQueue.any { it.id == message.id }) return
            
            messageQueue.add(message)
            _incomingMessages.emit(message)
            
            // Ретранслируем, если не достигнут лимит хопов
            if (message.hopCount < MAX_HOP_COUNT) {
                val forwardMessage = message.copy(hopCount = message.hopCount + 1)
                broadcastMessage(forwardMessage)
            }
        } catch (e: Exception) {
            // Invalid message
        }
    }
    
    @SuppressLint("MissingPermission")
    fun sendMessage(message: MeshMessage) {
        scope.launch {
            messageQueue.add(message)
            broadcastMessage(message)
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun broadcastMessage(message: MeshMessage) {
        val data = message.toBytes()
        
        connectedDevices.values.forEach { device ->
            // Отправляем через GATT
            // В реальной реализации нужно управлять GATT соединениями
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopMesh() {
        bluetoothLeScanner?.stopScan(scanCallback)
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        connectedDevices.clear()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMesh()
        scope.cancel()
    }
}

data class MeshMessage(
    val id: String,
    val senderId: String,
    val recipientId: String?,  // null = broadcast
    val encryptedContent: ByteArray,
    val timestamp: Long,
    val hopCount: Int = 0
) {
    fun toBytes(): ByteArray {
        // Сериализация сообщения
        return "$id|$senderId|${recipientId ?: ""}|${android.util.Base64.encodeToString(encryptedContent, android.util.Base64.NO_WRAP)}|$timestamp|$hopCount"
            .toByteArray()
    }
    
    companion object {
        fun fromBytes(data: ByteArray): MeshMessage {
            val parts = String(data).split("|")
            return MeshMessage(
                id = parts[0],
                senderId = parts[1],
                recipientId = parts[2].ifEmpty { null },
                encryptedContent = android.util.Base64.decode(parts[3], android.util.Base64.NO_WRAP),
                timestamp = parts[4].toLong(),
                hopCount = parts[5].toInt()
            )
        }
    }
}

data class NearbyDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long
)
