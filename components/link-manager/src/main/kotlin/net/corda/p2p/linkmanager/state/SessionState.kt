package net.corda.p2p.linkmanager.state

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolInitiatorDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolResponderDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.crypto.protocol.api.SerialisableSessionData
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.nio.ByteBuffer
import net.corda.data.p2p.state.SessionState as AvroSessionData

internal data class SessionState(
    val message: LinkOutMessage?,
    val sessionData: SerialisableSessionData,
) {
    companion object {
        fun AvroSessionData.toCorda(
            avroSchemaRegistry: AvroSchemaRegistry,
            encryptionClient: SessionEncryptionOpsClient,
            checkRevocation: CheckRevocation,
        ): SessionState {
            val rawData = ByteBuffer.wrap(
                encryptionClient.decryptSessionData(this.encryptedSessionData.array()),
            )
            val sessionData = when (val type = avroSchemaRegistry.getClassType(rawData)) {
                AuthenticationProtocolInitiatorDetails::class.java -> {
                    avroSchemaRegistry.deserialize(
                        rawData,
                        AuthenticationProtocolInitiatorDetails::class.java,
                        null,
                    ).toCorda(checkRevocation)
                }
                AuthenticationProtocolResponderDetails::class.java -> {
                    avroSchemaRegistry.deserialize(
                        rawData,
                        AuthenticationProtocolResponderDetails::class.java,
                        null,
                    ).toCorda()
                }
                Session::class.java -> {
                    avroSchemaRegistry.deserialize(
                        rawData,
                        Session::class.java,
                        null,
                    ).toCorda()
                }
                else -> throw CordaRuntimeException("Unexpected type: $type")
            }
            return SessionState(
                message = this.message,
                sessionData = sessionData,
            )
        }
    }

    fun toAvro(
        avroSchemaRegistry: AvroSchemaRegistry,
        encryptionClient: SessionEncryptionOpsClient,
    ): AvroSessionData {
        val sessionAvroData = sessionData.toAvro()
        val rawData = avroSchemaRegistry.serialize(sessionAvroData)
        val encryptedData = encryptionClient.encryptSessionData(rawData.array())
        return AvroSessionData(
            message,
            ByteBuffer.wrap(encryptedData),
        )
    }
}
