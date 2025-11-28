package com.travelfoodie.feature.board

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.travelfoodie.core.ui.SharedTripViewModel
import com.travelfoodie.feature.board.databinding.FragmentChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    // Use lazy to avoid crash if SharedTripViewModel is not available
    private val sharedViewModel: SharedTripViewModel? by lazy {
        try {
            val vm: SharedTripViewModel by activityViewModels()
            vm
        } catch (e: Exception) {
            null
        }
    }
    private lateinit var adapter: ChatAdapter
    private var currentChatRoomId: String? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadAndSendImage(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Get chatRoomId from arguments if available
            currentChatRoomId = arguments?.getString("chatRoomId")

            setupMenu()
            setupRecyclerView()
            setupMessageInput()
            observeMessages()

            if (currentChatRoomId != null) {
                // Load messages for the specific chat room
                viewModel.loadMessagesByChatRoom(currentChatRoomId!!)
            } else {
                // Fall back to observing selected trip
                observeSelectedTrip()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                "채팅을 불러오는 중 오류가 발생했습니다",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupMenu() {
        binding.buttonMenu.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add(0, MENU_PARTICIPANTS, 0, "참여자 보기")
            popup.menu.add(0, MENU_INVITE, 1, "초대하기")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_PARTICIPANTS -> {
                        showParticipantsDialog()
                        true
                    }
                    MENU_INVITE -> {
                        showInviteDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun showParticipantsDialog() {
        val chatRoomId = currentChatRoomId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val members = viewModel.getChatRoomMembers(chatRoomId)
                val memberEmails = viewModel.getChatRoomMemberEmails(chatRoomId)

                val allMembers = mutableListOf<String>()
                allMembers.addAll(members.map { "UID: ${it.take(8)}..." })
                allMembers.addAll(memberEmails.map { it })

                if (allMembers.isEmpty()) {
                    allMembers.add("참여자 정보 없음")
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("참여자 목록 (${allMembers.size}명)")
                    .setItems(allMembers.toTypedArray(), null)
                    .setPositiveButton("확인", null)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "참여자 정보를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInviteDialog() {
        val chatRoomId = currentChatRoomId ?: return

        val editText = EditText(requireContext()).apply {
            hint = "이메일 주소 입력"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("새 멤버 초대")
            .setMessage("초대할 사람의 이메일을 입력하세요")
            .setView(editText)
            .setPositiveButton("초대") { _, _ ->
                val email = editText.text.toString().trim().lowercase()
                if (email.isNotEmpty() && email.contains("@")) {
                    inviteMember(chatRoomId, email)
                } else {
                    Toast.makeText(requireContext(), "올바른 이메일을 입력하세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun inviteMember(chatRoomId: String, email: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.inviteMemberByEmail(chatRoomId, email)
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "$email 님을 초대했습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "초대 실패: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "초대 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val MENU_PARTICIPANTS = 1001
        private const val MENU_INVITE = 1002
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter { imageUrl ->
            // Open image in full screen when clicked
            showImageDialog(imageUrl)
        }
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatFragment.adapter
        }
    }

    private fun setupMessageInput() {
        // Image button click
        binding.buttonImage.setOnClickListener {
            openImagePicker()
        }

        // Send button click
        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.sendMessage(message)
                    binding.editTextMessage.text?.clear()
                }
            } else {
                Snackbar.make(binding.root, "메시지를 입력하세요", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private fun uploadAndSendImage(uri: Uri) {
        val chatRoomId = currentChatRoomId ?: return

        Toast.makeText(requireContext(), "이미지 업로드 중...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.uploadAndSendImage(chatRoomId, uri, requireContext().contentResolver)
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "이미지 전송 완료", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "이미지 전송 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "이미지 업로드 중 오류 발생", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImageDialog(imageUrl: String) {
        val imageView = android.widget.ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }

        // Load image using Coil
        coil.ImageLoader(requireContext()).enqueue(
            coil.request.ImageRequest.Builder(requireContext())
                .data(imageUrl)
                .target(imageView)
                .build()
        )

        AlertDialog.Builder(requireContext())
            .setView(imageView)
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerViewChat.smoothScrollToPosition(messages.size - 1)
                }
                binding.textViewEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeSelectedTrip() {
        val vm = sharedViewModel ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            vm.selectedTripId.collect { tripId ->
                if (tripId != null) {
                    viewModel.loadMessagesForTrip(tripId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
