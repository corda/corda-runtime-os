package net.corda.membership.impl.persistence.service

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.QueryFailedResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.impl.persistence.service.handler.PersistMemberInfoHandler
import net.corda.membership.impl.persistence.service.handler.PersistRegistrationRequestHandler
import net.corda.membership.impl.persistence.service.handler.PersistenceHandler
import net.corda.membership.impl.persistence.service.handler.PersistenceHandlerServices
import net.corda.membership.impl.persistence.service.handler.QueryMemberInfoHandler
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.lang.reflect.Constructor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
internal class MembershipPersistenceRPCProcessor(
    private val clock: Clock,
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    memberInfoFactory: MemberInfoFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    virtualNodeInfoReadService: VirtualNodeInfoReadService
) : RPCResponderProcessor<MembershipPersistenceRequest, MembershipPersistenceResponse> {

    private companion object {
        val logger = contextLogger()
    }

    private val handlers: Map<Class<*>, Class<out PersistenceHandler<out Any>>> = mapOf(
        PersistRegistrationRequest::class.java to PersistRegistrationRequestHandler::class.java,
        PersistMemberInfo::class.java to PersistMemberInfoHandler::class.java,
        QueryMemberInfo::class.java to QueryMemberInfoHandler::class.java,
    )
    private val constructors = ConcurrentHashMap<Class<*>, Constructor<*>>()
    private val persistenceHandlerServices = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService
    )

    override fun onNext(
        request: MembershipPersistenceRequest,
        respFuture: CompletableFuture<MembershipPersistenceResponse>
    ) {
        logger.info("Processor received new RPC persistence request. Selecting handler.")
        val result = try {
            getHandler(request.request::class.java).invoke(request.context, request.request)
        } catch (e: Exception) {
            val error = "Exception thrown while processing membership persistence request: ${e.message}"
            logger.warn(error)
            QueryFailedResponse(error)
        }
        respFuture.complete(
            MembershipPersistenceResponse(
                buildResponseContext(request.context),
                result
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(requestClass: Class<*>): PersistenceHandler<Any> {
        return constructors.computeIfAbsent(requestClass) {
            val type = handlers[requestClass] ?: throw MembershipPersistenceException(
                "No handler has been registered to handle the persistence request received." +
                        "Request received: [$requestClass]"
            )
            type.constructors.first {
                it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(PersistenceHandlerServices::class.java)
            }.apply { isAccessible = true }
        }.newInstance(persistenceHandlerServices) as PersistenceHandler<Any>
    }

    private fun buildResponseContext(requestContext: MembershipRequestContext): MembershipResponseContext {
        return with(requestContext) {
            MembershipResponseContext(
                requestTimestamp,
                requestId,
                clock.instant(),
                holdingIdentity
            )
        }
    }
}
