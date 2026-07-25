package com.example.intertrack.fragments

data class Instructor(
    val id: String,
    val initials: String,
    val name: String,
    val department: String,
    val university: String,
    val email: String,
    val office: String,
    val bio: String,
    val accentColor: String = "#0569bf"
)

// Dummy data — TODO: replace with API response from GET /api/instructors
val dummyInstructors = listOf(
    Instructor(
        id = "sarah-smith",
        initials = "SS",
        name = "Dr. Sarah Smith",
        department = "Computer Engineering",
        university = "Istanbul Technical University",
        email = "s.smith@itu.edu.tr",
        office = "EEB 3rd Floor, Room 312",
        bio = "Associate Professor specializing in software engineering, distributed systems, and AI-driven applications. Supervised over 60 internship students.",
        accentColor = "#0569bf"
    ),
    Instructor(
        id = "james-wilson",
        initials = "JW",
        name = "Prof. James Wilson",
        department = "Computer Science",
        university = "Bogazici University",
        email = "j.wilson@boun.edu.tr",
        office = "Hisar Campus, Block A, Room 204",
        bio = "Professor of Computer Science with research focus on machine learning and data engineering. Industry collaboration with leading tech companies.",
        accentColor = "#16A34A"
    ),
    Instructor(
        id = "elif-demir",
        initials = "ED",
        name = "Dr. Elif Demir",
        department = "Software Engineering",
        university = "METU",
        email = "elif.demir@metu.edu.tr",
        office = "Informatics Institute, Room 118",
        bio = "Researcher and lecturer in agile software development, mobile computing, and human-computer interaction.",
        accentColor = "#7C3AED"
    ),
    Instructor(
        id = "ali-kaya",
        initials = "AK",
        name = "Assoc. Prof. Ali Kaya",
        department = "Electrical & Electronics Engineering",
        university = "Bilkent University",
        email = "a.kaya@bilkent.edu.tr",
        office = "EE Building, Room 305",
        bio = "Specializes in embedded systems, IoT, and digital signal processing. Supervises interdisciplinary internship projects.",
        accentColor = "#F97316"
    ),
    Instructor(
        id = "nina-torres",
        initials = "NT",
        name = "Dr. Nina Torres",
        department = "Management Information Systems",
        university = "Istanbul University",
        email = "n.torres@istanbul.edu.tr",
        office = "Faculty of Business, Room 211",
        bio = "Expert in enterprise information systems, digital transformation, and business analytics.",
        accentColor = "#0EA5E9"
    )
)
