package com.uad.uadlearningapp

data class Task(
    var id: String? = null,
    var title: String? = null,
    var description: String? = null,
    var deadline: String? = null,
    var subject: String? = null,
    var isCompleted: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var fileUrl: String? = "",
    var fileName: String? = ""
)