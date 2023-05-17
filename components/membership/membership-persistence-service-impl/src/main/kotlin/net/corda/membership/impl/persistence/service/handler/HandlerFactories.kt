package net.corda.membership.impl.persistence.service.handler

import io.micrometer.core.instrument.Timer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.ActivateMember
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
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.data.membership.db.request.query.QueryStaticNetworkInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.Metric
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.ConcurrentHashMap

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
        ::getTransactionTimer
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
        SuspendMember::class.java to { SuspendMemberHandler(persistenceHandlerServices) },
        ActivateMember::class.java to { ActivateMemberHandler(persistenceHandlerServices) },
        QueryStaticNetworkInfo::class.java to { QueryStaticNetworkInfoHandler(persistenceHandlerServices) },
        UpdateStaticNetworkInfo::class.java to { UpdateStaticNetworkInfoHandler(persistenceHandlerServices) },
    )

    private val handlerTimers = ConcurrentHashMap<String, Timer>()
    private val transactionTimers = ConcurrentHashMap<String, Timer>()

    private fun getHandlerTimer(operation: String): Timer {
        return handlerTimers.computeIfAbsent(operation) {
            Metric.MembershipPersistenceHandler
                .builder()
                .withTag(CordaMetrics.Tag.OperationName, operation)
                .build()
        }
    }

    private fun getTransactionTimer(operation: String): Timer {
        return transactionTimers.computeIfAbsent(operation) {
            Metric.MembershipPersistenceTransaction
                .builder()
                .withTag(CordaMetrics.Tag.OperationName, operation)
                .build()
        }
    }

    private fun getHandler(requestClass: Class<*>): PersistenceHandler<Any, Any> {
        val factory = handlerFactories[requestClass] ?: throw MembershipPersistenceException(
            "No handler has been registered to handle the persistence request received." +
                "Request received: [$requestClass]"
        )
        @Suppress("UNCHECKED_CAST")
        return factory.invoke() as PersistenceHandler<Any, Any>
    }

    fun handle(request: MembershipPersistenceRequest): Any? {
        val rqClass = request.request::class.java
        return getHandlerTimer(rqClass.simpleName).recordCallable {
            getHandler(rqClass).invoke(request.context, request.request)
        }
    }
}