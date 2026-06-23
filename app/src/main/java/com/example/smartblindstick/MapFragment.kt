package com.example.smartblindstick

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.util.concurrent.TimeUnit

class MapFragment : Fragment() {

    private lateinit var osmMap: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    private lateinit var hospitalCountText: TextView
    private lateinit var hospitalListContainer: LinearLayout
    private lateinit var btnRefresh: MaterialButton
    private lateinit var fabMyLocation: FloatingActionButton

    // ✅ Your fix: Extended timeout
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var lastKnownLat = 0.0
    private var lastKnownLon = 0.0

    // ✅ Stores ALL hospitals — all shown on map, top 10 in card
    private val allHospitalList = mutableListOf<Hospital>()

    private data class Hospital(
        val name: String,
        val lat: Double,
        val lng: Double,
        val distanceKm: Double
    )

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 2001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        osmMap = view.findViewById(R.id.osmMap)
        hospitalCountText = view.findViewById(R.id.hospitalCountText)
        hospitalListContainer = view.findViewById(R.id.hospitalListContainer)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        fabMyLocation = view.findViewById(R.id.fabMyLocation)

        setupMap()
        checkLocationPermission()

        btnRefresh.setOnClickListener {
            if (lastKnownLat != 0.0 && lastKnownLon != 0.0) {
                searchHospitals(lastKnownLat, lastKnownLon)
            } else {
                if (::myLocationOverlay.isInitialized) {
                    val loc = myLocationOverlay.myLocation
                    if (loc != null) searchHospitals(loc.latitude, loc.longitude)
                    else Toast.makeText(requireContext(), "Getting location...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fabMyLocation.setOnClickListener {
            if (::myLocationOverlay.isInitialized) {
                val loc = myLocationOverlay.myLocation
                if (loc != null) {
                    osmMap.controller.animateTo(loc)
                    osmMap.controller.setZoom(15.0)
                } else {
                    Toast.makeText(requireContext(), "Location not available yet", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupMap() {
        osmMap.setTileSource(TileSourceFactory.MAPNIK)
        osmMap.setMultiTouchControls(true)
        osmMap.controller.setZoom(14.0)
        osmMap.controller.setCenter(GeoPoint(21.1458, 79.0882))
    }

    private fun isGpsEnabled(): Boolean {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (isGpsEnabled()) enableMyLocation()
            else showGpsEnableDialog()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun showGpsEnableDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable GPS")
            .setMessage("GPS is turned off. Please enable it to find nearby hospitals.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                hospitalCountText.text = "GPS required to search hospitals"
            }
            .setCancelable(false)
            .show()
    }

    private fun enableMyLocation() {
        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()), osmMap
        )
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()

        myLocationOverlay.runOnFirstFix {
            val location = myLocationOverlay.myLocation
            if (location != null) {
                lastKnownLat = location.latitude
                lastKnownLon = location.longitude
                // ✅ Your fix: activity?. prevents crash if user leaves screen
                activity?.runOnUiThread {
                    myLocationOverlay.disableFollowLocation()
                    osmMap.controller.animateTo(location)
                    osmMap.controller.setZoom(15.0)
                    searchHospitals(location.latitude, location.longitude)
                }
            }
        }

        osmMap.overlays.add(myLocationOverlay)
    }

    private fun searchHospitals(lat: Double, lon: Double) {
        lastKnownLat = lat
        lastKnownLon = lon
        hospitalCountText.text = "Searching nearby hospitals..."

        // Remove only markers, keep myLocation overlay
        osmMap.overlays.removeAll(osmMap.overlays.filterIsInstance<Marker>())
        allHospitalList.clear()

        // ✅ Query ALL hospitals + clinics — no limit
        val query = """
            [out:json][timeout:25];
            (
              node["amenity"="hospital"](around:5000,$lat,$lon);
              way["amenity"="hospital"](around:5000,$lat,$lon);
              relation["amenity"="hospital"](around:5000,$lat,$lon);
              node["amenity"="clinic"](around:5000,$lat,$lon);
              way["amenity"="clinic"](around:5000,$lat,$lon);
            );
            out center;
        """.trimIndent()

        val requestBody = query.toRequestBody("text/plain".toMediaType())

        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            // ✅ Your fix: User-Agent header
            .header("User-Agent", "NazarSmartBlindStick/1.0")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    hospitalCountText.text = "Search failed. Check internet."
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val elements = JSONObject(body).getJSONArray("elements")
                    val tempList = mutableListOf<Hospital>()

                    for (i in 0 until elements.length()) {
                        val el = elements.getJSONObject(i)
                        val tags = el.optJSONObject("tags") ?: continue
                        val name = tags.optString("name", "").ifEmpty { continue }

                        val hosLat: Double
                        val hosLon: Double

                        when {
                            el.has("lat") -> {
                                hosLat = el.getDouble("lat")
                                hosLon = el.getDouble("lon")
                            }
                            el.has("center") -> {
                                val c = el.getJSONObject("center")
                                hosLat = c.getDouble("lat")
                                hosLon = c.getDouble("lon")
                            }
                            else -> continue
                        }

                        val distM = FloatArray(1)
                        Location.distanceBetween(lat, lon, hosLat, hosLon, distM)
                        tempList.add(Hospital(name, hosLat, hosLon, distM[0] / 1000.0))
                    }

                    tempList.sortBy { it.distanceKm }

                    activity?.runOnUiThread {
                        allHospitalList.clear()
                        allHospitalList.addAll(tempList)

                        // ✅ ALL hospitals shown on map
                        for (hospital in allHospitalList) {
                            val marker = Marker(osmMap)
                            marker.position = GeoPoint(hospital.lat, hospital.lng)
                            marker.title = hospital.name
                            marker.snippet = "${"%.1f".format(hospital.distanceKm)} km away"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            // ✅ ANY marker click opens navigation
                            marker.setOnMarkerClickListener { _, _ ->
                                openNavigation(hospital.lat, hospital.lng)
                                true
                            }
                            osmMap.overlays.add(marker)
                        }

                        osmMap.invalidate()

                        // ✅ Top 10 in scrollable bottom card
                        buildHospitalCards(allHospitalList.take(10))

                        hospitalCountText.text = if (allHospitalList.isEmpty())
                            "No hospitals found nearby"
                        else
                            "${allHospitalList.size} found · Top 10 listed below"
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        hospitalCountText.text = "Error parsing results"
                    }
                }
            }
        })
    }

    private fun buildHospitalCards(hospitals: List<Hospital>) {
        hospitalListContainer.removeAllViews()

        hospitals.forEachIndexed { index, hospital ->

            // Row
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 12.dpToPx(), 0, 12.dpToPx())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    openNavigation(hospital.lat, hospital.lng)
                }
            }

            // Rank badge
            val badge = TextView(requireContext()).apply {
                text = "${index + 1}"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                gravity = android.view.Gravity.CENTER
                val size = 24.dpToPx()
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_live)
            }
            row.addView(badge)

            // Name + distance block
            val textBlock = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).also { it.marginStart = 12.dpToPx() }
            }

            val nameView = TextView(requireContext()).apply {
                text = hospital.name
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val distView = TextView(requireContext()).apply {
                text = "${"%.1f".format(hospital.distanceKm)} km away"
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
            }

            textBlock.addView(nameView)
            textBlock.addView(distView)
            row.addView(textBlock)

            // Go button
            val goBtn = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "Go"
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { openNavigation(hospital.lat, hospital.lng) }
            }
            row.addView(goBtn)

            hospitalListContainer.addView(row)

            // Divider except after last item
            if (index < hospitals.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.divider)
                    )
                }
                hospitalListContainer.addView(divider)
            }
        }
    }

    private fun openNavigation(lat: Double, lng: Double) {
        // ✅ Your fix: correct navigation intent + fallback
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
                )
            )
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                if (isGpsEnabled()) enableMyLocation()
                else showGpsEnableDialog()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Location permission required to find hospitals",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        osmMap.onResume()
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (isGpsEnabled() && !::myLocationOverlay.isInitialized) {
                enableMyLocation()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        osmMap.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        osmMap.onDetach()
    }
}