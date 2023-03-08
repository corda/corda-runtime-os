package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
internal class HandlerFactories(
    clock: Clock,
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    memberInfoFactory: MemberInfoFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    keyEncodingService: KeyEncodingService,
    platformInfoProvider: PlatformInfoProvider,
    allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
) {
    val persistenceHandlerServices = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService,
        keyEncodingService,
        platformInfoProvider,
        allowedCertificatesReaderWriterService,
    )
    private val handlerFactories: Map<Class<*>, () -> PersistenceHandler<out Any, out Any>> = mapOf(
        PersistRegistrationRequest::class.java to { PersistRegistrationRequestHandler(persistenceHandlerServices) },
        PersistMemberInfo::class.java to { PersistMemberInfoHandler(persistenceHandlerServices) },
        QueryMemberInfo::class.java to { QueryMemberInfoHandler(persistenceHandlerServices) },
        PersistGroupPolicy::class.java to { PersistGroupPolicyHandler(persistenceHandlerServices) },
        PersistGroupParameters::class.java to { PersistGroupParametersHandler(persistenceHandlerServices) },
        PersistGroupParametersInitialSnapshot::class.java to { PersistGroupParametersInitialSnapshotHandler(persistenceHandlerServices) },
        AddNotaryToGroupParameters::class.java to { AddNotaryToGroupParametersHandler(persistenceHandlerServices) },
        QueryMemberSignature::class.java to { QueryMemberSignatureHandler(persistenceHandlerServices) },
        UpdateMemberAndRegistrationRequestToApproved::class.java to
                { UpdateMemberAndRegistrationRequestToApprovedHandler(persistenceHandlerServices) },
        UpdateRegistrationRequestStatus::class.java to { UpdateRegistrationRequestStatusHandler(persistenceHandlerServices) },
        QueryGroupPolicy::class.java to { QueryGroupPolicyHandler(persistenceHandlerServices) },
        QueryRegistrationRequest::class.java to { QueryRegistrationRequestHandler(persistenceHandlerServices) },
        QueryRegistrationRequests::class.java to { QueryRegistrationRequestsHandler(persistenceHandlerServices) },
        MutualTlsAddToAllowedCertificates::class.java to { MutualTlsAddToAllowedCertificatesHandler(persistenceHandlerServices) },
        MutualTlsRemoveFromAllowedCertificates::class.java to { MutualTlsRemoveFromAllowedCertificatesHandler(persistenceHandlerServices) },
        MutualTlsListAllowedCertificates::class.java to { MutualTlsListAllowedCertificatesHandler(persistenceHandlerServices) },
        QueryPreAuthToken::class.java to { QueryPreAuthTokenHandler(persistenceHandlerServices) },
        AddPreAuthToken::class.java to { AddPreAuthTokenHandler(persistenceHandlerServices) },
        ConsumePreAuthToken::class.java to { ConsumePreAuthTokenHandler(persistenceHandlerServices) },
        RevokePreAuthToken::class.java to { RevokePreAuthTokenHandler(persistenceHandlerServices) },
        PersistApprovalRule::class.java to { PersistApprovalRuleHandler(persistenceHandlerServices) },
        DeleteApprovalRule::class.java to { DeleteApprovalRuleHandler(persistenceHandlerServices) },
        QueryApprovalRules::class.java to { QueryApprovalRulesHandler(persistenceHandlerServices) },
    )

    private fun getHandler(requestClass: Class<*>): PersistenceHandler<Any, Any> {
        val factory = handlerFactories[requestClass] ?: throw MembershipPersistenceException(
            "No handler has been registered to handle the persistence request received." +
                "Request received: [$requestClass]"
        )
        @Suppress("UNCHECKED_CAST")
        return factory.invoke() as PersistenceHandler<Any, Any>
    }

    fun handle(request: MembershipPersistenceRequest): Any? {
        return getHandler(request.request::class.java).invoke(request.context, request.request)
    }
}