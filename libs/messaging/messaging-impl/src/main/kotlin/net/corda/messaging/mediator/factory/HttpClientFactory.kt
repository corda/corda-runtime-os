package net.corda.messaging.mediator.factory

import java.net.http.HttpClient

object HttpClientFactory {
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().build()
    }

    fun getClient(): HttpClient = httpClient
}
