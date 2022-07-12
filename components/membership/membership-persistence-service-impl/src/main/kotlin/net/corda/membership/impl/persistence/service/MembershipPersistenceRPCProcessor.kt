package net.corda.membership.impl.persistence.service

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.impl.persistence.service.handler.PersistMemberInfoHandler
import net.corda.membership.impl.persistence.service.handler.PersistRegistrationRequestHandler
import net.corda.membership.impl.persistence.service.handler.PersistenceHandler
import net.corda.membership.impl.persistence.service.handler.PersistenceHandlerServices
import net.corda.membership.impl.persistence.service.handler.QueryMemberInfoHandler
import net.corda.membership.impl.persistence.service.handler.QueryMemberSignatureHandler
import net.corda.membership.impl.persistence.service.handler.UpdateMemberAndRegistrationRequestToApprovedHandler
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.CompletableFuture

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

    private val persistenceHandlerServices = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService
    )
    private val handlerFactories: Map<Class<*>, () -> PersistenceHandler<out Any, out Any>> = mapOf(
        PersistRegistrationRequest::class.java to { PersistRegistrationRequestHandler(persistenceHandlerServices) },
        PersistMemberInfo::class.java to { PersistMemberInfoHandler(persistenceHandlerServices) },
        QueryMemberInfo::class.java to { QueryMemberInfoHandler(persistenceHandlerServices) },
        QueryMemberSignature::class.java to { QueryMemberSignatureHandler(persistenceHandlerServices) },
        UpdateMemberAndRegistrationRequestToApproved::class.java to
            { UpdateMemberAndRegistrationRequestToApprovedHandler(persistenceHandlerServices) },
    )

    override fun onNext(
        request: MembershipPersistenceRequest,
        respFuture: CompletableFuture<MembershipPersistenceResponse>
    ) {
        logger.info("Processor received new RPC persistence request. Selecting handler.")
        val result = try {
            val result = getHandler(request.request::class.java).invoke(request.context, request.request)
            if (result is Unit) {
                null
            } else {
                result
            }
        } catch (e: Exception) {
            val error = "Exception thrown while processing membership persistence request: ${e.message}"
            logger.warn(error)
            PersistenceFailedResponse(error)
        }
        respFuture.complete(
            MembershipPersistenceResponse(
                buildResponseContext(request.context),
                result
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(requestClass: Class<*>): PersistenceHandler<Any, Any> {
        val factory = handlerFactories[requestClass] ?: throw MembershipPersistenceException(
            "No handler has been registered to handle the persistence request received." +
                "Request received: [$requestClass]"
        )
        return factory.invoke() as PersistenceHandler<Any, Any>
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
