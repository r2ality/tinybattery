package dev.tinybattery

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BATTERY_LEVEL_CHANGED -> {
                if (BluetoothDeviceReader.hasConnectPermission(context)) {
                    val device = try {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } catch (_: RuntimeException) {
                        null
                    }

                    if (device != null) {
                        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                        val name = try {
                            device.name
                        } catch (_: SecurityException) {
                            null
                        }
                        BatteryStore.saveBatteryLevel(context, device.address, level, name)
                    }
                }
                BatteryWidgetView.updateAll(context)
            }

            ACTION_ACL_CONNECTED,
            ACTION_ACL_DISCONNECTED,
            ACTION_BLUETOOTH_STATE_CHANGED,
            ACTION_A2DP_STATE_CHANGED,
            ACTION_HEADSET_STATE_CHANGED,
            ACTION_HEARING_AID_STATE_CHANGED,
            ACTION_LE_AUDIO_STATE_CHANGED -> refreshBluetooth(context)

            ACTION_POWER_CONNECTED,
            ACTION_POWER_DISCONNECTED,
            ACTION_BATTERY_LOW,
            ACTION_BATTERY_OKAY -> BatteryWidgetView.updateAll(context)
        }
    }

    private fun refreshBluetooth(context: Context) {
        if (!BluetoothDeviceReader.hasConnectPermission(context)) {
            BatteryWidgetView.updateAll(context)
            return
        }

        val pending = goAsync()
        BluetoothDeviceReader.refresh(context) { devices ->
            try {
                BatteryStore.saveConnected(context, devices)
                BatteryWidgetView.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        const val ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED"
        const val ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED"
        const val ACTION_BLUETOOTH_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED"
        const val ACTION_A2DP_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_HEADSET_STATE_CHANGED =
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_HEARING_AID_STATE_CHANGED =
            "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_LE_AUDIO_STATE_CHANGED =
            "android.bluetooth.leaudio.profile.action.CONNECTION_STATE_CHANGED"

        const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
        const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"
        const val ACTION_BATTERY_LOW = "android.intent.action.BATTERY_LOW"
        const val ACTION_BATTERY_OKAY = "android.intent.action.BATTERY_OKAY"
    }
}
