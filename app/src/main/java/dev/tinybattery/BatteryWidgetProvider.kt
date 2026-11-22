package dev.tinybattery

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        BatteryWidgetView.updateAll(context)
        refreshDevices(context)
    }

    override fun onEnabled(context: Context) {
        BatteryWidgetView.updateAll(context)
        refreshDevices(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            BatteryWidgetView.updateAll(context)
            refreshDevices(context)
        }
    }

    private fun refreshDevices(context: Context) {
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
        const val ACTION_REFRESH = "dev.tinybattery.action.REFRESH_WIDGET"
    }
}
