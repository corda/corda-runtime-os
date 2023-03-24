package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryEventParams
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import java.nio.ByteBuffer
import java.time.Instant

class VaultNamedParameterizedQueryImpl<T>(
    private val queryName: String,
    private val externalEventExecutor: ExternalEventExecutor,
    private val serializationService: SerializationService,
    private val resultClass: Class<T>
) : VaultNamedParameterizedQuery<T> {

    private var limit: Int? = null
    private var offset: Int? = null
    private var timestampLimit: Instant? = null
    private val queryParams = mutableMapOf<String, Any>()

    override fun setLimit(limit: Int): ParameterizedQuery<T> {
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): ParameterizedQuery<T> {
        this.offset = offset
        return this
    }

    override fun setParameter(name: String, value: Any): ParameterizedQuery<T> {
        require(queryParams[name] == null) { "Parameter with key $name is already set." }
        queryParams[name] = value
        return this
    }

    override fun setParameters(parameters: MutableMap<String, Any>): ParameterizedQuery<T> {
        val existingParams = (queryParams - parameters).map { it.key }

        require(existingParams.isEmpty()) { "Parameters with keys: $existingParams are already set." }
        queryParams.putAll(parameters)
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<T> {
        val offsetValue = offset
        val limitValue = limit

        require(offsetValue != null && offsetValue >= 0) {
            "Offset needs to be provided and needs to be a positive number to execute the query."
        }
        require(limitValue != null && limitValue > 0) {
            "Limit needs to be provided and needs to be a positive number to execute the query."
        }

        val results = externalEventExecutor.execute(
            VaultNamedQueryExternalEventFactory::class.java,
            VaultNamedQueryEventParams(queryName, getSerializedParameters(queryParams), offsetValue, limitValue)
        )

        return LedgerResultSetImpl(
            results.map { serializationService.deserialize(it.array(), resultClass) }
        )
    }

    override fun setCreatedTimestampLimit(timestampLimit: Instant): VaultNamedParameterizedQuery<T> {
        this.timestampLimit = timestampLimit
        return this
    }

    private fun getSerializedParameters(parameters: Map<String, Any>) : Map<String, ByteBuffer> {
        return parameters.mapValues {
            ByteBuffer.wrap(serializationService.serialize(it.value).bytes)
        }
    }
}