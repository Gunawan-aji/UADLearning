package com.uad.uadlearningapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

class CourseAdapter(
    private var courseList: List<Course>,
    private val onDeleteClick: (String) -> Unit,
    private val onItemClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCourseName)
        val tvLecturer: TextView = view.findViewById(R.id.tvLecturer)
        val tvSchedule: TextView = view.findViewById(R.id.tvSchedule)
        val layoutHeader: View = view.findViewById(R.id.layoutCourseHeader)
        val btnMenu: ImageButton = view.findViewById(R.id.btnMenuCourse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courseList[position]
        holder.tvName.text = course.courseName
        holder.tvLecturer.text = course.lecturer
        holder.tvSchedule.text = course.schedule
        holder.layoutHeader.setBackgroundColor(Color.parseColor(course.bannerColor))

        // Klik seluruh kartu untuk masuk detail
        holder.itemView.setOnClickListener {
            onItemClick(course)
        }

        // Klik menu titik tiga untuk hapus
        holder.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add("Keluar dan hapus kelas")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "Keluar dan hapus kelas") {
                    course.id?.let { id -> onDeleteClick(id) }
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = courseList.size

    fun updateData(newList: List<Course>) {
        this.courseList = newList
        notifyDataSetChanged()
    }
}