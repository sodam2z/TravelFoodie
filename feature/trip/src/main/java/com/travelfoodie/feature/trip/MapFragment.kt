package com.travelfoodie.feature.trip

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.travelfoodie.core.data.remote.GooglePlacesApi
import com.travelfoodie.core.ui.SharedTripViewModel
import com.travelfoodie.feature.trip.databinding.FragmentMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedTripViewModel by activityViewModels()

    @Inject
    lateinit var googlePlacesApi: GooglePlacesApi

    private var googleMap: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private var selectedLocationName: String? = null
    private var selectedLatLng: LatLng? = null

    private lateinit var placesAdapter: PlacesAutocompleteAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupSearchAutocomplete()

        binding.btnAddTripHere.setOnClickListener {
            if (selectedLocationName != null && selectedLatLng != null) {
                showAddTripDialog(selectedLocationName!!, selectedLatLng!!)
            }
        }

        binding.fabMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val defaultLocation = LatLng(37.5665, 126.9780)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.uiSettings?.isMyLocationButtonEnabled = false
        checkLocationPermission()
        googleMap?.setOnMapClickListener { latLng ->
            reverseGeocodeAndSelect(latLng)
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
            }
            else -> {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                android.util.Log.d("MapFragment", "Moved to current location: $currentLatLng")
            } else {
                Toast.makeText(requireContext(), "현재 위치를 찾을 수 없습니다. GPS를 확인해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearchAutocomplete() {
        placesAdapter = PlacesAutocompleteAdapter(requireContext(), googlePlacesApi, com.travelfoodie.core.data.BuildConfig.GOOGLE_PLACES_API_KEY)
        binding.searchLocation.setAdapter(placesAdapter)
        binding.searchLocation.threshold = 3

        binding.searchLocation.setOnItemClickListener { _, _, position, _ ->
            val selectedPlace = placesAdapter.getPlaceAtPosition(position)
            if (selectedPlace != null) {
                val latLng = LatLng(selectedPlace.lat ?: 37.5665, selectedPlace.lng ?: 126.9780)
                onLocationSelected(latLng, selectedPlace.description)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                binding.searchLocation.setText("")
            }
        }
    }

    private fun reverseGeocodeAndSelect(latLng: LatLng) {
        viewLifecycleOwner.lifecycleScope.launch {
            val locationName = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        when {
                            !address.locality.isNullOrEmpty() -> address.locality
                            !address.subLocality.isNullOrEmpty() -> address.subLocality
                            !address.subAdminArea.isNullOrEmpty() -> address.subAdminArea
                            !address.adminArea.isNullOrEmpty() -> address.adminArea
                            !address.countryName.isNullOrEmpty() -> address.countryName
                            else -> "선택한 위치"
                        }
                    } else { "선택한 위치" }
                } catch (e: Exception) {
                    android.util.Log.e("MapFragment", "Geocoding failed: ${e.message}")
                    "선택한 위치"
                }
            }
            onLocationSelected(latLng, locationName)
        }
    }

    private fun onLocationSelected(latLng: LatLng, locationName: String) {
        selectedMarker?.remove()
        selectedMarker = googleMap?.addMarker(MarkerOptions().position(latLng).title(locationName))
        selectedLocationName = locationName
        selectedLatLng = latLng
        binding.cardSelectedLocation.visibility = View.VISIBLE
        binding.textSelectedLocation.text = locationName
        binding.textSelectedCoordinates.text = "${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
    }

    private fun showAddTripDialog(locationName: String, latLng: LatLng) {
        val dialogBinding = com.travelfoodie.feature.trip.databinding.DialogAddTripBinding.inflate(layoutInflater)
        var startDateMillis: Long = 0
        var endDateMillis: Long = 0
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        dialogBinding.editRegion.setText(locationName)
        dialogBinding.editRegion.isEnabled = false

        dialogBinding.editStartDate.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                startDateMillis = calendar.timeInMillis
                dialogBinding.editStartDate.setText(dateFormat.format(calendar.time))
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.editEndDate.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                endDateMillis = calendar.timeInMillis
                dialogBinding.editEndDate.setText(dateFormat.format(calendar.time))
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setView(dialogBinding.root).create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val title = dialogBinding.editTripTitle.text.toString().trim()
            val members = dialogBinding.editMembers.text.toString().trim().ifEmpty { "1" }

            val selectedThemes = mutableListOf<String>()
            for (chipId in dialogBinding.chipGroupTheme.checkedChipIds) {
                val themeText = when (chipId) {
                    R.id.chip_active -> "액티브"
                    R.id.chip_culture -> "문화"
                    R.id.chip_relaxation -> "휴식"
                    R.id.chip_shopping -> "쇼핑"
                    R.id.chip_food -> "맛집 투어"
                    else -> null
                }
                if (themeText != null) selectedThemes.add(themeText)
            }
            val theme = if (selectedThemes.isEmpty()) "액티브" else selectedThemes.joinToString(",")

            when {
                title.isEmpty() -> { Toast.makeText(requireContext(), "여행 제목을 입력하세요", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                startDateMillis == 0L -> { Toast.makeText(requireContext(), "출발일을 선택하세요", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                endDateMillis == 0L -> { Toast.makeText(requireContext(), "도착일을 선택하세요", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                startDateMillis > endDateMillis -> { Toast.makeText(requireContext(), "출발일이 도착일보다 늦을 수 없습니다", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            }

            val tripId = java.util.UUID.randomUUID().toString()
            val trip = com.travelfoodie.core.data.local.entity.TripEntity(tripId = tripId, userId = "dev_user_001", title = title, startDate = startDateMillis, endDate = endDateMillis, theme = theme, members = members, regionName = locationName)

            viewLifecycleOwner.lifecycleScope.launch {
                val tripViewModel = (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.let { androidx.lifecycle.ViewModelProvider(it)[TripViewModel::class.java] }
                if (tripViewModel != null) {
                    tripViewModel.createTripWithAutoGeneration(trip, locationName, members, latLng.latitude, latLng.longitude)
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "여행을 생성하고 있습니다...\n여행 탭에서 확인하세요!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
