package com.mclowicz.mctracker.features.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.mclowicz.mctracker.R
import com.mclowicz.mctracker.databinding.FragmentMapsBinding
import com.mclowicz.mctracker.features.map.MapUtil.calculateDistance
import com.mclowicz.mctracker.features.map.MapUtil.calculateElapsedTime
import com.mclowicz.mctracker.features.map.MapUtil.observeTracking
import com.mclowicz.mctracker.features.map.MapUtil.setCameraPosition
import com.mclowicz.mctracker.model.Result
import com.mclowicz.mctracker.permission.RequestPermissionHandler
import com.mclowicz.mctracker.service.TrackerService
import com.mclowicz.mctracker.util.Constants.ACTION_SERVICE_START
import com.mclowicz.mctracker.util.Constants.ACTION_SERVICE_STOP
import com.mclowicz.mctracker.util.disable
import com.mclowicz.mctracker.util.enable
import com.mclowicz.mctracker.util.hide
import com.mclowicz.mctracker.util.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsFragment : Fragment(R.layout.fragment_maps), OnMapReadyCallback,
    GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMarkerClickListener {

    private lateinit var binding: FragmentMapsBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var requestPermissionHandler: RequestPermissionHandler
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationList = mutableListOf<LatLng>()
    private var polylineList = mutableListOf<Polyline>()
    private var markerList = mutableListOf<Marker>()
    private var startTime = 0L
    private var stopTime = 0L
    val started = MutableLiveData<Boolean>(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMapsBinding.bind(view)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        initComponents()
        bindUI()
    }

    private fun initComponents() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        requestPermissionHandler = RequestPermissionHandler(
            context = this,
            requestedPermissions = arrayOf(BACKGROUND_PERMISSION),
            onGrantedPermissionsCallback = { handleGrantedPermission() },
            onDeniedPermissionCallback = { handleNotGrantedPermission() }
        ).apply { register() }
    }

    private fun bindUI() {
        binding.apply {
            buttonStart.setOnClickListener {
                onStartButtonClicked()
            }
            buttonReset.setOnClickListener {
                onResetButtonClicked()
            }
            buttonStop.setOnClickListener {
                onStopButtonClicked()
            }
        }
    }

    private fun onStopButtonClicked() {
        stopForegroundService()
        binding.apply {
            buttonStop.hide()
            buttonStart.show()
        }
    }

    private fun onResetButtonClicked() {
        mapReset()
    }

    private fun onStartButtonClicked() {
        if (hasBackgroundLocationPermission()) {
            startCountdown()
            binding.apply {
                buttonStart.disable()
                buttonStart.hide()
                buttonStop.show()
            }
        } else {
            requestPermissionHandler.request()
        }
    }

    private fun startCountdown() {
        binding.apply {
            textTimer.show()
            buttonStop.disable()
            val timer: CountDownTimer = object : CountDownTimer(4000, 1000) {
                override fun onTick(milisUntilFinished: Long) {
                    val currentSecond = milisUntilFinished / 1000
                    if (currentSecond.toString() == "0") {
                        binding.apply {
                            textTimer.text = "GO"
                            textTimer.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.black)
                            )
                        }
                    } else {
                        binding.apply {
                            textTimer.text = currentSecond.toString()
                            textTimer.setTextColor(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.design_default_color_error
                                )
                            )
                        }
                    }
                }

                override fun onFinish() {
                    sendActionCommandToService(ACTION_SERVICE_START)
                    binding.textTimer.hide()
                }
            }
            timer.start()
        }
    }

    private fun stopForegroundService() {
        binding.buttonStart.disable()
        sendActionCommandToService(ACTION_SERVICE_STOP)
    }

    private fun sendActionCommandToService(action: String) {
        Intent(requireContext(), TrackerService::class.java)
            .apply {
                this.action = action
                requireContext().startService(this)
            }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        this.googleMap.apply {
            setOnMyLocationButtonClickListener(this@MapsFragment)
            setOnMarkerClickListener(this@MapsFragment)
            isMyLocationEnabled = true
            with(uiSettings) {
                isZoomControlsEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isScrollGesturesEnabled = false
            }
        }
        observeTrackerService()
    }

    private fun observeTrackerService() {
        TrackerService.apply {
            locationList.observe(viewLifecycleOwner) {
                it?.let {
                    this@MapsFragment.locationList = it
                    if (this@MapsFragment.locationList.size > 1) {
                        binding.buttonStop.enable()
                    }
                    drawPolyline()
                    followPolyline()
                }
            }
            startTime.observe(viewLifecycleOwner) {
                this@MapsFragment.startTime = it
            }
            stopTime.observe(viewLifecycleOwner) {
                this@MapsFragment.stopTime = it
                if (this@MapsFragment.stopTime != 0L) {
                    showBiggerPicture()
                    displayResults()
                }
            }
            started.observe(viewLifecycleOwner) {
                this@MapsFragment.started.value = it
                binding.apply {
                    textHint.observeTracking(it)
                    buttonStop.observeTracking(it)
                }
            }
        }
    }

    private fun drawPolyline() {
        googleMap.addPolyline(
            PolylineOptions().apply {
                width(10f)
                color(Color.BLUE)
                jointType(JointType.ROUND)
                startCap(ButtCap())
                endCap(ButtCap())
                addAll(locationList)
            }
        ).also { polylineList.add(it) }
    }

    private fun followPolyline() {
        if (locationList.isNotEmpty()) {
            googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    setCameraPosition(
                        locationList.last()
                    )
                ), 100, null
            )
        }
    }

    private fun showBiggerPicture() {
        val bounds = LatLngBounds.Builder()
        for (location in locationList) {
            bounds.include(location)
        }
        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(), 100
            ), 2000, null
        )
        addMarker(locationList.first())
        addMarker(locationList.last())
    }

    private fun addMarker(position: LatLng) {
        val marker = googleMap.addMarker(MarkerOptions().position(position))
        marker?.let { markerList.add(it) }
    }

    private fun displayResults() {
        val result = Result(
            calculateDistance(locationList),
            calculateElapsedTime(startTime, stopTime)
        )
        lifecycleScope.launch {
            delay(2500)
            val directions = MapsFragmentDirections.actionMapsFragmentToResultFragment(result)
            findNavController().navigate(directions)
            with (binding) {
                buttonStart.apply {
                    hide()
                    enable()
                }
                buttonStop.hide()
                buttonReset.show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapReset() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val lastKnownLocation = LatLng(
                it.result.latitude,
                it.result.longitude
            )
            for (polyline in polylineList) {
                polyline.remove()
            }
            googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    setCameraPosition(lastKnownLocation)
                )
            )
            locationList.clear()
            for (marker in markerList) {
                marker.remove()
            }
            binding.apply {
                buttonReset.hide()
                buttonStart.show()
            }
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        binding.textHint.animate().alpha(0f).duration = 1500
        lifecycleScope.launch {
            delay(2500)
            binding.apply {
                buttonStart.show()
                textHint.hide()
            }
        }
        return false
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        return true
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return activity?.let {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    BACKGROUND_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED
            } ?: false
        }
        return true
    }

    private fun handleGrantedPermission() {
        showSnackBar("Granted")
    }

    private fun handleNotGrantedPermission() {
        showSnackBar(getString(R.string.permission_denied))
    }

    private fun showSnackBar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .show()
    }

    companion object {
        private const val BACKGROUND_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
}