package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.ResultSetImpl
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryEventParams
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import java.nio.ByteBuffer
import java.time.Instant

// TODO CORE-12032 use delegation to create this class
class VaultNamedParameterizedQueryImpl<T>(
    private val queryName: String,
    private val externalEventExecutor: ExternalEventExecutor,
    private val serializationService: SerializationService,
    private val resultClass: Class<T>
) : VaultNamedParameterizedQuery<T> {

    private companion object {
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda.TimestampLimit"
    }

    private var limit: Int? = null
    private var offset: Int? = null
    private val queryParams = mutableMapOf<String, Any>()

    override fun setLimit(limit: Int): VaultNamedParameterizedQuery<T> {
        require(this.limit == null) { "Limit is already set." }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): VaultNamedParameterizedQuery<T> {
        require(this.offset == null) { "Offset is already set." }
        this.offset = offset
        return this
    }

    override fun setParameter(name: String, value: Any): VaultNamedParameterizedQuery<T> {
        require(queryParams[name] == null) { "Parameter with key $name is already set." }
        queryParams[name] = value
        return this
    }

    override fun setParameters(parameters: MutableMap<String, Any>): VaultNamedParameterizedQuery<T> {
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

        getCreatedTimestampLimit()?.let {
            require(it <= Instant.now()) {
                "Timestamp limit must not be in the future."
            }
        } ?: setCreatedTimestampLimit(Instant.now())

        val results = externalEventExecutor.execute(
            VaultNamedQueryExternalEventFactory::class.java,
            VaultNamedQueryEventParams(queryName, getSerializedParameters(queryParams), offsetValue, limitValue)
        )

        return ResultSetImpl(
            results.map { serializationService.deserialize(it.array(), resultClass) }
        )
    }

    override fun setCreatedTimestampLimit(timestampLimit: Instant): VaultNamedParameterizedQuery<T> {
        require(timestampLimit <= Instant.now()) { "Timestamp limit must not be in the future." }

        queryParams[TIMESTAMP_LIMIT_PARAM_NAME] = timestampLimit
        return this
    }

    private fun getCreatedTimestampLimit() = queryParams[TIMESTAMP_LIMIT_PARAM_NAME] as? Instant

    private fun getSerializedParameters(parameters: Map<String, Any>) : Map<String, ByteBuffer> {
        return parameters.mapValues {
            ByteBuffer.wrap(serializationService.serialize(it.value).bytes)
        }
    }
}