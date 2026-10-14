package com.ndynagn.kmp.news.verification

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.scoped
import org.koin.plugin.module.dsl.single

@Serializable
private data class ProbeResponseDto(val value: Int)

internal class ProbeDependency

internal class ProbeConsumer(val dependency: ProbeDependency)

/** Opt-in neutral admission probe; never included in an ordinary application/framework. */
class CompatibilityProbe internal constructor(private val openDatabase: (String) -> ProbeDatabase) {
    /** Uses only a disposable database and local fixture server; throws when an admission assertion fails. */
    suspend fun verify(databasePath: String, serverUrl: String): String {
        verifyKoin()
        verifyRoom(databasePath)
        verifyMockNetwork()
        val engine = verifyPlatformNetwork(serverUrl)

        return "PASS: Koin, Room persistence/Flow/reopen, Ktor mock and $engine"
    }

    private fun verifyKoin() {
        val application = koinApplication {
            modules(
                module {
                    single<ProbeDependency>()
                    scope(named("probe")) {
                        scoped<ProbeConsumer>()
                    }
                },
            )
        }
        try {
            val scope = application.koin.createScope("probe-scope", named("probe"))
            check(scope.get<ProbeConsumer>().dependency === application.koin.get<ProbeDependency>())

            scope.close()
            check(application.koin.getScopeOrNull("probe-scope") == null)
        } finally {
            application.close()
        }
    }

    private suspend fun verifyRoom(path: String) = coroutineScope {
        val database = openDatabase(path)
        try {
            val observed = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(10_000) { database.entries().observe().first { it.any { entry -> entry.value == 42 } } }
            }

            database.entries().insert(ProbeEntity(1, 42))

            check(observed.await().single() == ProbeEntity(1, 42))
            check(database.entries().read().single().value == 42)
        } finally {
            database.close()
        }

        val reopened = openDatabase(path)
        try {
            check(reopened.entries().read().single() == ProbeEntity(1, 42))
        } finally {
            reopened.close()
        }
    }

    private suspend fun verifyMockNetwork() {
        val waitingBody = ByteChannel(autoFlush = true)
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/failure" -> error("Controlled network failure")
                "/wait" -> respond(waitingBody)
                "/http" -> respond("failure", HttpStatusCode.InternalServerError)
                "/malformed" -> respond("{broken", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("{\"value\":42}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        try {
            check(client.get("https://fixture/json").body<ProbeResponseDto>().value == 42)
            expectFailure { client.get("https://fixture/malformed").body<ProbeResponseDto>() }
            expectFailure { client.get("https://fixture/failure") }
            try {
                client.get("https://fixture/http")
                error("Expected HTTP failure")
            } catch (exception: ResponseException) {
                check(exception.response.status == HttpStatusCode.InternalServerError)
            }
            verifyCancellation(client, "https://fixture/wait")
        } finally {
            waitingBody.cancel(null)
            client.close()
            client.coroutineContext[Job]?.join()
            engine.close()
        }
    }

    private suspend fun verifyPlatformNetwork(serverUrl: String): String {
        val client = HttpClient {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val engineName = client.engine::class.simpleName
        check(engineName == "OkHttpEngine" || engineName == "DarwinClientEngine") { "Unexpected engine: $engineName" }
        try {
            check(client.get("$serverUrl/json").body<ProbeResponseDto>().value == 42)
            expectFailure { client.get("$serverUrl/malformed").body<ProbeResponseDto>() }
            expectFailure { client.get("$serverUrl/http") }
            expectFailure { client.get("http://127.0.0.1:1/unavailable") }
            verifyCancellation(client, "$serverUrl/wait")

            return engineName
        } finally {
            client.close()
            client.coroutineContext[Job]?.join()
        }
    }

    private suspend fun verifyCancellation(client: HttpClient, url: String) = coroutineScope {
        val started = CompletableDeferred<Unit>()
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            client.prepareGet(url).execute { response ->
                check(response.status == HttpStatusCode.OK)
                started.complete(Unit)
                response.bodyAsChannel().readRemaining()
            }
        }

        withTimeout(10_000) { started.await() }
        withTimeout(10_000) { request.cancelAndJoin() }

        check(request.isCancelled)
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        var failed = false
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            failed = true
        }
        check(failed) { "Expected a controlled failure" }
    }
}
