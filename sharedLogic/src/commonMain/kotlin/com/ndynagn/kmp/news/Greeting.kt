package com.ndynagn.kmp.news

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = sayHello(platform.name)
}
