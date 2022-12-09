package net.corda.applications.flowworker.setup.tasks

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import java.security.MessageDigest
import java.time.Instant
import java.util.*

fun String.toSecureHashString(): String {
    val algorithm = DigestAlgorithmName.SHA2_256.name
    return SecureHash(
        algorithm = algorithm,
        bytes = MessageDigest.getInstance(algorithm).digest(this.toByteArray())
    ).toString()
}

fun UUID.toStateRef():StateRef{
    val algorithm = DigestAlgorithmName.SHA2_256.name
    val txId = SecureHash(
        algorithm = algorithm,
        bytes = MessageDigest.getInstance(algorithm).digest(this.toString().toByteArray())
    )

    return StateRef(txId,1)
}



@Suppress("LongParameterList", "Unused")
fun getStartRPCEventRecord(
    requestId: String,
    flowName: String,
    x500Name: String,
    groupId: String,
    jsonArgs: String
): Record<*, *> {
    val identity = HoldingIdentity(x500Name, groupId)

    val context = FlowStartContext(
        FlowKey(requestId, identity),
        FlowInitiatorType.RPC,
        requestId,
        identity,
        "test-cordapp",
        identity,
        flowName,
        jsonArgs,
        emptyKeyValuePairList(),
        Instant.now()
    )

    val rpcStartFlow = StartFlow(context, jsonArgs)
    return Record(
        Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
        context.statusKey.toString(),
        FlowMapperEvent(rpcStartFlow)
    )
}
