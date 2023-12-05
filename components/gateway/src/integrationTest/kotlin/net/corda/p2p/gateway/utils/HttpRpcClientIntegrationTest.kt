package net.corda.p2p.gateway.utils

import com.sun.net.httpserver.HttpServer
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.GatewayTlsCertificates
import net.corda.data.p2p.GatewayTruststore
import net.corda.p2p.gateway.TestBase
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer

internal class HttpRpcClientIntegrationTest : TestBase() {
    @Test
    fun `verify that RPC HTTP client works`() {
        val port = getOpenPort()
        val avroSchemaRegistry = AvroSchemaRegistryImpl()
        val requestMessage = GatewayTlsCertificates(
            "tenantId",
            HoldingIdentity("X500-name", "Group ID"),
            listOf("pems"),
        )
        val replyMessage = GatewayTruststore(
            HoldingIdentity("X500-name", "Group ID"),
            listOf("more"),
        )
        val path = "/test"
        val uri = URI.create("http://www.alice.net:$port$path")
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext(path) { exchange ->
            exchange.use {
                val content = exchange.requestBody.readAllBytes()

                val request = avroSchemaRegistry.deserialize(
                    ByteBuffer.wrap(content),
                    GatewayTlsCertificates::class.java,
                    null,
                )
                assertThat(request).isEqualTo(requestMessage)
                val response = avroSchemaRegistry.serialize(replyMessage).array()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.write(response)
            }
        }
        try {
            server.start()

            val client = HttpRpcClient(avroSchemaRegistry)
            val reply: GatewayTruststore? = client.send(uri, requestMessage)
            assertThat(reply).isEqualTo(replyMessage)
        } finally {
            server.stop(0)
        }
    }
}
