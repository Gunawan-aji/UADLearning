package com.uad.uadlearningapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubmissionAdapter(
    private val submissions: List<Map<String, Any>>,
    private val onClick: (Map<String, Any>) -> Unit
) : RecyclerView.Adapter<SubmissionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvAnswer: TextView = view.findViewById(R.id.tvStudentAnswer)
        val tvGrade: TextView = view.findViewById(R.id.tvStudentGrade)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_submission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = submissions[position]
        val name = item["userName"]?.toString() ?: "Mahasiswa"
        val answer = item["answer"]?.toString() ?: "Tidak ada jawaban"
        val grade = item["grade"]?.toString()

        holder.tvName.text = name
        holder.tvAnswer.text = answer
        holder.tvGrade.text = if (grade == "null" || grade == null) "Belum Dinilai" else "Nilai: $grade"

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = submissions.size
}