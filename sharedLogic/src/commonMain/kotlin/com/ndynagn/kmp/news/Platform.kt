package com.ndynagn.kmp.news

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform