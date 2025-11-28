package com.travelfoodie.feature.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.travelfoodie.feature.voice.databinding.FragmentVoiceMemoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class VoiceMemoFragment : Fragment() {

    private var _binding: FragmentVoiceMemoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VoiceMemoViewModel by viewModels()
    private lateinit var adapter: VoiceMemoAdapter
    private var textToSpeech: TextToSpeech? = null

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setRecording(false)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.setTranscription(spokenText)
                binding.editTextTranscription.setText(spokenText)
            }
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition()
        } else {
            Toast.makeText(requireContext(), "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceMemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTts()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.KOREAN
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = VoiceMemoAdapter(
            onPlayClick = { memo ->
                // Play the transcribed text using TTS
                textToSpeech?.speak(memo.transcribedText, TextToSpeech.QUEUE_FLUSH, null, null)
                Toast.makeText(requireContext(), "재생 중...", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { memo ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("메모 삭제")
                    .setMessage("이 음성 메모를 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        viewModel.deleteMemo(memo.memoId)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        )

        binding.recyclerViewMemos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@VoiceMemoFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.buttonRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeechRecognition()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.buttonSave.setOnClickListener {
            val transcription = binding.editTextTranscription.text.toString().trim()
            val title = binding.editTextTitle.text.toString().trim()

            if (transcription.isEmpty()) {
                Toast.makeText(requireContext(), "먼저 음성을 녹음해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveMemo(title, transcription)
            binding.editTextTitle.text?.clear()
            binding.editTextTranscription.text?.clear()
            Toast.makeText(requireContext(), "메모가 저장되었습니다", Toast.LENGTH_SHORT).show()
        }

        binding.buttonClear.setOnClickListener {
            binding.editTextTitle.text?.clear()
            binding.editTextTranscription.text?.clear()
            viewModel.setTranscription("")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.memos.collect { memos ->
                adapter.submitList(memos)
                binding.textViewEmpty.visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRecording.collect { isRecording ->
                binding.buttonRecord.text = if (isRecording) "녹음 중..." else "녹음 시작"
                binding.buttonRecord.isEnabled = !isRecording
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun startSpeechRecognition() {
        viewModel.setRecording(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "음성 메모를 말씀해주세요")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.setRecording(false)
            Toast.makeText(requireContext(), "음성인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        _binding = null
    }
}
