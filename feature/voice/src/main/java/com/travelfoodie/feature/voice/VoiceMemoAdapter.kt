package com.travelfoodie.feature.voice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.travelfoodie.core.data.local.entity.VoiceMemoEntity
import com.travelfoodie.feature.voice.databinding.ItemVoiceMemoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceMemoAdapter(
    private val onPlayClick: (VoiceMemoEntity) -> Unit,
    private val onDeleteClick: (VoiceMemoEntity) -> Unit
) : ListAdapter<VoiceMemoEntity, VoiceMemoAdapter.VoiceMemoViewHolder>(VoiceMemoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceMemoViewHolder {
        val binding = ItemVoiceMemoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VoiceMemoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoiceMemoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VoiceMemoViewHolder(
        private val binding: ItemVoiceMemoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(memo: VoiceMemoEntity) {
            binding.apply {
                textViewTitle.text = memo.title
                textViewContent.text = memo.transcribedText
                textViewDate.text = formatDate(memo.createdAt)

                buttonPlay.setOnClickListener {
                    onPlayClick(memo)
                }

                buttonDelete.setOnClickListener {
                    onDeleteClick(memo)
                }
            }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
            return sdf.format(Date(timestamp))
        }
    }

    class VoiceMemoDiffCallback : DiffUtil.ItemCallback<VoiceMemoEntity>() {
        override fun areItemsTheSame(oldItem: VoiceMemoEntity, newItem: VoiceMemoEntity): Boolean {
            return oldItem.memoId == newItem.memoId
        }

        override fun areContentsTheSame(oldItem: VoiceMemoEntity, newItem: VoiceMemoEntity): Boolean {
            return oldItem == newItem
        }
    }
}
