package com.example.intertrack.models

data class Company(
    val id: Int,
    val name: String,
    val city: String,
    val industry: String,
    val description: String,
    val hasOpenInternship: Boolean = true,
    val size: String = "51–200 employees",
    val about: String = ""
)
