package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.gateway.Gateway.Companion.CONSUMER_GROUP_ID
import net.corda.p2p.gateway.Gateway.Companion.P2P_OUT_TOPIC
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

    private val topicService = TopicServiceStub()
    private val subscriptionFactory = SubscriptionFactoryStub(topicService)
    private val publisherFactory = PublisherFactoryStub(topicService)

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
                        client.send("localhost:10001;localhost:10000;PING".toByteArray())
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

    @Test
    fun `gateway to gateway`() {

    }

    @Test
    fun `test stubs`() {
        val pubConfig = PublisherConfig("pub")
        val publisher = PublisherFactoryStub(topicService).createPublisher(pubConfig, ConfigFactory.empty())
        val records = (1..10).map { Record(P2P_OUT_TOPIC, "$it", "Message $it") }
        publisher.publish(records)

        topicService.getRecords(P2P_OUT_TOPIC, CONSUMER_GROUP_ID, -1, false).forEach {
            println(it.record)
        }
    }

}