package com.example.optical_designer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
