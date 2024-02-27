package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.statemanager.api.State
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.linkmanager.state.SessionState.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.crypto.exceptions.CryptoException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import net.corda.data.p2p.state.SessionState as AvroSessionState

internal class StateConvertor(
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionEncryptionOpsClient: SessionEncryptionOpsClient,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(StateConvertor::class.java)
    }

    fun toCordaSessionState(
        state: State,
        checkRevocation: CheckRevocation,
    ): SessionState? {
        return try {
            schemaRegistry.deserialize<AvroSessionState>(ByteBuffer.wrap(state.value))
                .toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    checkRevocation,
                )
        } catch (e: CryptoException) {
            logger.warn("Could not retrieve ${SessionState::class.simpleName} for session '${state.key}'. Cause: ${e.message}")
            null
        }
    }

    fun toStateByteArray(
        state: SessionState,
    ): ByteArray {
        return schemaRegistry.serialize(
            state
                .toAvro(schemaRegistry, sessionEncryptionOpsClient),
        ).array()
    }
}
