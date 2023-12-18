package net.corda.p2p.linkmanager.state

import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolInitiatorDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolResponderDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.SessionData
import net.corda.p2p.linkmanager.stubs.Encryption
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.avro.specific.SpecificRecordBase
import java.nio.ByteBuffer
import net.corda.data.p2p.state.SessionState as AvroSessionData

internal data class SessionState(
    val message: LinkOutMessage,
    val sessionData: SessionData,
) {
    companion object {
        fun AvroSessionData.toCorda(
            avroSchemaRegistry: AvroSchemaRegistry,
            encryption: Encryption,
            checkRevocation: CheckRevocation,
        ): SessionState {
            val rawData = encryption.decrypt(this.encryptedSessionData.array())
            val avroSessionData = avroSchemaRegistry.deserialize(
                ByteBuffer.wrap(rawData),
                SpecificRecordBase::class.java,
                null,
            )
            val sessionData = when (avroSessionData) {
                is AuthenticationProtocolInitiatorDetails ->
                    avroSessionData.toCorda(checkRevocation)
                is AuthenticationProtocolResponderDetails ->
                    avroSessionData.toCorda()
                is Session -> avroSessionData.toCorda().let {
                    (it as? SessionData) ?: throw CordaRuntimeException("Unexpected type: ${it.javaClass}")
                }
                else -> throw CordaRuntimeException("Unexpected type: ${avroSessionData.javaClass}")
            }
            return SessionState(
                message = this.message,
                sessionData = sessionData
            )
        }
    }


    fun toAvro(
        avroSchemaRegistry: AvroSchemaRegistry,
        encryption: Encryption,
    ): AvroSessionData {
        val sessionAvroData = sessionData.toAvro()
        val rawData = avroSchemaRegistry.serialize(sessionAvroData)
        val encryptedData = encryption.encrypt(rawData.array())
        return AvroSessionData(
            message,
            ByteBuffer.wrap(encryptedData)
        )
    }
}