package com.travelfoodie.feature.restaurant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieDrawable
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.travelfoodie.core.data.local.entity.RestaurantEntity
import com.travelfoodie.core.sensors.ShakeDetector
import com.travelfoodie.core.ui.SharedTripViewModel
import com.travelfoodie.feature.restaurant.databinding.FragmentRestaurantListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class RestaurantListFragment : Fragment() {

    private var _binding: FragmentRestaurantListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RestaurantViewModel by viewModels()
    private val sharedViewModel: SharedTripViewModel by activityViewModels()
    private lateinit var adapter: RestaurantAdapter

    // Shake detection
    private var shakeDetector: ShakeDetector? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isNearDestination = false
    private var allRestaurants: List<RestaurantEntity> = emptyList()
    private var filteredRestaurants: List<RestaurantEntity> = emptyList()
    private var currentFilter: String? = null

    // Location permission launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Location access granted, check proximity
                checkProximityToDestination()
            }
            else -> {
                // No location access granted
                showLocationPermissionRationale()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestaurantListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupRecyclerView()
        setupFilterChips()
        observeRestaurants()
        observeSelectedTrip()
        setupShakeDetector()
        checkLocationPermissionAndProximity()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { applyFilter(null) }
        binding.chipKorean.setOnClickListener { applyFilter("한식") }
        binding.chipJapanese.setOnClickListener { applyFilter("일식") }
        binding.chipChinese.setOnClickListener { applyFilter("중식") }
        binding.chipWestern.setOnClickListener { applyFilter("양식") }
        binding.chipCafe.setOnClickListener { applyFilter("카페") }
        binding.chipDessert.setOnClickListener { applyFilter("디저트") }
        binding.chipSeafood.setOnClickListener { applyFilter("해산물") }
    }

    private fun applyFilter(filter: String?) {
        currentFilter = filter
        filteredRestaurants = if (filter == null) {
            allRestaurants
        } else {
            allRestaurants.filter { restaurant ->
                restaurant.category.contains(filter, ignoreCase = true)
            }
        }
        adapter.submitList(filteredRestaurants)
        binding.textViewEmpty.visibility = if (filteredRestaurants.isEmpty()) View.VISIBLE else View.GONE

        // Update chip selection state
        binding.chipAll.isChecked = filter == null
        binding.chipKorean.isChecked = filter == "한식"
        binding.chipJapanese.isChecked = filter == "일식"
        binding.chipChinese.isChecked = filter == "중식"
        binding.chipWestern.isChecked = filter == "양식"
        binding.chipCafe.isChecked = filter == "카페"
        binding.chipDessert.isChecked = filter == "디저트"
        binding.chipSeafood.isChecked = filter == "해산물"
    }

    private fun setupShakeDetector() {
        android.util.Log.d("RestaurantListFragment", "Setting up ShakeDetector")
        shakeDetector = ShakeDetector(requireContext()) {
            android.util.Log.d("RestaurantListFragment", "Shake callback triggered! isNearDestination=$isNearDestination, restaurantCount=${filteredRestaurants.size}")
            if (isNearDestination && filteredRestaurants.isNotEmpty()) {
                android.util.Log.d("RestaurantListFragment", "Showing random restaurants dialog")
                showRandomRestaurants()
            } else if (!isNearDestination) {
                android.util.Log.d("RestaurantListFragment", "Not near destination - showing warning")
                Snackbar.make(
                    binding.root,
                    "여행지 근처(1km 이내)에서만 랜덤 추천이 가능합니다",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                android.util.Log.d("RestaurantListFragment", "No restaurants to recommend")
                Snackbar.make(
                    binding.root,
                    "추천할 맛집이 없습니다",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkLocationPermissionAndProximity() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkProximityToDestination()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun checkProximityToDestination() {
        lifecycleScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@launch
                }

                @SuppressLint("MissingPermission")
                val location = fusedLocationClient.lastLocation.await()
                if (location != null && allRestaurants.isNotEmpty()) {
                    // Check if we're within 1km of any restaurant
                    val nearbyRestaurants = allRestaurants.filter { restaurant ->
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude,
                            location.longitude,
                            restaurant.lat,
                            restaurant.lng,
                            results
                        )
                        results[0] <= 1000 // 1km in meters
                    }

                    isNearDestination = nearbyRestaurants.isNotEmpty()

                    if (isNearDestination) {
                        binding.textShakeHint.visibility = View.VISIBLE
                    } else {
                        binding.textShakeHint.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RestaurantListFragment", "Error checking location", e)
            }
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("랜덤 맛집 추천 기능을 사용하려면 위치 권한이 필요합니다. 여행지 근처(1km 이내)에서만 랜덤 추천을 받을 수 있습니다.")
            .setPositiveButton("권한 허용") { _, _ ->
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRandomRestaurants() {
        if (filteredRestaurants.size < 3) {
            Snackbar.make(
                binding.root,
                "맛집이 3개 미만입니다",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Vibration feedback
        val vibrator = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }

        // Select 3 random restaurants
        val randomRestaurants = filteredRestaurants.shuffled().take(3)

        // Show dialog with Lottie animation
        val dialogView = layoutInflater.inflate(R.layout.dialog_random_restaurants, null)
        val lottieView = dialogView.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottie_animation)
        val restaurantList = dialogView.findViewById<android.widget.TextView>(R.id.text_restaurants)

        // Configure Lottie animation
        lottieView.setAnimation(R.raw.slot_machine)
        lottieView.repeatCount = LottieDrawable.INFINITE
        lottieView.playAnimation()

        // Build restaurant list text
        val restaurantText = randomRestaurants.mapIndexed { index, restaurant ->
            "${index + 1}. ${restaurant.name}\n   ⭐ ${restaurant.rating} | ${restaurant.category}"
        }.joinToString("\n\n")

        restaurantList.text = restaurantText

        AlertDialog.Builder(requireContext())
            .setTitle("🎲 랜덤 맛집 추천")
            .setView(dialogView)
            .setPositiveButton("확인") { dialog, _ ->
                lottieView.cancelAnimation()
                dialog.dismiss()
            }
            .setOnDismissListener {
                lottieView.cancelAnimation()
            }
            .show()
    }

    /**
     * Observes SharedTripViewModel for trip selection
     * When TripListFragment creates/selects a trip, this automatically loads restaurants
     */
    private fun observeSelectedTrip() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.selectedTripId.collect { tripId ->
                if (tripId != null) {
                    // Auto-load restaurants for the selected trip
                    android.util.Log.d("RestaurantListFragment", "Loading restaurants for tripId: $tripId")
                    viewModel.loadRestaurants(tripId)
                } else {
                    android.util.Log.d("RestaurantListFragment", "No trip selected")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RestaurantAdapter(
            onRestaurantClick = { restaurant ->
                // Google Maps will open automatically in the adapter
            },
            onShareClick = { restaurant -> shareRestaurant(restaurant) }
        )

        binding.recyclerViewRestaurants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RestaurantListFragment.adapter
        }
    }

    private fun shareRestaurant(restaurant: RestaurantEntity) {
        val shareText = """
            🍽️ ${restaurant.name}

            📍 카테고리: ${restaurant.category}
            ⭐ 평점: ${restaurant.rating}
            ${restaurant.distance?.let { "📏 거리: %.1f km".format(it) } ?: ""}

            TravelFoodie에서 공유됨
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "${restaurant.name} - 맛집 추천")
        }

        startActivity(Intent.createChooser(shareIntent, "맛집 공유하기"))
    }

    private fun observeRestaurants() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.restaurants.collect { restaurants ->
                allRestaurants = restaurants
                applyFilter(currentFilter)

                // Update current restaurants for shake feature
                if (restaurants.isNotEmpty()) {
                    checkProximityToDestination()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("RestaurantListFragment", "onResume - starting ShakeDetector")
        shakeDetector?.start()
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("RestaurantListFragment", "onPause - stopping ShakeDetector")
        shakeDetector?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shakeDetector?.stop()
        shakeDetector = null
        _binding = null
    }
}
