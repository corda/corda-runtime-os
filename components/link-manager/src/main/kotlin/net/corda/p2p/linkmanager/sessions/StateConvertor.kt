package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.statemanager.api.State
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.linkmanager.state.SessionState.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import java.nio.ByteBuffer
import net.corda.data.p2p.state.SessionState as AvroSessionState

internal class StateConvertor(
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionEncryptionOpsClient: SessionEncryptionOpsClient,
) {
    fun toCordaSessionState(
        state: State,
        checkRevocation: CheckRevocation,
    ): SessionState {
        return schemaRegistry.deserialize<AvroSessionState>(ByteBuffer.wrap(state.value))
            .toCorda(
                schemaRegistry,
                sessionEncryptionOpsClient,
                checkRevocation,
            )
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
