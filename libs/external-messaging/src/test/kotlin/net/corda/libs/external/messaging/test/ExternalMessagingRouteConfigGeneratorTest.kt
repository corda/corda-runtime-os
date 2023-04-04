package net.corda.libs.external.messaging.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import java.util.UUID
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.external.messaging.ExternalMessagingConfigProviderImpl
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGeneratorImpl
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.serialization.ExternalMessagingChannelConfigSerializerImpl
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializerImpl
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.schema.configuration.ExternalMessagingConfig
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExternalMessagingRouteConfigGeneratorTest {

    private companion object {
        val ALICE_HOLDING_ID1 = net.corda.virtualnode.HoldingIdentity(
            MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
            "GROUP_ID1"
        )

        val cpiId = CpiIdentifier(
            "myCpi",
            "1.0",
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "signerSummaryHash".toByteArray())
        )

        fun genExternalMsgConfig(
            receiveTopicPattern: String,
            isActive: Boolean,
            inactiveResponseType: InactiveResponseType
        ): SmartConfig {
            val externalMsgConfig = ConfigFactory.empty()
                .withValue(
                    ExternalMessagingConfig.EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN,
                    ConfigValueFactory.fromAnyRef(receiveTopicPattern)
                )
                .withValue(ExternalMessagingConfig.EXTERNAL_MESSAGING_ACTIVE, ConfigValueFactory.fromAnyRef(isActive))
                .withValue(
                    ExternalMessagingConfig.EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE,
                    ConfigValueFactory.fromAnyRef(inactiveResponseType.toString())
                )
            return SmartConfigFactory.createWithoutSecurityServices().create(externalMsgConfig)
        }

        fun genCpk(externalChannelsConfigJson: String): CpkMetadata {
            return CpkMetadata(
                cpkId = CpkIdentifier(
                    UUID.randomUUID().toString(),
                    "1.0",
                    cpiId.signerSummaryHash
                ),
                manifest = CpkManifest(CpkFormatVersion(1, 0)),
                mainBundle = "main-bundle",
                libraries = emptyList(),
                cordappManifest = CordappManifest(
                    "net.cordapp.Bundle",
                    "1.2.3",
                    12,
                    34,
                    CordappType.WORKFLOW,
                    "someName",
                    "R3",
                    42,
                    "some license",
                    mapOf(
                        "Corda-Contract-Classes" to "contractClass1, contractClass2",
                        "Corda-Flow-Classes" to "flowClass1, flowClass2"
                    ),
                ),
                type = CpkType.UNKNOWN,
                fileChecksum = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32)),
                cordappCertificates = emptySet(),
                timestamp = Instant.now(),
                externalChannelsConfig = externalChannelsConfigJson
            )
        }
    }

    @Test
    fun `ensure the route configuration is generated correctly`() {

        val smartConfig =
            genExternalMsgConfig("ext.\$HOLDING_ID\$.\$CHANNEL_NAME\$.receive", false, InactiveResponseType.IGNORE)

        val externalMessagingRouteConfigGenerator =
            ExternalMessagingRouteConfigGeneratorImpl(
                ExternalMessagingConfigProviderImpl(smartConfig),
                ExternalMessagingRouteConfigSerializerImpl(),
                ExternalMessagingChannelConfigSerializerImpl()
            )

        val externalChannelsConfigJson = """
                        {
                            "channels": [
                                {
                                    "name": "a.b.c",
                                    "type": "SEND"
                                },
                                {
                                    "name": "1.2.3",
                                    "type": "SEND-RECEIVE"
                                }
                            ]
                        }
                    """.trimMargin()

        val externalChannelsConfigJson2 = """
                        {
                            "channels": [
                                {
                                    "name": "c.b.a",
                                    "type": "SEND"
                                },
                                {
                                    "name": "3.2.1",
                                    "type": "SEND-RECEIVE"
                                }
                            ]
                        }
                    """.trimMargin()

        val expectedRouteConfig =
            """
                {
                    "currentRoutes": {
                        "cpiIdentifier": {
                            "name": "myCpi",
                            "version": "1.0",
                            "signerSummaryHash": "SHA-256:7369676E657253756D6D61727948617368"
                        },
                        "routes": [
                            {
                                "channelName": "a.b.c",
                                "externalReceiveTopicName": "ext.7EA3CA6EF888.a.b.c.receive",
                                "active": false,
                                "inactiveResponseType": "IGNORE"
                            },
                            {
                                "channelName": "1.2.3",
                                "externalReceiveTopicName": "ext.7EA3CA6EF888.1.2.3.receive",
                                "active": false,
                                "inactiveResponseType": "IGNORE"
                            },
                            {
                                "channelName": "c.b.a",
                                "externalReceiveTopicName": "ext.7EA3CA6EF888.c.b.a.receive",
                                "active": false,
                                "inactiveResponseType": "IGNORE"
                            },
                            {
                                "channelName": "3.2.1",
                                "externalReceiveTopicName": "ext.7EA3CA6EF888.3.2.1.receive",
                                "active": false,
                                "inactiveResponseType": "IGNORE"
                            }
                        ]
                    },
                    "previousVersionRoutes": []
                }
            """.trimIndent()

        val routesConfig = externalMessagingRouteConfigGenerator.generateConfig(
            ALICE_HOLDING_ID1,
            cpiId,
            cpks = setOf(genCpk(externalChannelsConfigJson), genCpk(externalChannelsConfigJson2))
        )

        val mapper = ObjectMapper()
        assertThat(mapper.readTree(routesConfig)).isEqualTo(mapper.readTree(expectedRouteConfig))
    }
}
