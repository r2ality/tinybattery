package dev.tinybattery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * Renders the widget with a deliberately small RemoteViews surface.
 * It uses one fixed XML hierarchy and updates existing views only: no nested
 * RemoteViews rows, reflective setInt calls, custom widget Views, or adapters.
 */
object BatteryWidgetView {
    private data class Palette(
        val text: Int,
        val muted: Int,
        val accent: Int,
        val track: Int,
        val surface: Int
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, BatteryWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val views = try {
            buildViews(context)
        } catch (e: RuntimeException) {
            android.util.Log.e("TinyBattery", "Failed to build widget views", e)
            return
        }
        ids.forEach { id ->
            try {
                manager.updateAppWidget(id, views)
            } catch (e: RuntimeException) {
                android.util.Log.e("TinyBattery", "updateAppWidget failed for $id", e)
            }
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val palette = palette(context)
        val views = RemoteViews(context.packageName, R.layout.widget_battery)

        // Dedicated RemoteViews operations only. Avoid reflective setInt/setFloat
        // calls, whose target methods vary across framework/launcher versions.
        views.setImageViewResource(R.id.widget_surface_image, palette.surface)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextColor(R.id.widget_subtitle, palette.muted)
        views.setTextColor(R.id.phone_overline, palette.muted)
        views.setTextColor(R.id.phone_name, palette.text)
        views.setTextColor(R.id.phone_detail, palette.muted)
        views.setTextColor(R.id.phone_percent, palette.text)
        views.setTextColor(R.id.connected_title, palette.text)
        views.setTextColor(R.id.connected_count, palette.muted)
        views.setTextColor(R.id.empty_state, palette.muted)
        views.setTextColor(R.id.widget_refresh, palette.accent)

        val batteryIntent = context.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val phoneLevel = if (rawLevel >= 0 && scale > 0) {
            ((rawLevel * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        views.setImageViewBitmap(
            R.id.phone_ring,
            makeRing(context, phoneLevel, palette.accent, palette.track, PHONE_RING_DP)
        )
        views.setTextViewText(
            R.id.phone_percent,
            if (phoneLevel in 0..100) "$phoneLevel%" else "—"
        )
        views.setTextViewText(R.id.phone_name, context.getString(R.string.this_phone))
        val phoneDetail = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> R.string.charging
            BatteryManager.BATTERY_STATUS_FULL -> R.string.fully_charged
            else -> R.string.phone_battery
        }
        views.setTextViewText(R.id.phone_detail, context.getString(phoneDetail))

        val hasPermission = BluetoothDeviceReader.hasConnectPermission(context)
        val devices = if (hasPermission) BatteryStore.connected(context) else emptyList()
        views.setTextViewText(
            R.id.connected_count,
            context.resources.getQuantityString(
                R.plurals.connected_count,
                devices.size,
                devices.size
            )
        )

        val visibleCount = minOf(devices.size, MAX_VISIBLE_DEVICES)
        val slots = listOf(
            DeviceSlot(R.id.device_1_root, R.id.tile_ring_1, R.id.tile_percent_1, R.id.tile_icon_1, R.id.tile_name_1),
            DeviceSlot(R.id.device_2_root, R.id.tile_ring_2, R.id.tile_percent_2, R.id.tile_icon_2, R.id.tile_name_2),
            DeviceSlot(R.id.device_3_root, R.id.tile_ring_3, R.id.tile_percent_3, R.id.tile_icon_3, R.id.tile_name_3),
            DeviceSlot(R.id.device_4_root, R.id.tile_ring_4, R.id.tile_percent_4, R.id.tile_icon_4, R.id.tile_name_4)
        )

        slots.forEachIndexed { index, slot ->
            val device = devices.getOrNull(index)
            if (device == null) {
                views.setViewVisibility(slot.root, View.GONE)
            } else {
                views.setViewVisibility(slot.root, View.VISIBLE)
                views.setImageViewBitmap(
                    slot.ring,
                    makeRing(context, device.batteryLevel, palette.accent, palette.track, DEVICE_RING_DP)
                )
                views.setTextViewText(
                    slot.percent,
                    if (device.batteryLevel in 0..100) "${device.batteryLevel}%" else "—"
                )
                views.setTextColor(slot.percent, palette.text)
                views.setImageViewResource(slot.icon, iconFor(device.kind))
                views.setTextViewText(slot.name, device.name)
                views.setTextColor(slot.name, palette.text)
            }
        }

        views.setViewVisibility(R.id.empty_state, if (visibleCount == 0) View.VISIBLE else View.GONE)
        if (visibleCount == 0) {
            val hint = if (!hasPermission) R.string.permission_hint else R.string.no_devices
            views.setTextViewText(R.id.empty_state, context.getString(hint))
        } else if (devices.size > MAX_VISIBLE_DEVICES) {
            val extra = devices.size - MAX_VISIBLE_DEVICES
            views.setTextViewText(
                R.id.empty_state,
                context.resources.getQuantityString(R.plurals.more_devices, extra, extra)
            )
            views.setViewVisibility(R.id.empty_state, View.VISIBLE)
        }

        val openApp = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val refresh = PendingIntent.getBroadcast(
            context,
            101,
            Intent(context, BatteryWidgetProvider::class.java).setAction(BatteryWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        return views
    }

    private data class DeviceSlot(
        val root: Int,
        val ring: Int,
        val percent: Int,
        val icon: Int,
        val name: Int
    )

    private fun iconFor(kind: DeviceKind): Int = when (kind) {
        DeviceKind.PHONE -> R.drawable.ic_phone
        DeviceKind.HEADSET -> R.drawable.ic_headphones
        DeviceKind.SPEAKER -> R.drawable.ic_speaker
        DeviceKind.UNKNOWN -> R.drawable.ic_unknown
    }

    private fun makeRing(
        context: Context,
        level: Int,
        accent: Int,
        track: Int,
        sizeDp: Int
    ): Bitmap {
        // Cap bitmap dimensions to keep the RemoteViews Binder payload comfortably small
        // on high-density screens while retaining enough resolution for smooth rings.
        val maxPixels = if (sizeDp > DEVICE_RING_DP) 220 else 150
        val size = (sizeDp * context.resources.displayMetrics.density)
            .roundToInt().coerceIn(1, maxPixels)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = size * 0.085f
        val inset = stroke / 2f + 1f
        val bounds = RectF(inset, inset, size - inset, size - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = track
        }
        canvas.drawArc(bounds, -90f, 360f, false, paint)

        if (level in 0..100 && level > 0) {
            paint.color = when {
                level <= 15 -> LOW_BATTERY
                level <= 30 -> MEDIUM_BATTERY
                else -> accent
            }
            canvas.drawArc(bounds, -90f, level * 3.6f, false, paint)
        }
        return bitmap
    }

    private fun palette(context: Context): Palette {
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val accent = systemColor(context, "system_accent1_600", if (dark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt())
        val glass = BatteryStore.theme(context) == WidgetTheme.GLASS
        return Palette(
            text = if (dark) 0xFFF5F1FA.toInt() else 0xFF28252E.toInt(),
            muted = if (dark) 0xFFBDB8C8.toInt() else 0xFF77717F.toInt(),
            accent = accent,
            track = if (dark) 0xFF494451.toInt() else 0xFFE5E0EA.toInt(),
            surface = if (glass) R.drawable.widget_surface_glass else R.drawable.widget_surface
        )
    }

    private fun systemColor(context: Context, name: String, fallback: Int): Int {
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return fallback
        return runCatching { context.getColor(id) }.getOrDefault(fallback)
    }

    private const val MAX_VISIBLE_DEVICES = 4
    private const val PHONE_RING_DP = 82
    private const val DEVICE_RING_DP = 44
    private const val LOW_BATTERY = 0xFFE35D67.toInt()
    private const val MEDIUM_BATTERY = 0xFFE3A34D.toInt()
}
