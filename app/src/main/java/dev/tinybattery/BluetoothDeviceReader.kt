package dev.tinybattery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/** Reads connected devices through Android's public audio-profile APIs. */
object BluetoothDeviceReader {
    private const val PROFILE_TIMEOUT_MS = 4_500L

    fun hasConnectPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun refresh(context: Context, callback: (List<BluetoothBatteryDevice>) -> Unit) {
        val appContext = context.applicationContext
        if (!hasConnectPermission(appContext)) {
            callback(emptyList())
            return
        }

        val adapter = try {
            appContext.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (_: SecurityException) {
            null
        }

        if (adapter == null || !runCatching { adapter.isEnabled }.getOrDefault(false)) {
            callback(emptyList())
            return
        }

        val profiles = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            add(BluetoothProfile.GATT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(BluetoothProfile.HEARING_AID)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(BluetoothProfile.LE_AUDIO)
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val found = linkedMapOf<String, BluetoothBatteryDevice>()
        val completed = AtomicBoolean(false)
        val finishedProfiles = mutableSetOf<Int>()
        var remaining = profiles.size

        fun finish() {
            if (completed.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                callback(found.values.toList())
            }
        }

        fun markFinished(profileId: Int) {
            handler.post {
                if (!finishedProfiles.add(profileId)) return@post
                remaining -= 1
                if (remaining <= 0) finish()
            }
        }

        handler.postDelayed({ finish() }, PROFILE_TIMEOUT_MS)

        profiles.forEach { requestedProfile ->
            val accepted = try {
                adapter.getProfileProxy(
                    appContext,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(
                            profile: Int,
                            proxy: BluetoothProfile
                        ) {
                            handler.post {
                                if (completed.get()) {
                                    runCatching { adapter.closeProfileProxy(profile, proxy) }
                                    return@post
                                }

                                try {
                                    for (device in proxy.connectedDevices) {
                                        val address = device.address
                                        val name = try {
                                            device.name?.takeIf { it.isNotBlank() }
                                                ?: "Bluetooth device"
                                        } catch (_: SecurityException) {
                                            "Bluetooth device"
                                        }
                                        val kind = classify(device, profile)
                                        val old = found[address]
                                        if (old == null ||
                                            (old.kind == DeviceKind.UNKNOWN && kind != DeviceKind.UNKNOWN)
                                        ) {
                                            found[address] = BluetoothBatteryDevice(
                                                address = address,
                                                name = name,
                                                kind = kind,
                                                batteryLevel = -1
                                            )
                                        }
                                    }
                                } catch (_: SecurityException) {
                                    // Permission can be revoked while the profile is connecting.
                                } finally {
                                    runCatching { adapter.closeProfileProxy(profile, proxy) }
                                    markFinished(requestedProfile)
                                }
                            }
                        }

                        override fun onServiceDisconnected(profile: Int) {
                            markFinished(requestedProfile)
                        }
                    },
                    requestedProfile
                )
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }

            if (!accepted) markFinished(requestedProfile)
        }
    }

    private fun classify(device: BluetoothDevice, profile: Int): DeviceKind {
        val bluetoothClass = try {
            device.bluetoothClass
        } catch (_: SecurityException) {
            null
        }

        val major = bluetoothClass?.majorDeviceClass
        val minor = bluetoothClass?.deviceClass

        if (major == BluetoothClass.Device.Major.PHONE) return DeviceKind.PHONE

        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            return when (minor) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> DeviceKind.SPEAKER

                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> DeviceKind.HEADSET

                else -> if (profile == BluetoothProfile.HEADSET ||
                    profile == BluetoothProfile.HEARING_AID ||
                    profile == BluetoothProfile.LE_AUDIO
                ) DeviceKind.HEADSET else DeviceKind.UNKNOWN
            }
        }

        return if (profile == BluetoothProfile.HEADSET ||
            profile == BluetoothProfile.HEARING_AID ||
            profile == BluetoothProfile.LE_AUDIO
        ) DeviceKind.HEADSET else DeviceKind.UNKNOWN
    }
}
