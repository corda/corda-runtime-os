package net.corda.web.client

import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import org.osgi.service.component.annotations.Component

@Component(service = [CordaHttpClient::class])
class CordaHttpClientImpl : CordaHttpClient {

    private val threadPoolSize = 10  // Modify based on requirements
    private val executor = Executors.newFixedThreadPool(threadPoolSize)
    var client: HttpClient = HttpClient.newBuilder()
        .executor(executor)
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()

    override suspend fun post(url: URL, payload: ByteArray): ByteArray {
        return coroutineScope {
            val request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build()

            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await().body()
        }
    }
}
