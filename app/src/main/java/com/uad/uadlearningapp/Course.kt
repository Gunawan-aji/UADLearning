package com.uad.uadlearningapp

data class Course(
    var id: String? = null,
    val courseName: String = "",
    val lecturer: String = "",
    val schedule: String = "",
    val bannerColor: String = "#1A73E8",
    val creatorId: String = "",
    val classCode: String = ""
)