package dev.tinybattery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** A compact, fully native settings/dashboard screen; no UI libraries required. */
class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView
    private lateinit var grantButton: TextView
    private lateinit var refreshButton: TextView
    private lateinit var deviceSummary: TextView
    private lateinit var phoneDetail: TextView
    private lateinit var phoneRing: BatteryRingView
    private lateinit var devicesContainer: LinearLayout
    private lateinit var materialChoice: TextView
    private lateinit var glassChoice: TextView

    private var accent = 0xFF6750A4.toInt()
    private var dark = false
    private var primaryText = 0xFF28252E.toInt()
    private var secondaryText = 0xFF77717F.toInt()
    private var pageBackground = 0xFFF5F2F8.toInt()
    private var cardBackground = 0xFFFFFFFF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = isDarkMode()
        pageBackground = if (dark) 0xFF17151D.toInt() else 0xFFF5F2F8.toInt()
        primaryText = if (dark) 0xFFF5F1FA.toInt() else 0xFF28252E.toInt()
        secondaryText = if (dark) 0xFFBDB7C7.toInt() else 0xFF77717F.toInt()
        cardBackground = if (dark) 0xFF24212B.toInt() else 0xFFFFFFFF.toInt()
        accent = resolveSystemAccent()
        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) {
            updatePermissionUi()
            updateOverview()
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBackground)
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(30))
        }
        scroll.addView(root)

        // Branded header with a small overline gives the screen a more intentional hierarchy.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appIcon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = getString(R.string.app_name)
        }
        header.addView(appIcon, LinearLayout.LayoutParams(dp(54), dp(54)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        brand.addView(label("BATTERY DASHBOARD", 9f, accent, true))
        brand.addView(label(getString(R.string.app_name), 25f, primaryText, true).apply {
            setPadding(0, dp(1), 0, 0)
        })
        brand.addView(label(getString(R.string.setup_intro), 12f, secondaryText, false).apply {
            setPadding(0, dp(1), 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(pill("LOCAL ONLY", if (dark) 0xFF35303F.toInt() else 0xFFEAE4F2.toInt(), accent))
        root.addView(header)

        // Phone overview.
        val hero = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        phoneRing = BatteryRingView(this).apply {
            level = readPhoneLevel()
            ringColor = accent
            contentDescription = getString(R.string.phone_ring_description)
        }
        hero.addView(phoneRing, LinearLayout.LayoutParams(dp(100), dp(100)))
        val heroText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), 0, 0, 0)
        }
        heroText.addView(label("THIS DEVICE", 10f, secondaryText, true))
        heroText.addView(label("Phone battery", 17f, primaryText, true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        phoneDetail = label("", 12f, secondaryText, false).apply {
            setPadding(0, dp(4), 0, 0)
        }
        heroText.addView(phoneDetail)
        hero.addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(hero, verticalParams(top = 22))

        // Connected device list; each device gets its own little battery circle.
        val accessories = card()
        val accessoryHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        accessoryHeader.addView(label(getString(R.string.bluetooth_access), 16f, primaryText, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        deviceSummary = pill("0 DEVICES", if (dark) 0xFF35303F.toInt() else 0xFFEAE4F2.toInt(), accent)
        accessoryHeader.addView(deviceSummary)
        accessories.addView(accessoryHeader)
        accessories.addView(label(
            "Battery levels reported by your connected Bluetooth gear.", 12f, secondaryText, false
        ).apply { setPadding(0, dp(4), 0, dp(12)) })
        devicesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        accessories.addView(devicesContainer)
        root.addView(accessories, verticalParams(top = 14))

        // Two-option appearance control.
        val appearance = card()
        appearance.addView(sectionHeading(getString(R.string.appearance)))
        appearance.addView(label(getString(R.string.appearance_hint), 12f, secondaryText, false).apply {
            setPadding(0, dp(4), 0, dp(12))
        })
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(if (dark) 0xFF302C38.toInt() else 0xFFECE7F1.toInt(), dp(17))
        }
        materialChoice = themeChoice(getString(R.string.theme_material_you))
        glassChoice = themeChoice(getString(R.string.theme_glass))
        choices.addView(materialChoice, LinearLayout.LayoutParams(0, dp(42), 1f))
        choices.addView(glassChoice, LinearLayout.LayoutParams(0, dp(42), 1f))
        materialChoice.setOnClickListener { selectTheme(WidgetTheme.MATERIAL_YOU) }
        glassChoice.setOnClickListener { selectTheme(WidgetTheme.GLASS) }
        appearance.addView(choices)
        root.addView(appearance, verticalParams(top = 14))

        // Permission and manual refresh controls.
        val bluetooth = card()
        bluetooth.addView(sectionHeading("Bluetooth access"))
        permissionStatus = label("", 12f, secondaryText, false).apply {
            setPadding(0, dp(5), 0, dp(12))
        }
        bluetooth.addView(permissionStatus)
        grantButton = actionButton(getString(R.string.allow_bluetooth), accent, 0xFFFFFFFF.toInt())
        grantButton.setOnClickListener { requestBluetoothPermission() }
        bluetooth.addView(grantButton, verticalParams())
        refreshButton = actionButton(
            getString(R.string.refresh_now),
            if (dark) 0xFF393443.toInt() else 0xFFECE7F1.toInt(),
            primaryText
        )
        refreshButton.setOnClickListener { refreshDevices() }
        bluetooth.addView(refreshButton, verticalParams(top = 8))
        root.addView(bluetooth, verticalParams(top = 14))

        root.addView(label(getString(R.string.privacy_note), 11f, secondaryText, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(18), dp(8), 0)
        }, verticalParams(top = 4))

        setContentView(scroll)
        updateThemeChoices()
        updatePermissionUi()
        updateOverview()
    }

    private fun selectTheme(theme: WidgetTheme) {
        BatteryStore.setTheme(this, theme)
        updateThemeChoices()
        BatteryWidgetView.updateAll(this)
    }

    private fun updateThemeChoices() {
        val selected = BatteryStore.theme(this)
        val selectedText = 0xFFFFFFFF.toInt()
        val normalText = if (dark) 0xFFEAE5F2.toInt() else 0xFF56515F.toInt()
        materialChoice.setTextColor(if (selected == WidgetTheme.MATERIAL_YOU) selectedText else normalText)
        glassChoice.setTextColor(if (selected == WidgetTheme.GLASS) selectedText else normalText)
        materialChoice.background = rounded(
            if (selected == WidgetTheme.MATERIAL_YOU) accent else android.graphics.Color.TRANSPARENT,
            dp(14)
        )
        glassChoice.background = rounded(
            if (selected == WidgetTheme.GLASS) accent else android.graphics.Color.TRANSPARENT,
            dp(14)
        )
    }

    private fun updateOverview() {
        if (!::phoneRing.isInitialized) return
        val level = readPhoneLevel()
        phoneRing.level = level
        phoneRing.invalidate()
        val battery = readPhoneBatteryState()
        val percentage = if (level in 0..100) "$level%" else "Battery level unavailable"
        val state = when (battery) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully charged"
            else -> "On battery"
        }
        phoneDetail.text = "$percentage · $state"

        val devices = if (BluetoothDeviceReader.hasConnectPermission(this)) {
            BatteryStore.connected(this)
        } else emptyList()
        deviceSummary.text = resources.getQuantityString(
            R.plurals.accessory_summary, devices.size, devices.size
        ).uppercase()

        devicesContainer.removeAllViews()
        if (devices.isEmpty()) {
            devicesContainer.addView(label(
                if (BluetoothDeviceReader.hasConnectPermission(this)) {
                    getString(R.string.no_devices)
                } else {
                    getString(R.string.permission_hint)
                },
                12f,
                secondaryText,
                false
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(10))
            })
        } else {
            devices.take(8).forEachIndexed { index, device ->
                if (index > 0) {
                    val divider = View(this).apply {
                        setBackgroundColor(if (dark) 0x33FFFFFF else 0x1F28252E)
                    }
                    devicesContainer.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
                        topMargin = dp(7)
                        bottomMargin = dp(7)
                    })
                }
                devicesContainer.addView(deviceRow(device))
            }
        }
    }

    private fun deviceRow(device: BluetoothBatteryDevice): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        val icon = ImageView(this).apply {
            setImageResource(when (device.kind) {
                DeviceKind.PHONE -> R.drawable.ic_phone
                DeviceKind.HEADSET -> R.drawable.ic_headphones
                DeviceKind.SPEAKER -> R.drawable.ic_speaker
                DeviceKind.UNKNOWN -> R.drawable.ic_unknown
            })
            setColorFilter(accent)
            contentDescription = null
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(8), 0)
        }
        text.addView(label(device.name, 13f, primaryText, true))
        text.addView(label(deviceTypeLabel(device.kind), 10f, secondaryText, false).apply {
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val ring = BatteryRingView(this).apply {
            level = device.batteryLevel
            ringColor = accent
            compact = true
        }
        row.addView(ring, LinearLayout.LayoutParams(dp(54), dp(54)))
        return row
    }

    private fun deviceTypeLabel(kind: DeviceKind): String = when (kind) {
        DeviceKind.PHONE -> "Phone"
        DeviceKind.HEADSET -> "Headset"
        DeviceKind.SPEAKER -> "Speaker"
        DeviceKind.UNKNOWN -> "Bluetooth device"
    }

    private fun readPhoneLevel(): Int {
        val intent = registerReceiver(null, IntentFilterCompat.batteryChanged())
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else -1
    }

    private fun readPhoneBatteryState(): Int {
        val intent = registerReceiver(null, IntentFilterCompat.batteryChanged())
        return intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    }

    private fun requestBluetoothPermission() {
        if (BluetoothDeviceReader.hasConnectPermission(this)) {
            refreshDevices()
        } else {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
        }
    }

    private fun refreshDevices() {
        if (!BluetoothDeviceReader.hasConnectPermission(this)) {
            updatePermissionUi()
            return
        }
        refreshButton.text = getString(R.string.refreshing)
        refreshButton.isEnabled = false
        BluetoothDeviceReader.refresh(this) { devices ->
            BatteryStore.saveConnected(this, devices)
            BatteryWidgetView.updateAll(this)
            updatePermissionUi()
            updateOverview()
            refreshButton.text = getString(R.string.refresh_now)
            refreshButton.isEnabled = true
        }
    }

    private fun updatePermissionUi() {
        if (!::permissionStatus.isInitialized) return
        val granted = BluetoothDeviceReader.hasConnectPermission(this)
        permissionStatus.text = getString(
            if (granted) R.string.permission_granted else R.string.permission_required
        )
        grantButton.visibility = if (granted) View.GONE else View.VISIBLE
        refreshButton.visibility = if (granted) View.VISIBLE else View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            updatePermissionUi()
            if (BluetoothDeviceReader.hasConnectPermission(this)) refreshDevices()
            updateOverview()
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(
            cardBackground,
            dp(23),
            if (dark) 0xFF34303D.toInt() else 0xFFE9E3EF.toInt()
        )
    }

    private fun sectionHeading(value: String) = label(value, 16f, primaryText, true)

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun pill(value: String, backgroundColor: Int, textColor: Int) = TextView(this).apply {
        text = value
        textSize = 9f
        letterSpacing = 0.04f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textColor)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = rounded(backgroundColor, dp(20))
    }

    private fun themeChoice(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(if (dark) 0xFFEAE5F2.toInt() else 0xFF56515F.toInt())
        isClickable = true
        isFocusable = true
    }

    private fun actionButton(value: String, backgroundColor: Int, textColor: Int) = TextView(this).apply {
        text = value
        textSize = 14f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textColor)
        minHeight = dp(46)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(backgroundColor, dp(16))
        isClickable = true
        isFocusable = true
    }

    private fun verticalParams(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { if (top > 0) topMargin = dp(top) }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun resolveSystemAccent(): Int {
        val id = resources.getIdentifier("system_accent1_600", "color", "android")
        return if (id != 0) runCatching { getColor(id) }.getOrDefault(0xFF6750A4.toInt())
        else 0xFF6750A4.toInt()
    }

    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private class BatteryRingView(private val activity: Activity) : View(activity) {
        var level: Int = -1
        var ringColor: Int = 0xFF6750A4.toInt()
        var compact: Boolean = false
        private val density = activity.resources.displayMetrics.density
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = if ((activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES) 0xFF494451.toInt() else 0xFFE5E0EA.toInt()
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = activity.resources.displayMetrics.scaledDensity * 18f
            color = if ((activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES) 0xFFF5F1FA.toInt() else 0xFF28252E.toInt()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val diameter = minOf(width, height).toFloat()
            val stroke = density * if (compact) 4.5f else 6.5f
            val inset = stroke / 2f + density * 3f
            val left = (width - diameter) / 2f + inset
            val top = (height - diameter) / 2f + inset
            val bounds = RectF(left, top, left + diameter - inset * 2f, top + diameter - inset * 2f)
            trackPaint.strokeWidth = stroke
            canvas.drawArc(bounds, -90f, 360f, false, trackPaint)
            if (level in 0..100 && level > 0) {
                ringPaint.strokeWidth = stroke
                ringPaint.color = when {
                    level <= 15 -> 0xFFE35D67.toInt()
                    level <= 30 -> 0xFFE3A34D.toInt()
                    else -> ringColor
                }
                canvas.drawArc(bounds, -90f, level * 3.6f, false, ringPaint)
            }
            textPaint.textSize = resources.displayMetrics.scaledDensity * if (compact) 11f else 19f
            val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(if (level in 0..100) "$level%" else "—", width / 2f, baseline, textPaint)
        }
    }

    /** Avoids a dependency on AndroidX's IntentFilterCompat. */
    private object IntentFilterCompat {
        fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 41
    }
}
