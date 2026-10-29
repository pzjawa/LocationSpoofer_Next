package com.vincenthzr.locationspoofer.ui.components.map

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer
import com.tencent.tencentmap.mapsdk.maps.TencentMapOptions
import com.tencent.tencentmap.mapsdk.maps.TextureMapView
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import com.tencent.tencentmap.mapsdk.maps.model.CircleOptions
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.LatLngBounds
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions
import com.tencent.tencentmap.mapsdk.maps.model.PolylineOptions
import com.vincenthzr.locationspoofer.data.model.AppMapType
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapMarker
import com.vincenthzr.locationspoofer.ui.components.MarkerType

// Tencent uses GCJ-02, matching the application's stored coordinates.
class TencentMapControllerImpl(
    private val map: TencentMap,
    private val context: Context
) : AppMapController {
    private var isDarkMode = false
    private var currentMapType = AppMapType.NORMAL
    private var cameraFinishListener: ((Double, Double) -> Unit)? = null
    private var cameraMoveListener: ((Double, Double) -> Unit)? = null

    init {
        map.setOnCameraChangeListener(object : TencentMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition) {
                position.target?.let { cameraMoveListener?.invoke(it.latitude, it.longitude) }
            }

            override fun onCameraChangeFinished(position: CameraPosition) {
                position.target?.let { cameraFinishListener?.invoke(it.latitude, it.longitude) }
            }
        })
    }

    override fun clear() = map.clearAllOverlays()

    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        if (points.size < 2) return
        map.addPolyline(PolylineOptions().color(colorInt).width(width).apply {
            points.forEach { add(LatLng(it.first, it.second)) }
        })
    }

    override fun addCircle(
        lat: Double, lng: Double, radius: Double, fillColorInt: Int,
        strokeColorInt: Int, strokeWidth: Float
    ) {
        map.addCircle(CircleOptions().center(LatLng(lat, lng)).radius(radius)
            .fillColor(fillColorInt).strokeColor(strokeColorInt).strokeWidth(strokeWidth))
    }

    override fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker {
        val marker = map.addMarker(MarkerOptions(LatLng(lat, lng)).title(title)
            .icon(BitmapDescriptorFactory.fromBitmap(GaodeMarkerHelper.getMarkerBitmap(context, title, type)))
            .anchor(0.5f, 0.9f))
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) {
                marker.position = LatLng(lat, lng)
            }
        }
    }

    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        map.animateCamera(if (zoom == null) CameraUpdateFactory.newLatLng(LatLng(lat, lng))
            else CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        map.moveCamera(if (zoom == null) CameraUpdateFactory.newLatLng(LatLng(lat, lng))
            else CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    override fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) =
        fitBounds(points, padding, padding, padding, padding)

    override fun fitBounds(
        points: List<Pair<Double, Double>>, paddingLeft: Int, paddingTop: Int,
        paddingRight: Int, paddingBottom: Int
    ) {
        if (points.isEmpty()) return
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.first, it.second)) }
        map.animateCamera(CameraUpdateFactory.newLatLngBoundsRect(
            builder.build(), paddingLeft, paddingRight, paddingTop, paddingBottom))
    }

    override val cameraTargetLat: Double? get() = map.cameraPosition?.target?.latitude
    override val cameraTargetLng: Double? get() = map.cameraPosition?.target?.longitude

    override fun setOnCameraChangeListener(onFinish: (Double, Double) -> Unit) {
        cameraFinishListener = onFinish
    }

    override fun setOnCameraMoveListener(onMove: (Double, Double) -> Unit) {
        cameraMoveListener = onMove
    }

    override fun disableUiControls() {
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isCompassEnabled = false
            setAllGesturesEnabled(true)
        }
    }

    override fun setDarkMode(isDark: Boolean, context: Context) {
        isDarkMode = isDark
        setMapType(currentMapType)
    }

    override fun setMapType(type: AppMapType) {
        currentMapType = type
        map.mapType = when {
            type == AppMapType.SATELLITE -> TencentMap.MAP_TYPE_SATELLITE
            isDarkMode -> TencentMap.MAP_TYPE_DARK
            else -> TencentMap.MAP_TYPE_NORMAL
        }
        map.setBuilding3dEffectEnable(type == AppMapType.MAP_3D)
        val camera = map.cameraPosition ?: return
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(
            camera.target, camera.zoom, if (type == AppMapType.MAP_3D) 45f else 0f, camera.bearing)))
    }
}

@Composable
fun TencentMapViewContainer(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isDark: Boolean,
    modifier: Modifier,
    onMapReady: (AppMapController) -> Unit,
    onControllerCreated: (AppMapController) -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var consent by remember(prefs) { mutableStateOf(prefs.getBoolean("tencent_privacy_agreed", false)) }
    var customKey by remember(prefs) { mutableStateOf(prefs.getString("tencent_api_key", "").orEmpty()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == "tencent_api_key") customKey = preferences.getString(key, "").orEmpty()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val bundledKey = remember(context) {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("TencentMapSDK").orEmpty()
    }
    val apiKey = customKey.trim().ifEmpty { bundledKey.trim() }
    if (apiKey.isEmpty() || !consent) {
        val uriHandler = LocalUriHandler.current
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (apiKey.isEmpty()) {
                Text(stringResource(R.string.tencent_key_required))
            } else {
                Text(stringResource(R.string.tencent_privacy_message))
                TextButton(onClick = {
                    uriHandler.openUri("https://privacy.qq.com/document/preview/a10a8634f237464da9a95f4f07e73e40")
                }) { Text(stringResource(R.string.tencent_privacy_title)) }
                Button(onClick = {
                    prefs.edit().putBoolean("tencent_privacy_agreed", true).apply()
                    consent = true
                }) { Text(stringResource(R.string.tencent_privacy_agree)) }
            }
        }
        return
    }

    key(apiKey) {
        val mapView = remember(context) {
            TencentMapInitializer.setAgreePrivacy(context.applicationContext, true)
            TencentMapInitializer.start(context.applicationContext)
            TextureMapView(context, TencentMapOptions().setMapKey(apiKey))
        }
        val ready by rememberUpdatedState(onMapReady)
        val created by rememberUpdatedState(onControllerCreated)
        val dark by rememberUpdatedState(isDark)
        DisposableEffect(mapView, lifecycleOwner) {
            var started = false
            var resumed = false
            var destroyed = false
            var delivered = false
            val loadedCallback = TencentMap.OnMapLoadedCallback {
                if (!destroyed && !delivered) {
                    delivered = true
                    val controller = TencentMapControllerImpl(mapView.map, context)
                    controller.setDarkMode(dark, context)
                    created(controller)
                    ready(controller)
                }
            }
            mapView.map.addOnMapLoadedCallback(loadedCallback)
            fun sync(state: Lifecycle.State) {
                if (destroyed) return
                if (resumed && !state.isAtLeast(Lifecycle.State.RESUMED)) {
                    mapView.onPause()
                    resumed = false
                }
                if (started && !state.isAtLeast(Lifecycle.State.STARTED)) {
                    mapView.onStop()
                    started = false
                }
                if (state == Lifecycle.State.DESTROYED) {
                    mapView.map.removeOnMapLoadedCallback(loadedCallback)
                    mapView.map.setOnCameraChangeListener(null)
                    mapView.onDestroy()
                    destroyed = true
                    return
                }
                if (!started && state.isAtLeast(Lifecycle.State.STARTED)) {
                    mapView.onStart()
                    started = true
                }
                if (!resumed && state.isAtLeast(Lifecycle.State.RESUMED)) {
                    mapView.onResume()
                    resumed = true
                }
            }
            val observer = LifecycleEventObserver { _, _ -> sync(lifecycleOwner.lifecycle.currentState) }
            lifecycleOwner.lifecycle.addObserver(observer)
            sync(lifecycleOwner.lifecycle.currentState)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                sync(Lifecycle.State.DESTROYED)
            }
        }
        AndroidView(
            factory = {
                mapView.apply {
                    setOnTouchListener { view, _ ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                }
            },
            modifier = modifier
        )
    }
}
