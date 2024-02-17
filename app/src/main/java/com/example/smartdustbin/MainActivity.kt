package com.example.smartdustbin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import android.bluetooth.BluetoothProfile
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var inputStream: InputStream
    private lateinit var handler: Handler

    private val bluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        handler = Handler(Looper.getMainLooper())

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        var bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.STATE_CONNECTED) == BluetoothAdapter.STATE_DISCONNECTED) {
            showToast("Bluetooth is not supported on this device")
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetooth.launch(enableBtIntent)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                connectToBluetoothDevice()
            }
        }
    }

    private suspend fun connectToBluetoothDevice() {
        withContext(Dispatchers.Main) {
            val deviceName = "ESP32_Scale" // Change this to your ESP32 device name
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

            for (device in pairedDevices) {
                if (device.name == deviceName) {
                    bluetoothDevice = device
                    break
                }
            }

            if (!::bluetoothDevice.isInitialized) {
                showToast("Device $deviceName not found!")
                return@withContext
            }

            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Handle lack of permissions
                showToast("Bluetooth permissions are not granted")
                return@withContext
            }

            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(bluetoothUUID)
                bluetoothSocket.connect()
                inputStream = bluetoothSocket.inputStream
                startReadingData()
            } catch (e: IOException) {
                Log.e(TAG, "Error: ${e.message}")
                showToast("Failed to connect to device")
            }
        }
    }

    private fun startReadingData() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val bytes = ByteArray(1024)
                    val bytesCount = inputStream.read(bytes)
                    val data = String(bytes, 0, bytesCount)
                    withContext(Dispatchers.Main) {
                        textView.text = data
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from input stream: ${e.message}")
                    break
                }
            }
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                CoroutineScope(Dispatchers.IO).launch {
                    connectToBluetoothDevice()
                }
            } else {
                showToast("Bluetooth is required for this app to function")
                finish()
            }
        }
}