package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.MemberOpsClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.httprpc.v1.MemberRegistrationRestResource
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestStatus
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRestResource::class])
class MemberRegistrationRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberOpsClient::class)
    private val memberOpsClient: MemberOpsClient
) : MemberRegistrationRestResource, PluggableRestResource<MemberRegistrationRestResource>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private interface InnerMemberRegistrationRpcOps {
        fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress

        fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RegistrationRequestStatus>
        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String
        ): RegistrationRequestStatus?
    }

    private val className = this::class.java.simpleName

    override val protocolVersion = 1

    private var impl: InnerMemberRegistrationRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberRegistrationRestResource>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MemberOpsClient>())
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MemberRegistrationRestResource> = MemberRegistrationRestResource::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun startRegistration(
        holdingIdentityShortHash: String,
        memberRegistrationRequest: MemberRegistrationRequest
    ) = impl.startRegistration(holdingIdentityShortHash, memberRegistrationRequest)

    override fun checkRegistrationProgress(
        holdingIdentityShortHash: String
    ) = impl.checkRegistrationProgress(holdingIdentityShortHash)

    override fun checkSpecificRegistrationProgress(
        holdingIdentityShortHash: String,
        registrationRequestId: String,
    ) = impl.checkSpecificRegistrationProgress(holdingIdentityShortHash, registrationRequestId)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMemberRegistrationRpcOps {
        override fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ) =
            throw ServiceUnavailableException(
                "${MemberRegistrationRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )

        override fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RegistrationRequestStatus> =
            throw ServiceUnavailableException(
                "${MemberRegistrationRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String,
        ) =
            throw ServiceUnavailableException(
                "${MemberRegistrationRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
    }

    private inner class ActiveImpl : InnerMemberRegistrationRpcOps {
        override fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress {
            return memberOpsClient.startRegistration(memberRegistrationRequest.toDto(holdingIdentityShortHash)).fromDto()
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RegistrationRequestStatus> {
            return try {
                memberOpsClient.checkRegistrationProgress(
                    ShortHash.parseOrThrow(holdingIdentityShortHash)
                ).map { it.fromDto() }
            } catch (e: RegistrationProgressNotFoundException) {
                throw ResourceNotFoundException(e.message!!)
            }
        }

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String,
        ): RegistrationRequestStatus? {
            return try {
                memberOpsClient.checkSpecificRegistrationProgress(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    registrationRequestId
                )?.fromDto()
            } catch (e: RegistrationProgressNotFoundException) {
                throw ResourceNotFoundException(e.message!!)
            }
        }
    }
}
