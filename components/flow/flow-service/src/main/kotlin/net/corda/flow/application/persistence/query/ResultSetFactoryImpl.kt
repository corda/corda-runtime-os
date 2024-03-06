package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer

@Component(
    service = [ResultSetFactory::class, UsedByFlow::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class ResultSetFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : ResultSetFactory, UsedByFlow {

    override fun <R> create(
        parameters: Map<String, Any?>,
        limit: Int,
        offset: Int,
        resultClass: Class<R>,
        resultSetExecutor: OffsetResultSetExecutor<R>
    ): PagedQuery.ResultSet<R> {
        return OffsetResultSetImpl(serializationService, getSerializedParameters(parameters), limit, offset, resultClass, resultSetExecutor)
    }

    override fun <R> createStable(
        parameters: Map<String, Any?>,
        limit: Int,
        offset: Int,
        resultClass: Class<R>,
        resultSetExecutor: StableResultSetExecutor<R>
    ): PagedQuery.ResultSet<R> {
        return StableResultSetImpl(
            serializationService = serializationService,
            serializedParameters = getSerializedParameters(parameters).toMutableMap(),
            limit = limit,
            offset = offset,
            resultClass = resultClass,
            resultSetExecutor = resultSetExecutor)
    }

    private fun getSerializedParameters(parameters: Map<String, Any?>): Map<String, ByteBuffer?> {
        return parameters.mapValues { (_, param) ->
            param?.let {
                ByteBuffer.wrap(serializationService.serialize(it).bytes)
            }
        }
    }
}
