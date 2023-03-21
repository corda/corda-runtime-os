package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

@Component(service = [VaultNamedQueryExecutor::class])
class VaultNamedQueryExecutorImpl @Activate constructor(
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = VaultNamedQueryRegistry::class)
    private val registry: VaultNamedQueryRegistry,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
): VaultNamedQueryExecutor {

    private companion object {
        private const val UTXO_RELEVANT_TX_TABLE = "utxo_relevant_transaction_state"

        private val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    override fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: ExecuteVaultNamedQueryRequest
    ): PagedQuery.ResultSet<ByteBuffer> {

        log.info("Executing query: ${request.queryName}")

        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }

        val (resultList, newOffset) = entitySandboxService.get(holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                // TODO For now we only select the tx id but we might need the whole entity?
                val query = em.createQuery(
                    "SELECT transaction_id FROM $UTXO_RELEVANT_TX_TABLE " +
                            vaultNamedQuery.whereJson
                )

                request.queryParameters.forEach {
                    query.setParameter(it.key, serializationService.deserialize(it.value.array(), Any::class.java))
                }

                val originalResultList = query.resultList

                query.firstResult = request.offset
                query.maxResults = request.limit

                Pair(query.resultList, originalResultList.size - request.limit)
            }

        return LedgerResultSetImpl(
            if (newOffset > 0) newOffset else 0, // We don't want any negative numbers so just reset to 0
            resultList.size,
            newOffset > 0,
            resultList.filterNotNull().map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        )
    }
}