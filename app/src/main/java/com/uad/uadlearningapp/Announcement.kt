package com.uad.uadlearningapp

data class Announcement(
    var id: String? = null,
    val authorName: String = "",
    val date: String = "",
    val content: String = "",
    val authorInitial: String = "D"
)