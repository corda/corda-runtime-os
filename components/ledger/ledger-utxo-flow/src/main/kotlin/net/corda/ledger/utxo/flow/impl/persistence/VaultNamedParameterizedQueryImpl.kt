package net.corda.ledger.utxo.flow.impl.persistence

import io.micrometer.core.instrument.Timer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryEventParams
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.metrics.CordaMetrics
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.time.Clock
import net.corda.utilities.toByteArrays
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import java.time.Instant

// TODO CORE-12032 use delegation to create this class
@Suppress("LongParameterList")
class VaultNamedParameterizedQueryImpl<T>(
    private val queryName: String,
    private val externalEventExecutor: ExternalEventExecutor,
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val resultSetFactory: ResultSetFactory,
    private var parameters: MutableMap<String, Any?>,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<T>,
    private val clock: Clock,
) : VaultNamedParameterizedQuery<T> {

    private companion object {
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"
    }

    override fun setLimit(limit: Int): VaultNamedParameterizedQuery<T> {
        require(limit > 0) { "Limit cannot be negative or zero" }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): VaultNamedParameterizedQuery<T> {
        throw UnsupportedOperationException("This query does not support offset functionality.")
    }

    override fun setParameter(name: String, value: Any?): VaultNamedParameterizedQuery<T> {
        parameters[name] = value
        return this
    }

    override fun setParameters(parameters: Map<String, Any?>): VaultNamedParameterizedQuery<T> {
        val timestampLimit = parameters[TIMESTAMP_LIMIT_PARAM_NAME]
        this.parameters = parameters.toMutableMap()
        if (timestampLimit != null) {
            this.parameters[TIMESTAMP_LIMIT_PARAM_NAME] = timestampLimit
        }
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<T> {
        getCreatedTimestampLimit()?.let {
            require(it <= clock.instant()) {
                "Timestamp limit must not be in the future."
            }
        } ?: setCreatedTimestampLimit(clock.instant())

        val resultSet = resultSetFactory.create(
            parameters,
            limit,
            resultClass
        ) @Suspendable { serializedParameters, resumePoint, offset ->
            recordSuspendable(::ledgerPersistenceFlowTimer) @Suspendable {
                wrapWithPersistenceException {
                    externalEventExecutor.execute(
                        VaultNamedQueryExternalEventFactory::class.java,
                        VaultNamedQueryEventParams(
                            queryName,
                            serializedParameters.toByteArrays(),
                            limit,
                            resumePoint?.array(),
                            offset
                        )
                    )
                }
            }
        }
        resultSet.next()
        return resultSet
    }

    override fun setCreatedTimestampLimit(timestampLimit: Instant): VaultNamedParameterizedQuery<T> {
        require(timestampLimit <= Instant.now()) { "Timestamp limit must not be in the future." }

        parameters[TIMESTAMP_LIMIT_PARAM_NAME] = timestampLimit
        return this
    }

    private fun getCreatedTimestampLimit() = parameters[TIMESTAMP_LIMIT_PARAM_NAME] as? Instant

    private fun ledgerPersistenceFlowTimer(): Timer {
        return CordaMetrics.Metric.Ledger.PersistenceFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, LedgerPersistenceMetricOperationName.FindWithNamedQuery.name)
            .build()
    }
}
