package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

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
): VaultNamedQueryExecutor, UsedByPersistence {

    private companion object {
        private const val UTXO_VISIBLE_TX_TABLE = "utxo_visible_transaction_state"

        private val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    override fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: FindWithNamedQuery
    ): EntityResponse {

        log.debug("Executing query: ${request.queryName}")

        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }

        val resultList = entitySandboxService.get(holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                // TODO For now we only select the tx id but we might need the whole entity?
                val query = em.createNativeQuery(
                    "SELECT transaction_id FROM $UTXO_VISIBLE_TX_TABLE " +
                            vaultNamedQuery.whereJson // TODO Add timestamp limit logic
                )

                request.parameters.forEach {
                    query.setParameter(it.key, serializationService.deserialize(it.value.array(), Any::class.java))
                }

                query.firstResult = request.offset
                query.maxResults = request.limit

                query.resultList
            }

        return EntityResponse(
            resultList.filterNotNull().map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        )
    }
}
