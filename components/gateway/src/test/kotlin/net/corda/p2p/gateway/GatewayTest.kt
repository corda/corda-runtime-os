package net.corda.p2p.gateway

import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.model.Topic
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.v5.base.util.NetworkHostAndPort
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.security.KeyStore
import java.util.concurrent.CountDownLatch

class GatewayTest {

    private val clientMessageContent = "PING"
    private val serverResponseContent = "PONG"
    private val keystorePass = "cordacadevpass"
    private val truststorePass = "trustpass"
    private val serverAddress = NetworkHostAndPort.parse("localhost:10000")
    private val sslConfiguration = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
    }

    private val topicService = TopicServiceImpl()
    private val subscriptionFactory = InMemSubscriptionFactory(topicService)
    private val publisherFactory = CordaPublisherFactory(topicService)

    @Test
    fun `http client to gateway`() {
        val gatewayAddress = NetworkHostAndPort("localhost", 10000)
        Gateway(gatewayAddress, sslConfiguration, subscriptionFactory, publisherFactory).use {
            it.start()
            HttpClient(gatewayAddress, sslConfiguration).use { client->
                client.start()
                val responseReceived = CountDownLatch(1)
                client.onConnection.subscribe { evt ->
                    if (evt.connected) {
                        client.send(clientMessageContent.toByteArray())
                    }
                }
                client.onReceive.subscribe { msg ->
                    println(msg)
                    responseReceived.countDown()
                }
                responseReceived.await()
            }
        }
    }
}