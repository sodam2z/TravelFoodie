package com.travelfoodie.feature.board

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.travelfoodie.feature.board.databinding.FragmentChatRoomListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatRoomListFragment : Fragment() {

    private var _binding: FragmentChatRoomListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatRoomAdapter: ChatRoomAdapter

    // Contact picker launcher
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                handleSelectedContact(contactUri)
            }
        }
    }

    // Permission launcher for contacts
    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openPhoneContacts()
        } else {
            Toast.makeText(requireContext(), "연락처 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatRoomListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeChatRooms()
    }

    private fun setupRecyclerView() {
        chatRoomAdapter = ChatRoomAdapter { chatRoomId ->
            val bundle = Bundle().apply {
                putString("chatRoomId", chatRoomId)
            }
            findNavController().navigate(R.id.action_chatRoomList_to_chat, bundle)
        }

        binding.recyclerViewChatRooms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatRoomAdapter
        }
    }

    private fun setupFab() {
        binding.fabCreateChat.setOnClickListener {
            showCreateChatMenu()
        }
    }

    private fun showCreateChatMenu() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_chat, null)
        bottomSheet.setContentView(view)

        // Google Email option
        view.findViewById<View>(R.id.optionGoogleEmail)?.setOnClickListener {
            bottomSheet.dismiss()
            createChatWithGoogleEmail()
        }

        // Phone Contacts option
        view.findViewById<View>(R.id.optionPhoneContacts)?.setOnClickListener {
            bottomSheet.dismiss()
            checkContactsPermissionAndOpen()
        }

        bottomSheet.show()
    }

    private fun createChatWithGoogleEmail() {
        // Try Firebase Auth first, then fall back to Google Sign-In
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())

        val email: String?
        val displayName: String?

        when {
            firebaseUser != null -> {
                email = firebaseUser.email
                displayName = firebaseUser.displayName ?: "사용자"
            }
            googleAccount != null -> {
                email = googleAccount.email
                displayName = googleAccount.displayName ?: "Google User"
            }
            else -> {
                Toast.makeText(requireContext(), "Google 계정으로 로그인해주세요", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (email != null) {
            showGoogleEmailChatDialog(email, displayName ?: "사용자")
        } else {
            Toast.makeText(requireContext(), "이메일 정보를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGoogleEmailChatDialog(myEmail: String, myName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_google_email_chat, null)
        val inputEmail = dialogView.findViewById<android.widget.EditText>(R.id.editTextInviteEmail)
        val inputChatName = dialogView.findViewById<android.widget.EditText>(R.id.editTextChatName)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Google 이메일로 채팅 만들기")
            .setView(dialogView)
            .setPositiveButton("만들기") { _, _ ->
                val inviteEmail = inputEmail.text.toString().trim().lowercase()
                val chatRoomName = inputChatName.text.toString().trim().ifEmpty {
                    if (inviteEmail.isNotEmpty()) inviteEmail else "새 채팅"
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    // Create chat room with both email-based identification for invite lookup
                    // The ViewModel will handle creating the room with proper member IDs
                    val chatRoomId = viewModel.createEmailBasedChatRoom(
                        myEmail = myEmail,
                        inviteEmail = inviteEmail.takeIf { it.isNotEmpty() },
                        chatRoomName = chatRoomName
                    )

                    val bundle = Bundle().apply {
                        putString("chatRoomId", chatRoomId)
                    }
                    findNavController().navigate(R.id.action_chatRoomList_to_chat, bundle)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun checkContactsPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openPhoneContacts()
        } else {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openPhoneContacts() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun handleSelectedContact(contactUri: android.net.Uri) {
        val cursor = requireContext().contentResolver.query(
            contactUri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                val phoneNumber = if (numberIndex >= 0) it.getString(numberIndex) else ""

                // Create chat room with this contact
                createChatWithPhoneContact(name, phoneNumber)
            }
        }
    }

    private fun createChatWithPhoneContact(contactName: String, phoneNumber: String) {
        val chatRoomName = "$contactName 채팅"

        viewLifecycleOwner.lifecycleScope.launch {
            // Use phone number as friendId for phone-based contacts
            val chatRoomId = viewModel.createFriendChatRoomAndGetId(
                friendIds = listOf(phoneNumber),
                chatRoomName = chatRoomName
            )

            // Store the phone number association if needed
            // For now, just navigate to the chat
            val bundle = Bundle().apply {
                putString("chatRoomId", chatRoomId)
            }
            findNavController().navigate(R.id.action_chatRoomList_to_chat, bundle)

            Toast.makeText(requireContext(), "$contactName 님과의 채팅방이 생성되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeChatRooms() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatRooms.collect { rooms ->
                chatRoomAdapter.submitList(rooms)
                binding.textViewEmpty.visibility =
                    if (rooms.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
