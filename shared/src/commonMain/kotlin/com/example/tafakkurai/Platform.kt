package com.example.tafakkurai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform