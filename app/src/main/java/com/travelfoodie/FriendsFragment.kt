package com.travelfoodie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FriendsFragment : Fragment() {

    private val viewModel: FriendsViewModel by viewModels()
    private lateinit var friendsAdapter: FriendsAdapter

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPhoneContactsDialog()
        } else {
            Snackbar.make(requireView(), "Contacts permission denied", Snackbar.LENGTH_SHORT).show()
        }
    }

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // SMS permission granted
        } else {
            Snackbar.make(requireView(), "SMS permission denied", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        setupButtons(view)
        observeFriends(view)
        observeInviteCode(view)
    }

    private fun setupRecyclerView(view: View) {
        friendsAdapter = FriendsAdapter()
        view.findViewById<RecyclerView>(R.id.recyclerViewFriends).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendsAdapter
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.buttonAddByCode).setOnClickListener {
            showAddByInviteCodeDialog()
        }

        view.findViewById<Button>(R.id.buttonAddByPhone).setOnClickListener {
            checkContactsPermissionAndShow()
        }

        view.findViewById<Button>(R.id.buttonShareInvite).setOnClickListener {
            showShareInviteDialog()
        }
    }

    private fun observeFriends(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friends.collect { friends ->
                friendsAdapter.submitList(friends)
                view.findViewById<TextView>(R.id.textViewEmpty).visibility =
                    if (friends.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeInviteCode(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.inviteCode.collect { code ->
                view.findViewById<TextView>(R.id.textViewMyCode).text = "My Invite Code: $code"
            }
        }
    }

    private fun showAddByInviteCodeDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter 6-digit invite code"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Friend by Code")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val code = editText.text.toString().trim()
                if (code.length == 6) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = viewModel.addFriendByInviteCode(code)
                        if (result.isSuccess) {
                            Snackbar.make(requireView(), "Friend added successfully!", Snackbar.LENGTH_SHORT).show()
                        } else {
                            Snackbar.make(requireView(), result.exceptionOrNull()?.message ?: "Failed to add friend", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Snackbar.make(requireView(), "Invalid code format", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkContactsPermissionAndShow() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                showPhoneContactsDialog()
            }
            else -> {
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun showPhoneContactsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = viewModel.getPhoneContacts()

            val contactNames = contacts.map { "${it.name} (${it.phoneNumber})" }.toTypedArray()
            val selectedContacts = mutableListOf<Int>()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Contacts")
                .setMultiChoiceItems(contactNames, null) { _, which, isChecked ->
                    if (isChecked) {
                        selectedContacts.add(which)
                    } else {
                        selectedContacts.remove(which)
                    }
                }
                .setPositiveButton("Add as Friends") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        selectedContacts.forEach { index ->
                            viewModel.addFriendFromContact(contacts[index])
                        }
                        Snackbar.make(requireView(), "${selectedContacts.size} friends added", Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("Send SMS Invite") { _, _ ->
                    selectedContacts.forEach { index ->
                        sendSMSInvite(contacts[index].phoneNumber)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun sendSMSInvite(phoneNumber: String) {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                try {
                    val smsManager = SmsManager.getDefault()
                    val message = viewModel.getSMSInviteMessage()
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    Snackbar.make(requireView(), "Invite sent to $phoneNumber", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(requireView(), "Failed to send SMS", Snackbar.LENGTH_SHORT).show()
                }
            }
            else -> {
                requestSmsPermission.launch(Manifest.permission.SEND_SMS)
            }
        }
    }

    private fun showShareInviteDialog() {
        val inviteCode = viewModel.inviteCode.value
        val options = arrayOf("Share via KakaoTalk", "Copy Code", "Share via Other Apps")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Share Invite Code")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareViaKakao(inviteCode)
                    1 -> copyInviteCode(inviteCode)
                    2 -> shareViaOtherApps(inviteCode)
                }
            }
            .show()
    }

    private fun shareViaKakao(inviteCode: String) {
        val template = FeedTemplate(
            content = Content(
                title = "Join TravelFoodie!",
                description = "Use my invite code: $inviteCode to add me as a friend",
                imageUrl = "https://via.placeholder.com/400x400",
                link = Link(
                    webUrl = "https://travelfoodie.app",
                    mobileWebUrl = "https://travelfoodie.app"
                )
            )
        )

        if (ShareClient.instance.isKakaoTalkSharingAvailable(requireContext())) {
            ShareClient.instance.shareDefault(requireContext(), template) { sharingResult, error ->
                if (error != null) {
                    Snackbar.make(requireView(), "KakaoTalk share failed", Snackbar.LENGTH_SHORT).show()
                } else if (sharingResult != null) {
                    Snackbar.make(requireView(), "Shared via KakaoTalk", Snackbar.LENGTH_SHORT).show()
                }
            }
        } else {
            Snackbar.make(requireView(), "KakaoTalk not installed", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyInviteCode(inviteCode: String) {
        val clipboard = ContextCompat.getSystemService(requireContext(), android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("Invite Code", inviteCode)
        clipboard?.setPrimaryClip(clip)
        Snackbar.make(requireView(), "Code copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }

    private fun shareViaOtherApps(inviteCode: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Join me on TravelFoodie! Use my invite code: $inviteCode")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share invite code"))
    }
}
