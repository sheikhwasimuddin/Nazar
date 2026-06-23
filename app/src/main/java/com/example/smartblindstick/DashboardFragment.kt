package com.example.smartblindstick

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private lateinit var database: DatabaseReference

    private lateinit var lidarText: TextView
    private lateinit var lidarStatusText: TextView
    private lateinit var waterText: TextView
    private lateinit var waterStatusText: TextView
    private lateinit var emergencyText: TextView
    private lateinit var obstacleText: TextView

    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var accelMagText: TextView

    private lateinit var lidarChart: LineChart
    private lateinit var waterChart: LineChart
    private lateinit var accelChart: LineChart

    private lateinit var liveBadgeContainer: LinearLayout
    private lateinit var liveBadgeDot: View
    private lateinit var liveBadgeText: TextView
    private val connectionHandler = Handler(Looper.getMainLooper())
    private val offlineRunnable = Runnable { setDeviceOffline() }

    private val lidarEntries = ArrayList<Entry>()
    private val waterEntries = ArrayList<Entry>()
    private val accelXEntries = ArrayList<Entry>()
    private val accelYEntries = ArrayList<Entry>()
    private val accelZEntries = ArrayList<Entry>()

    private var index = 0f
    private val CHANNEL_ID = "NazarAlerts"

    companion object {
        private var hasNotifiedObstacle = false
        private var hasNotifiedWater = false
        private var hasNotifiedEmergency = false
        private var hasNotifiedFall = false
        private var hasNotifiedLidar = false // ADDED for LiDAR

        private var lastEmergencyState = false
        private var lastObstacleState = false
        private var lastWaterState = false
        private var lastFallState = false
        private var lastLidarState = false // ADDED for LiDAR
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        createNotificationChannel()

        lidarText = view.findViewById(R.id.lidarText)
        lidarStatusText = view.findViewById(R.id.lidarStatusText)
        waterText = view.findViewById(R.id.waterText)
        waterStatusText = view.findViewById(R.id.waterStatusText)
        emergencyText = view.findViewById(R.id.emergencyText)
        obstacleText = view.findViewById(R.id.obstacleText)

        accelXText = view.findViewById(R.id.accelXText)
        accelYText = view.findViewById(R.id.accelYText)
        accelZText = view.findViewById(R.id.accelZText)
        accelMagText = view.findViewById(R.id.accelMagText)

        lidarChart = view.findViewById(R.id.lidarChart)
        waterChart = view.findViewById(R.id.waterChart)
        accelChart = view.findViewById(R.id.accelChart)

        liveBadgeContainer = view.findViewById(R.id.liveBadgeContainer)
        liveBadgeDot = view.findViewById(R.id.liveBadgeDot)
        liveBadgeText = view.findViewById(R.id.liveBadgeText)

        setDeviceOffline()

        setupChart(lidarChart, ContextCompat.getColor(requireContext(), R.color.primary))
        setupChart(waterChart, ContextCompat.getColor(requireContext(), R.color.accent))
        setupChart(accelChart, ContextCompat.getColor(requireContext(), R.color.statusRed))

        database = FirebaseDatabase.getInstance().getReference("Sensor")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || !isAdded) return

                setDeviceOnline()
                connectionHandler.removeCallbacks(offlineRunnable)
                connectionHandler.postDelayed(offlineRunnable, 5000)

                val lidar = snapshot.child("lidar").getValue(Int::class.java) ?: 0
                val water = snapshot.child("water").getValue(Int::class.java) ?: 0
                val emergency = snapshot.child("emergencyPressed").getValue(Boolean::class.java) ?: false
                val obstacleDetected = snapshot.child("obstacleDetected").getValue(Boolean::class.java) ?: false
                val lidarStatusString = snapshot.child("obstacle_status").getValue(String::class.java) ?: "Scanning..."
                val waterDetected = snapshot.child("waterDetected").getValue(Boolean::class.java) ?: false
                val waterStatusString = snapshot.child("waterStatus").getValue(String::class.java) ?: if(waterDetected) "Wet" else "Dry"

                val x = snapshot.child("x").getValue(Double::class.java) ?: 0.0
                val y = snapshot.child("y").getValue(Double::class.java) ?: 0.0
                val z = snapshot.child("z").getValue(Double::class.java) ?: 0.0
                val magnitude = snapshot.child("magnitude").getValue(Double::class.java) ?: 0.0
                val fallDetected = snapshot.child("fallDetected").getValue(Boolean::class.java) ?: false

                // Text UI updates
                val lidarStr = "LiDAR: $lidar mm"
                lidarText.text = lidarStr
                TranslationManager.translateText(lidarStr) { lidarText.text = it }

                val waterStr = "Water Level: $water"
                waterText.text = waterStr
                TranslationManager.translateText(waterStr) { waterText.text = it }

                accelXText.text = "%.1f".format(x)
                accelYText.text = "%.1f".format(y)
                accelZText.text = "%.1f".format(z)
                accelMagText.text = "%.2f".format(magnitude)

                if (emergency) {
                    val emStr = "🚨 EMERGENCY!"
                    emergencyText.text = emStr
                    TranslationManager.translateText(emStr) { emergencyText.text = it }
                    emergencyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusRed))
                } else {
                    val safeStr = "Status: Safe"
                    emergencyText.text = safeStr
                    TranslationManager.translateText(safeStr) { emergencyText.text = it }
                    emergencyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                }

                val lidarStatusIcon = view.findViewById<ImageView>(R.id.lidarStatusIcon)
                val lidarStatusIconCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.lidarStatusIconCard)

                lidarStatusText.text = lidarStatusString
                TranslationManager.translateText(lidarStatusString) { lidarStatusText.text = it }

                if (lidarStatusString.contains("Out of Range") || lidarStatusString.contains("Safe")) {
                    lidarStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    lidarStatusIcon?.setImageResource(android.R.drawable.ic_menu_search)
                    lidarStatusIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    lidarStatusIconCard?.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                } else {
                    lidarStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    lidarStatusIcon?.setImageResource(android.R.drawable.ic_dialog_alert)
                    lidarStatusIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
                    lidarStatusIconCard?.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                }

                val obstacleIcon = view.findViewById<ImageView>(R.id.obstacleIcon)
                val obstacleIconCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.obstacleIconCard)

                if (obstacleDetected) {
                    val obsStr = "🚧 OBSTACLE: Close Proximity!"
                    obstacleText.text = obsStr
                    TranslationManager.translateText(obsStr) { obstacleText.text = it }
                    obstacleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusRed))
                    obstacleIcon?.setImageResource(android.R.drawable.ic_dialog_alert)
                    obstacleIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusRed))
                    obstacleIconCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.statusRed).let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) })
                } else {
                    val clearStr = "✅ IR Path Clear"
                    obstacleText.text = clearStr
                    TranslationManager.translateText(clearStr) { obstacleText.text = it }
                    obstacleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    obstacleIcon?.setImageResource(android.R.drawable.ic_menu_directions)
                    obstacleIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    obstacleIconCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.statusGreen).let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) })
                }

                val waterStatusIcon = view.findViewById<ImageView>(R.id.waterStatusIcon)
                val waterStatusIconCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.waterStatusIconCard)

                if (waterDetected) {
                    val wetStr = "⚠️ WATER DETECTED ($waterStatusString)"
                    waterStatusText.text = wetStr
                    TranslationManager.translateText(wetStr) { waterStatusText.text = it }
                    waterStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusRed))
                    waterStatusIcon?.setImageResource(android.R.drawable.ic_dialog_alert)
                    waterStatusIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusRed))
                    waterStatusIconCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.statusRed).let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) })
                } else {
                    val safeWaterStr = "✅ Safe ($waterStatusString)"
                    waterStatusText.text = safeWaterStr
                    TranslationManager.translateText(safeWaterStr) { waterStatusText.text = it }
                    waterStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    waterStatusIcon?.setImageResource(android.R.drawable.ic_menu_info_details)
                    waterStatusIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusGreen))
                    waterStatusIconCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.statusGreen).let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) })
                }


                // 🔥 FIX: FETCH EXACT USER TOGGLES FROM SETTINGS 🔥
                val sharedPreferences = requireContext().getSharedPreferences("NazarSettings", Context.MODE_PRIVATE)
                val notifyIr = sharedPreferences.getBoolean("notify_ir", true)
                val notifyWater = sharedPreferences.getBoolean("notify_water", true)
                val notifyLidar = sharedPreferences.getBoolean("notify_lidar", true) // ADDED

                // 1. Emergency Alert (Critical - Always Notify)
                if (emergency && !lastEmergencyState) {
                    if (!hasNotifiedEmergency) {
                        val msg = snapshot.child("emergencyMessage").getValue(String::class.java) ?: "Emergency switch was pressed!"
                        TranslationManager.translateText("🚨 EMERGENCY ALERT!") { title ->
                            TranslationManager.translateText(msg) { body -> sendNotification(title, body) }
                        }
                        hasNotifiedEmergency = true
                    }
                } else if (!emergency && lastEmergencyState) {
                    hasNotifiedEmergency = false
                }

                // 2. IR Obstacle Alert (Only if toggle is ON)
                if (obstacleDetected && !lastObstacleState) {
                    if (!hasNotifiedObstacle) {
                        if (notifyIr) {
                            TranslationManager.translateText("Immediate Obstacle Alert!") { title ->
                                TranslationManager.translateText("IR Sensor detected an obstacle in close proximity.") { body -> sendNotification(title, body) }
                            }
                        }
                        hasNotifiedObstacle = true
                    }
                } else if (!obstacleDetected && lastObstacleState) {
                    hasNotifiedObstacle = false
                }

                // 3. LiDAR Obstacle Alert (Only if toggle is ON)
                val isLidarObstacle = !(lidarStatusString.contains("Out of Range", ignoreCase = true) || lidarStatusString.contains("Safe", ignoreCase = true))
                if (isLidarObstacle && !lastLidarState) {
                    if (!hasNotifiedLidar) {
                        if (notifyLidar) {
                            TranslationManager.translateText("LiDAR Obstacle Alert!") { title ->
                                TranslationManager.translateText("LiDAR detected an obstacle ahead.") { body -> sendNotification(title, body) }
                            }
                        }
                        hasNotifiedLidar = true
                    }
                } else if (!isLidarObstacle && lastLidarState) {
                    hasNotifiedLidar = false
                }

                // 4. Water Alert (Only if toggle is ON)
                if (waterDetected && !lastWaterState) {
                    if (!hasNotifiedWater) {
                        if (notifyWater) {
                            TranslationManager.translateText("Water Detected!") { title ->
                                TranslationManager.translateText("Water detected by the smart stick.") { body -> sendNotification(title, body) }
                            }
                        }
                        hasNotifiedWater = true
                    }
                } else if (!waterDetected && lastWaterState) {
                    hasNotifiedWater = false
                }

                // 5. Fall Detection (Critical - Always Notify)
                val magnitudeThreshold = 15.0
                val isFallHappening = fallDetected || magnitude > magnitudeThreshold
                if (isFallHappening && !lastFallState) {
                    if (!hasNotifiedFall) {
                        TranslationManager.translateText("⚠️ Fall Detected!") { title ->
                            TranslationManager.translateText("Sudden movement or fall detected by the stick.") { body -> sendNotification(title, body) }
                        }
                        hasNotifiedFall = true
                    }
                } else if (!isFallHappening && lastFallState) {
                    hasNotifiedFall = false
                }

                lastEmergencyState = emergency
                lastObstacleState = obstacleDetected
                lastWaterState = waterDetected
                lastFallState = fallDetected || magnitude > 15.0
                lastLidarState = isLidarObstacle // ADDED

                updateCharts(lidar, water, x, y, z)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setDeviceOnline() {
        if (!isAdded) return
        val liveStr = "LIVE"
        liveBadgeText.text = liveStr
        TranslationManager.translateText(liveStr) { liveBadgeText.text = it }
        liveBadgeText.setTextColor(Color.WHITE)
        liveBadgeDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusGreen))
    }

    private fun setDeviceOffline() {
        if (!isAdded) return
        val offStr = "OFFLINE"
        liveBadgeText.text = offStr
        TranslationManager.translateText(offStr) { liveBadgeText.text = it }
        liveBadgeText.setTextColor(Color.parseColor("#9E9E9E"))
        liveBadgeDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectionHandler.removeCallbacks(offlineRunnable)
    }

    private fun setupChart(chart: LineChart, color: Int) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = Color.parseColor("#A0AEC0")
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F3FF")
                setDrawAxisLine(false)
                textColor = Color.parseColor("#A0AEC0")
                textSize = 10f
            }
            axisRight.isEnabled = false
            animateX(500)
        }
    }

    private fun updateCharts(lidar: Int, water: Int, x: Double, y: Double, z: Double) {
        lidarEntries.add(Entry(index, lidar.toFloat()))
        waterEntries.add(Entry(index, water.toFloat()))
        accelXEntries.add(Entry(index, x.toFloat()))
        accelYEntries.add(Entry(index, y.toFloat()))
        accelZEntries.add(Entry(index, z.toFloat()))

        if (lidarEntries.size > 30) {
            lidarEntries.removeAt(0)
            waterEntries.removeAt(0)
            accelXEntries.removeAt(0)
            accelYEntries.removeAt(0)
            accelZEntries.removeAt(0)
        }
        index++

        val lidarColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val waterColor = ContextCompat.getColor(requireContext(), R.color.accent)

        lidarChart.data = LineData(LineDataSet(lidarEntries, "LiDAR").apply {
            color = lidarColor
            setCircleColor(lidarColor)
            lineWidth = 2.5f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = lidarColor
            fillAlpha = 20
        })

        waterChart.data = LineData(LineDataSet(waterEntries, "Water").apply {
            color = waterColor
            setCircleColor(waterColor)
            lineWidth = 2.5f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = waterColor
            fillAlpha = 20
        })

        accelChart.data = LineData(
            LineDataSet(accelXEntries, "X").apply { color = Color.RED; setDrawCircles(false); setDrawValues(false) },
            LineDataSet(accelYEntries, "Y").apply { color = Color.GREEN; setDrawCircles(false); setDrawValues(false) },
            LineDataSet(accelZEntries, "Z").apply { color = Color.BLUE; setDrawCircles(false); setDrawValues(false) }
        )

        lidarChart.invalidate()
        waterChart.invalidate()
        accelChart.invalidate()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nazar Alerts", NotificationManager.IMPORTANCE_HIGH)
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}