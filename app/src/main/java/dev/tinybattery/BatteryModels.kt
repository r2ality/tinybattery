package dev.tinybattery

enum class DeviceKind {
    PHONE,
    HEADSET,
    SPEAKER,
    UNKNOWN
}

data class BluetoothBatteryDevice(
    val address: String,
    val name: String,
    val kind: DeviceKind,
    val batteryLevel: Int
)

enum class WidgetTheme {
    MATERIAL_YOU,
    GLASS
}
