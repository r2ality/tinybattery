package dev.tinybattery

import android.content.Context
import java.util.Locale

object BatteryStore {
    private const val PREFS = "tiny_battery_data"
    private const val KEY_CONNECTED = "connected_addresses"
    private const val KEY_THEME = "widget_theme"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun suffix(address: String): String =
        address.replace(":", "").lowercase(Locale.ROOT)

    private fun nameKey(address: String) = "name_${suffix(address)}"
    private fun kindKey(address: String) = "kind_${suffix(address)}"
    private fun levelKey(address: String) = "level_${suffix(address)}"

    fun saveConnected(context: Context, devices: List<BluetoothBatteryDevice>) {
        val store = prefs(context)
        val editor = store.edit()
        editor.putStringSet(KEY_CONNECTED, devices.map { it.address }.toSet())
        devices.forEach { device ->
            editor.putString(nameKey(device.address), device.name)
            editor.putString(kindKey(device.address), device.kind.name)
        }
        editor.apply()
    }

    fun connected(context: Context): List<BluetoothBatteryDevice> {
        val store = prefs(context)
        val addresses: Set<String> = store.getStringSet(KEY_CONNECTED, mutableSetOf()) ?: emptySet()
        return addresses.map { address ->
            val kind = runCatching {
                DeviceKind.valueOf(store.getString(kindKey(address), DeviceKind.UNKNOWN.name)
                    ?: DeviceKind.UNKNOWN.name)
            }.getOrDefault(DeviceKind.UNKNOWN)

            BluetoothBatteryDevice(
                address = address,
                name = store.getString(nameKey(address), null)
                    ?.takeIf { it.isNotBlank() } ?: "Bluetooth device",
                kind = kind,
                batteryLevel = store.getInt(levelKey(address), -1)
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun saveBatteryLevel(
        context: Context,
        address: String,
        level: Int,
        reportedName: String?
    ) {
        if (level !in 0..100) return

        val editor = prefs(context).edit()
            .putInt(levelKey(address), level)
        if (!reportedName.isNullOrBlank()) {
            editor.putString(nameKey(address), reportedName)
        }
        editor.apply()
    }

    fun theme(context: Context): WidgetTheme {
        val raw = prefs(context).getString(KEY_THEME, WidgetTheme.MATERIAL_YOU.name)
        return runCatching { WidgetTheme.valueOf(raw ?: WidgetTheme.MATERIAL_YOU.name) }
            .getOrDefault(WidgetTheme.MATERIAL_YOU)
    }

    fun setTheme(context: Context, theme: WidgetTheme) {
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()
    }
}
