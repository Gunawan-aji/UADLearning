package com.uad.uadlearningapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.uad.uadlearningapp.databinding.ItemTaskBinding

class TaskAdapter(
    private var taskMap: MutableMap<String, Task>,
    private val onDeleteClick: (String) -> Unit,
    private val onToggleComplete: (String, Boolean) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var taskKeys: List<String> = taskMap.keys.toList()

    fun updateData(newMap: MutableMap<String, Task>) {
        this.taskMap = newMap
        this.taskKeys = newMap.keys.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val taskId = taskKeys[position]
        val task = taskMap[taskId]
        if (task != null) holder.bind(taskId, task)
    }

    override fun getItemCount(): Int = taskMap.size

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(taskId: String, task: Task) {
            binding.apply {
                tvTitle.text = task.title
                tvSubject.text = task.subject
                tvDeadline.text = "Deadline: ${task.deadline}"
                cbDone.setOnCheckedChangeListener(null)
                cbDone.isChecked = task.isCompleted
                cbDone.setOnCheckedChangeListener { _, isChecked -> onToggleComplete(taskId, isChecked) }
                btnDelete.setOnClickListener { onDeleteClick(taskId) }
            }
        }
    }
}