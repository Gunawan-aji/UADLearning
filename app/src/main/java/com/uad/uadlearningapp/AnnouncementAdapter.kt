package com.uad.uadlearningapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnnouncementAdapter(
    private var announcementList: List<Announcement>,
    // TAMBAHKAN: Parameter lambda untuk menangani klik
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {

    class AnnouncementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tvInitial)
        val tvAuthorName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return AnnouncementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        val announcement = announcementList[position]
        holder.tvInitial.text = announcement.authorInitial
        holder.tvAuthorName.text = announcement.authorName
        holder.tvDate.text = announcement.date
        holder.tvContent.text = announcement.content

        // TAMBAHKAN: Logika klik pada seluruh item pengumuman
        holder.itemView.setOnClickListener {
            onItemClick(announcement)
        }
    }

    override fun getItemCount(): Int = announcementList.size

    fun updateData(newList: List<Announcement>) {
        this.announcementList = newList
        notifyDataSetChanged()
    }
}