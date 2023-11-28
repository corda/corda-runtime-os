package net.corda.libs.virtualnode.datamodel.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializerImpl
import net.corda.libs.packaging.core.CpiIdentifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExternalMessagingRouteConfigSerializerImplTest {

    @Suppress("LongMethod")
    @Test
    fun `serialise configuration to json`() {
        val signerSummaryHash =
            parseSecureHash("SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D")
        val cpiIdentifierV2 = CpiIdentifier("my app", "2.0", signerSummaryHash)
        val cpiIdentifierV3 = CpiIdentifier("my app", "3.0", signerSummaryHash)

        val routeChannel1 = Route(
            "channel_1",
            "ext.32CE686107E4.channel_1.receive",
            true,
            InactiveResponseType.ERROR
        )

        val routeChannel2 = Route(
            "channel_2",
            "ext.32CE686107E4.channel_2.receive",
            false,
            InactiveResponseType.IGNORE
        )

        val externalMessagingRoutes = Routes(
            cpiIdentifierV3,
            listOf(routeChannel1, routeChannel2)
        )

        val previousRoutes = Routes(
            cpiIdentifierV2,
            listOf(routeChannel1)
        )

        val config = RouteConfiguration(
            externalMessagingRoutes,
            listOf(previousRoutes)
        )

        val configJson = ExternalMessagingRouteConfigSerializerImpl().serialize(config)

        val expectedJson = """
            {
                "currentRoutes": {
                    "cpiIdentifier": {
                        "name": "my app",
                        "version": "3.0",
                        "signerSummaryHash": "SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D"
                    },
                    "routes": [
                        {
                            "channelName": "channel_1",
                            "externalReceiveTopicName": "ext.32CE686107E4.channel_1.receive",
                            "active": true,
                            "inactiveResponseType": "ERROR"
                        },
                        {
                            "channelName": "channel_2",
                            "externalReceiveTopicName": "ext.32CE686107E4.channel_2.receive",
                            "active": false,
                            "inactiveResponseType": "IGNORE"
                        }
                    ]
                },
                "previousVersionRoutes": [
                    {
                        "cpiIdentifier": {
                            "name": "my app",
                            "version": "2.0",
                            "signerSummaryHash": "SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D"
                        },
                        "routes": [
                            {
                                "channelName": "channel_1",
                                "externalReceiveTopicName": "ext.32CE686107E4.channel_1.receive",
                                "active": true,
                                "inactiveResponseType": "ERROR"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val mapper = ObjectMapper()
        assertThat(mapper.readTree(configJson)).isEqualTo(mapper.readTree(expectedJson))
    }

    @Suppress("LongMethod")
    @Test
    fun `deserialize configuration from json`() {
        val configJson = """
            {
                "currentRoutes": {
                    "cpiIdentifier": {
                        "name": "my app",
                        "version": "3.0",
                        "signerSummaryHash": "SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D"
                    },
                    "routes": [
                        {
                            "channelName": "channel_1",
                            "externalReceiveTopicName": "ext.32CE686107E4.channel_1.receive",
                            "active": true,
                            "inactiveResponseType": "ERROR"
                        },
                        {
                            "channelName": "channel_2",
                            "externalReceiveTopicName": "ext.32CE686107E4.channel_2.receive",
                            "active": false,
                            "inactiveResponseType": "IGNORE"
                        }
                    ]
                },
                "previousVersionRoutes": [
                    {
                        "cpiIdentifier": {
                            "name": "my app",
                            "version": "2.0",
                            "signerSummaryHash": "SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D"
                        },
                        "routes": [
                            {
                                "channelName": "channel_1",
                                "externalReceiveTopicName": "ext.32CE686107E4.channel_1.receive",
                                "active": true,
                                "inactiveResponseType": "ERROR"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val config = ExternalMessagingRouteConfigSerializerImpl().deserialize(configJson)

        val expectedSignerSummaryHash =
            parseSecureHash("SHA-256:60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D")
        val expectedCpiIdentifierV2 = CpiIdentifier("my app", "2.0", expectedSignerSummaryHash)
        val expectedCpiIdentifierV3 = CpiIdentifier("my app", "3.0", expectedSignerSummaryHash)

        val expectedRouteChannel1 = Route(
            "channel_1",
            "ext.32CE686107E4.channel_1.receive",
            true,
            InactiveResponseType.ERROR
        )

        val expectedRouteChannel2 = Route(
            "channel_2",
            "ext.32CE686107E4.channel_2.receive",
            false,
            InactiveResponseType.IGNORE
        )

        val expectedCurrentRoutes = Routes(
            expectedCpiIdentifierV3,
            listOf(expectedRouteChannel1, expectedRouteChannel2)
        )

        val expectedPreviousRoutes = Routes(
            expectedCpiIdentifierV2,
            listOf(expectedRouteChannel1)
        )

        val expectedConfig = RouteConfiguration(
            expectedCurrentRoutes,
            listOf(expectedPreviousRoutes)
        )

        assertThat(config).isEqualTo(expectedConfig)
    }
}
