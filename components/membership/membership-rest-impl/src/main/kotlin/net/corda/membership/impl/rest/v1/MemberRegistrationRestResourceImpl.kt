package net.corda.membership.impl.rest.v1

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.httprpc.v1.MemberRegistrationRestResource
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.httprpc.v1.types.response.RestRegistrationRequestStatus
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRestResource::class])
class MemberRegistrationRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberResourceClient::class)
    private val memberResourceClient: MemberResourceClient,
) : MemberRegistrationRestResource, PluggableRestResource<MemberRegistrationRestResource>, Lifecycle {
    private interface InnerMemberRegistrationRestResource {
        fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress

        fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RestRegistrationRequestStatus>
        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String
        ): RestRegistrationRequestStatus
    }

    override val protocolVersion = 1

    private var impl: InnerMemberRegistrationRestResource = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberRegistrationRestResource>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RestResourceLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MemberResourceClient>())
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

    private object InactiveImpl : InnerMemberRegistrationRestResource {
        override fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ) =
            throw ServiceUnavailableException(
                "${MemberRegistrationRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )

        override fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RestRegistrationRequestStatus> =
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

    private inner class ActiveImpl : InnerMemberRegistrationRestResource {
        override fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress {
            val dto = memberRegistrationRequest.toDto(holdingIdentityShortHash)
            try {
                return memberResourceClient.startRegistration(dto).fromDto()
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException(
                    "holdingIdentityShortHash",
                    holdingIdentityShortHash,
                )
            } catch (e: CordaRPCAPIPartitionException) {
                throw ServiceUnavailableException("Could not perform start registration operation: Repartition Event!")
            }
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RestRegistrationRequestStatus> {
            return try {
                memberResourceClient.checkRegistrationProgress(
                    ShortHash.parseOrThrow(holdingIdentityShortHash)
                ).map { it.fromDto() }
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException(e.message!!)
            } catch (e: ServiceNotReadyException) {
                throw ServiceUnavailableException(e.message!!)
            } catch (e: CordaRPCAPIPartitionException) {
                throw ServiceUnavailableException("Could not perform check registration operation: Repartition Event!")
            }
        }

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String,
        ): RestRegistrationRequestStatus {
            return try {
                memberResourceClient.checkSpecificRegistrationProgress(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    registrationRequestId
                ).fromDto()
            } catch (e: RegistrationProgressNotFoundException) {
                throw ResourceNotFoundException(e.message!!)
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException(e.message!!)
            } catch (e: ServiceNotReadyException) {
                throw ServiceUnavailableException(e.message!!)
            } catch (e: CordaRPCAPIPartitionException) {
                throw ServiceUnavailableException("Could not perform check specific registration operation: Repartition Event!")
            }
        }
    }
}
