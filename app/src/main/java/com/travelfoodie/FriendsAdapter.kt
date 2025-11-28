package com.travelfoodie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.travelfoodie.core.data.local.entity.FriendEntity

class FriendsAdapter : ListAdapter<FriendEntity, FriendsAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.text_friend_name)
        private val contactTextView: TextView = itemView.findViewById(R.id.text_friend_contact)
        private val typeTextView: TextView = itemView.findViewById(R.id.text_contact_type)

        fun bind(friend: FriendEntity) {
            nameTextView.text = friend.name
            contactTextView.text = friend.contactValue
            typeTextView.text = when (friend.contactType) {
                "phone" -> "Phone"
                "kakao" -> "KakaoTalk"
                "invite_code" -> "Invite Code"
                else -> friend.contactType
            }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<FriendEntity>() {
        override fun areItemsTheSame(oldItem: FriendEntity, newItem: FriendEntity): Boolean {
            return oldItem.friendId == newItem.friendId
        }

        override fun areContentsTheSame(oldItem: FriendEntity, newItem: FriendEntity): Boolean {
            return oldItem == newItem
        }
    }
}
