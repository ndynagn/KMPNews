package com.ndynagn.kmp.news.feature.search

import com.ndynagn.kmp.news.data.news.configureNewsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class LocalNewsEngineTest {
    @Test
    fun mediatorUsesRealOkHttpForJsonFailuresCancellationAndClose() = runBlocking {
        val root = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .firstOrNull { Files.exists(it.resolve("project.yaml")) }
            ?: error("The Toolchain checkout root must be discoverable")
        val script = root.resolve("scripts/local-news-server.py")
        val server = ProcessBuilder("python3", script.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start()

        try {
            val port = checkNotNull(server.inputStream.bufferedReader().readLine()) {
                "The local server exited before announcing its port"
            }.toInt()
            val client = HttpClient(OkHttp) { configureNewsHttpClient(localNewsConfiguration) }

            verifyLocalNewsEngine(client, port)
        } finally {
            server.destroy()
            server.waitFor()
        }
    }
}
