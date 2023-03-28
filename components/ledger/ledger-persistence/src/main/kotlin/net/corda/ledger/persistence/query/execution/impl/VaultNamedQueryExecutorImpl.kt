package net.corda.ledger.persistence.query.execution.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.ledger.persistence.common.ComponentLeafDto
import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.persistence.utxo.impl.UtxoTransactionOutputDto
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import javax.persistence.Tuple

@Component(
    service = [
        VaultNamedQueryExecutor::class,
        UsedByPersistence::class
    ],
    property = [
        SandboxConstants.CORDA_MARKER_ONLY_SERVICE
    ],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryExecutorImpl @Activate constructor(
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = VaultNamedQueryRegistry::class)
    private val registry: VaultNamedQueryRegistry,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
) : VaultNamedQueryExecutor, UsedByPersistence {

    private companion object {
        const val UTXO_VISIBLE_TX_TABLE = "utxo_visible_transaction_state"
        const val UTXO_TX_COMPONENT_TABLE = "utxo_transaction_component"
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda.TimestampLimit"

        val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    override fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: FindWithNamedQuery
    ): EntityResponse {

        log.debug("Executing query: ${request.queryName}")

        // Get the query from the registry and make sure it exists
        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }
        require(vaultNamedQuery.query.type == VaultNamedQuery.Type.WHERE_JSON) {
            "Only WHERE queries are supported for now."
        }

        // Fetch the state and refs for the given transaction IDs
        val contractStateResults = fetchStateAndRefs(
            request,
            vaultNamedQuery.query.query,
            holdingIdentity
        )

        // Deserialize the parameters into readable objects instead of bytes
        val deserializedParams = request.parameters.mapValues {
            serializationService.deserialize(it.value.array(), Any::class.java)
        }

        // Apply filters and transforming functions (if there's any)
        val filteredAndTransformedResults = contractStateResults.filter {
            vaultNamedQuery.filter?.filter(it, deserializedParams) ?: true
        }.map {
            vaultNamedQuery.mapper?.transform(it, deserializedParams) ?: it
        }.filterNotNull() // This has no effect as of now, but we keep it for safety purposes

        // Once filtering and transforming are done collector function can be applied (if present)
        val collectedResults = vaultNamedQuery.collector?.collect(
            filteredAndTransformedResults,
            deserializedParams
        )?.results?.filterNotNull() ?: filteredAndTransformedResults

        // Return the filtered/transformed/collected (if present) result to the caller
        return EntityResponse(
            collectedResults.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        )
    }

    /**
     * A function that fetches the contract states that belong to the given transaction IDs. The data stored in the
     * component table will be deserialized into contract states using component groups.
     */
    private fun fetchStateAndRefs(
        request: FindWithNamedQuery,
        whereJson: String?,
        holdingIdentity: HoldingIdentity
    ): List<StateAndRef<ContractState>> {

        validateParameters(request)

        @Suppress("UNCHECKED_CAST")
        val componentGroups = entitySandboxService.get(holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                val query = em.createNativeQuery(
                    "SELECT tc.transaction_id, tc.group_idx, tc.leaf_idx, tc.data FROM " +
                            "$UTXO_TX_COMPONENT_TABLE AS tc " +
                            "JOIN $UTXO_VISIBLE_TX_TABLE AS visible_states " +
                                "ON visible_states.transaction_id = tc.transaction_id " +
                            "$whereJson " +
                            "AND tc.group_idx IN (:groupIndices) " +
                            "AND visible_states.consumed <= :$TIMESTAMP_LIMIT_PARAM_NAME",
                    Tuple::class.java
                ).setParameter("groupIndices", listOf(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal)
                )

                request.parameters.filter { it.value != null }.forEach { rec ->
                    val bytes = rec.value.array()
                    query.setParameter(rec.key, serializationService.deserialize(bytes))
                }

                query.firstResult = request.offset
                query.maxResults = request.limit

                query.resultList as List<Tuple>
            }.map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }.groupBy { it.groupIndex }

        val outputInfos = componentGroups[UtxoComponentGroup.OUTPUTS_INFO.ordinal]
            ?.associate { Pair(it.leafIndex, it.data) }
            ?: emptyMap()

        val utxoComponentDtos = componentGroups[UtxoComponentGroup.OUTPUTS.ordinal]?.map {
            val info = outputInfos[it.leafIndex]
            requireNotNull(info) {
                "Missing output info at index [${it.leafIndex}] for UTXO transaction with ID [${it.transactionId}]"
            }
            UtxoTransactionOutputDto(it.transactionId, it.leafIndex, info, it.data)
        } ?: emptyList()

        return utxoComponentDtos.map {
            val info = serializationService.deserialize<UtxoOutputInfoComponent>(it.info)
            val contractState = serializationService.deserialize<ContractState>(it.data)
            StateAndRefImpl(
                state = TransactionStateImpl(contractState, info.notaryName, info.notaryKey, info.getEncumbranceGroup()),
                ref = StateRef(parseSecureHash(it.transactionId), it.leafIndex)
            )
        }
    }

    private fun validateParameters(request: FindWithNamedQuery) {
        val nullParamNames = request.parameters.filter { it.value == null }.map { it.key }

        if (nullParamNames.isNotEmpty()) {
            val msg = "Null value found for parameters ${nullParamNames.joinToString(", ")}"
            log.error(msg)
            throw NullParameterException(msg)
        }
    }
}
