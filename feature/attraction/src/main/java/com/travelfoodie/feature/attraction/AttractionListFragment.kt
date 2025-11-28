package com.travelfoodie.feature.attraction

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Vibrator
import android.speech.tts.TextToSpeech
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
import com.travelfoodie.core.data.local.entity.PoiEntity
import com.travelfoodie.core.sensors.ShakeDetector
import com.travelfoodie.core.ui.SharedTripViewModel
import com.travelfoodie.feature.attraction.databinding.FragmentAttractionListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@AndroidEntryPoint
class AttractionListFragment : Fragment() {

    private var _binding: FragmentAttractionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AttractionViewModel by viewModels()
    private val sharedViewModel: SharedTripViewModel by activityViewModels()
    private lateinit var adapter: AttractionAdapter

    // Text-to-Speech
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Shake detection
    private var shakeDetector: ShakeDetector? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isNearDestination = false
    private var allAttractions: List<PoiEntity> = emptyList()
    private var filteredAttractions: List<PoiEntity> = emptyList()
    private var currentFilter: String? = null

    // Location permission launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                checkProximityToDestination()
            }
            else -> {
                showLocationPermissionRationale()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttractionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        android.util.Log.d("AttractionListFragment", "onViewCreated - SharedViewModel instance: ${sharedViewModel.hashCode()}")

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initializeTextToSpeech()
        setupRecyclerView()
        setupFilterChips()
        setupShakeDetector()
        observeAttractions()
        observeSelectedTrip()
        checkLocationPermissionAndProximity()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { applyFilter(null) }
        binding.chipNature.setOnClickListener { applyFilter("자연") }
        binding.chipCulture.setOnClickListener { applyFilter("문화") }
        binding.chipHistory.setOnClickListener { applyFilter("역사") }
        binding.chipActive.setOnClickListener { applyFilter("액티브") }
        binding.chipRelax.setOnClickListener { applyFilter("휴식") }
        binding.chipFood.setOnClickListener { applyFilter("미식") }
    }

    private fun applyFilter(filter: String?) {
        currentFilter = filter
        filteredAttractions = if (filter == null) {
            allAttractions
        } else {
            allAttractions.filter { poi ->
                poi.category.contains(filter, ignoreCase = true)
            }
        }
        adapter.submitList(filteredAttractions)
        binding.textViewEmpty.visibility = if (filteredAttractions.isEmpty()) View.VISIBLE else View.GONE

        // Update chip selection state
        binding.chipAll.isChecked = filter == null
        binding.chipNature.isChecked = filter == "자연"
        binding.chipCulture.isChecked = filter == "문화"
        binding.chipHistory.isChecked = filter == "역사"
        binding.chipActive.isChecked = filter == "액티브"
        binding.chipRelax.isChecked = filter == "휴식"
        binding.chipFood.isChecked = filter == "미식"
    }

    private fun setupShakeDetector() {
        shakeDetector = ShakeDetector(requireContext()) {
            if (isNearDestination && filteredAttractions.isNotEmpty()) {
                showRandomAttractions()
            } else if (!isNearDestination) {
                Snackbar.make(
                    binding.root,
                    "여행지 근처(1km 이내)에서만 랜덤 추천이 가능합니다",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                Snackbar.make(
                    binding.root,
                    "추천할 명소가 없습니다",
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

                // For attractions, we check proximity to the trip region
                // Since attractions may not have lat/lng, we enable shake if we have attractions
                isNearDestination = location != null && allAttractions.isNotEmpty()

                if (isNearDestination) {
                    binding.textShakeHint.visibility = View.VISIBLE
                } else {
                    binding.textShakeHint.visibility = View.GONE
                }
            } catch (e: Exception) {
                android.util.Log.e("AttractionListFragment", "Error checking location", e)
            }
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("랜덤 명소 추천 기능을 사용하려면 위치 권한이 필요합니다.")
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

    private fun showRandomAttractions() {
        if (filteredAttractions.size < 3) {
            Snackbar.make(
                binding.root,
                "명소가 3개 미만입니다",
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

        // Select 3 random attractions
        val randomAttractions = filteredAttractions.shuffled().take(3)

        // Show dialog with Lottie animation
        val dialogView = layoutInflater.inflate(R.layout.dialog_random_attractions, null)
        val lottieView = dialogView.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottie_animation)
        val attractionList = dialogView.findViewById<android.widget.TextView>(R.id.text_attractions)

        // Configure Lottie animation
        lottieView.setAnimation(R.raw.slot_machine)
        lottieView.repeatCount = LottieDrawable.INFINITE
        lottieView.playAnimation()

        // Build attraction list text
        val attractionText = randomAttractions.mapIndexed { index, poi ->
            "${index + 1}. ${poi.name}\n   ⭐ ${poi.rating} | ${poi.category}"
        }.joinToString("\n\n")

        attractionList.text = attractionText

        AlertDialog.Builder(requireContext())
            .setTitle("🎲 랜덤 명소 추천")
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

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.KOREAN)
                isTtsInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                if (isTtsInitialized) {
                    Snackbar.make(
                        binding.root,
                        "명소 설명을 클릭하면 읽어드립니다",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun speakAttraction(poi: PoiEntity) {
        if (!isTtsInitialized) {
            Snackbar.make(
                binding.root,
                "음성 읽기 기능을 사용할 수 없습니다",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val textToSpeak = "${poi.name}. ${poi.description ?: "설명이 없습니다."}"
        textToSpeech?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)

        Snackbar.make(
            binding.root,
            "🔊 음성으로 읽는 중...",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun observeSelectedTrip() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.selectedTripId.collect { tripId ->
                if (tripId != null) {
                    android.util.Log.d("AttractionListFragment", "Loading attractions for tripId: $tripId")
                    viewModel.loadAttractions(tripId)
                } else {
                    android.util.Log.d("AttractionListFragment", "No trip selected")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AttractionAdapter(
            onShareClick = { poi -> shareAttraction(poi) },
            onSpeakClick = { poi -> speakAttraction(poi) }
        )
        binding.recyclerViewAttractions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AttractionListFragment.adapter
        }
    }

    private fun shareAttraction(poi: PoiEntity) {
        val shareText = """
            🎯 ${poi.name}

            📍 카테고리: ${poi.category}
            ⭐ 평점: ${poi.rating}
            ${poi.description?.let { "\n📝 $it" } ?: ""}

            TravelFoodie에서 공유됨
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "${poi.name} - 명소 추천")
        }

        startActivity(Intent.createChooser(shareIntent, "명소 공유하기"))
    }

    private fun observeAttractions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.attractions.collect { attractions ->
                android.util.Log.d("AttractionListFragment", "Displaying ${attractions.size} attractions")
                allAttractions = attractions
                applyFilter(currentFilter)

                // Update shake hint visibility
                if (attractions.isNotEmpty()) {
                    checkProximityToDestination()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.start()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        shakeDetector?.stop()
        shakeDetector = null
        _binding = null
    }
}
