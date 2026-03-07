package com.elendal.gpsalarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.appbar.MaterialToolbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin

class MapPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_RADIUS = "extra_radius"
    }

    private lateinit var mapView: MapView
    private lateinit var tvRadiusLabel: TextView
    private lateinit var sliderRadius: Slider
    private lateinit var btnUseCurrentLocation: MaterialButton
    private lateinit var btnConfirm: MaterialButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var circleOverlay: Polygon? = null

    private var currentRadius: Float = 500f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_map_picker)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        mapView = findViewById(R.id.map_view)
        tvRadiusLabel = findViewById(R.id.tv_radius_label)
        sliderRadius = findViewById(R.id.slider_radius)
        btnUseCurrentLocation = findViewById(R.id.btn_use_current_location)
        btnConfirm = findViewById(R.id.btn_confirm)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get intent extras
        val initLat = intent.getDoubleExtra(EXTRA_LAT, 51.5)
        val initLng = intent.getDoubleExtra(EXTRA_LNG, -0.1)
        currentRadius = intent.getFloatExtra(EXTRA_RADIUS, 500f)

        // Setup map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(initLat, initLng))

        // Add my location overlay
        val locationProvider = GpsMyLocationProvider(this)
        myLocationOverlay = MyLocationNewOverlay(locationProvider, mapView).also {
            it.enableMyLocation()
            mapView.overlays.add(it)
        }

        // Setup slider
        sliderRadius.value = currentRadius.coerceIn(100f, 2000f)
        updateRadiusLabel(currentRadius)

        sliderRadius.addOnChangeListener { _, value, _ ->
            currentRadius = value
            updateRadiusLabel(value)
            updateCircleOverlay()
        }

        // Draw initial circle
        updateCircleOverlay()

        // Map scroll listener to redraw circle when map is panned
        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                updateCircleOverlay()
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateCircleOverlay()
                return false
            }
        })

        btnUseCurrentLocation.setOnClickListener { moveToCurrentLocation() }
        btnConfirm.setOnClickListener { confirmLocation() }
    }

    private fun updateRadiusLabel(radius: Float) {
        tvRadiusLabel.text = "Radius: ${radius.toInt()} m"
    }

    private fun updateCircleOverlay() {
        val center = mapView.mapCenter
        val centerGeoPoint = GeoPoint(center.latitude, center.longitude)

        // Remove old circle overlay
        circleOverlay?.let { mapView.overlays.remove(it) }

        val polygon = Polygon(mapView).apply {
            points = generateCirclePoints(centerGeoPoint, currentRadius.toDouble())
            fillColor = 0x336750A4.toInt()
            strokeColor = 0xFF6750A4.toInt()
            strokeWidth = 3f
        }

        circleOverlay = polygon
        mapView.overlays.add(polygon)
        mapView.invalidate()
    }

    /**
     * Generates N GeoPoints arranged in a circle around [center] with the given [radiusMeters].
     */
    private fun generateCirclePoints(center: GeoPoint, radiusMeters: Double, numPoints: Int = 72): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0 // meters
        val lat = Math.toRadians(center.latitude)
        val lng = Math.toRadians(center.longitude)
        val angularRadius = radiusMeters / earthRadius

        for (i in 0..numPoints) {
            val angle = Math.toRadians((i * 360.0) / numPoints)
            val pointLat = Math.asin(
                sin(lat) * cos(angularRadius) + cos(lat) * sin(angularRadius) * cos(angle)
            )
            val pointLng = lng + Math.atan2(
                sin(angle) * sin(angularRadius) * cos(lat),
                cos(angularRadius) - sin(lat) * sin(pointLat)
            )
            points.add(GeoPoint(Math.toDegrees(pointLat), Math.toDegrees(pointLng)))
        }
        return points
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.animateTo(geoPoint)
            }
        }
    }

    private fun confirmLocation() {
        val center = mapView.mapCenter
        val result = Intent().apply {
            putExtra(EXTRA_LAT, center.latitude)
            putExtra(EXTRA_LNG, center.longitude)
            putExtra(EXTRA_RADIUS, currentRadius)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        myLocationOverlay?.disableMyLocation()
    }
}
