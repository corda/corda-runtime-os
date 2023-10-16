package net.corda.messaging.mediator.factory

import java.net.http.HttpClient
import java.time.Duration

object HttpClientFactory {
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    fun getClient(): HttpClient = httpClient
}
