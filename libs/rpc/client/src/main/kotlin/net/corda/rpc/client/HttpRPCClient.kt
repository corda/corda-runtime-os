package net.corda.rpc.client

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

internal class HttpRPCClient {

    private val httpClient = HttpClient.newHttpClient()

    fun sendRequest(
        endpoint: String,
        service: String,
        port: Int,
        version: Int,
        requestPayload: ByteArray
    ): CompletableFuture<ByteArray> {

        val uri = "http://$service:$port/rpc/$version/$endpoint"

        val request = HttpRequest.newBuilder()
            .uri(URI(uri))
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestPayload))
            .build()

        return CompletableFuture.supplyAsync {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            response.body()
        }
    }

}