package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryEventParams
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import java.time.Instant

// TODO CORE-12032 use delegation to create this class
@Suppress("LongParameterList")
class VaultNamedParameterizedQueryImpl<T>(
    private val queryName: String,
    private val externalEventExecutor: ExternalEventExecutor,
    private val resultSetFactory: ResultSetFactory,
    private var parameters: MutableMap<String, Any>,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<T>
) : VaultNamedParameterizedQuery<T> {

    private companion object {
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"
    }

    override fun setLimit(limit: Int): VaultNamedParameterizedQuery<T> {
        require (limit >= 0) { "Limit cannot be negative" }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): VaultNamedParameterizedQuery<T> {
        require (offset >= 0) { "Offset cannot be negative" }
        this.offset = offset
        return this
    }

    override fun setParameter(name: String, value: Any): VaultNamedParameterizedQuery<T> {
        parameters[name] = value
        return this
    }

    override fun setParameters(parameters: MutableMap<String, Any>): VaultNamedParameterizedQuery<T> {
        this.parameters = parameters.toMutableMap()
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<T> {
        getCreatedTimestampLimit()?.let {
            require(it <= Instant.now()) {
                "Timestamp limit must not be in the future."
            }
        } ?: setCreatedTimestampLimit(Instant.now())

        val resultSet = resultSetFactory.create(
            parameters,
            limit,
            offset,
            resultClass
        ) @Suspendable { serializedParameters, offset ->
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    VaultNamedQueryExternalEventFactory::class.java,
                    VaultNamedQueryEventParams(queryName, serializedParameters, offset, limit)
                )
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
}