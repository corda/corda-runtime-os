package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRestResource::class])
class MemberRegistrationRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberResourceClient::class)
    private val memberResourceClient: MemberResourceClient,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : MemberRegistrationRestResource, PluggableRestResource<MemberRegistrationRestResource>, Lifecycle {
    private interface InnerMemberRegistrationRestResource {
        fun startRegistration(
            holdingIdentityShortHash: String,
            memberRegistrationContext: Map<String, String>
        ): RegistrationRequestProgress

        fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RestRegistrationRequestStatus>
        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: String,
            registrationRequestId: String
        ): RestRegistrationRequestStatus
    }

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

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
    ) = impl.startRegistration(holdingIdentityShortHash, memberRegistrationRequest.context)

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
            memberRegistrationContext: Map<String, String>,
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
            memberRegistrationContext: Map<String, String>,
        ): RegistrationRequestProgress {
            try {
                return memberResourceClient.startRegistration(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    memberRegistrationContext
                ).fromDto()
            } catch (e: CouldNotFindEntityException) {
                throw ResourceNotFoundException(
                    e.entity,
                    holdingIdentityShortHash,
                    ExceptionDetails(e::class.java.name, "${e.message}")
                )
            } catch (e: CordaRPCAPIPartitionException) {
                throw throwServiceUnavailableException(
                    e,
                    "Could not perform start registration operation: Repartition Event!"
                )
            }
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: String): List<RestRegistrationRequestStatus> {
            return try {
                memberResourceClient.checkRegistrationProgress(
                    ShortHash.parseOrThrow(holdingIdentityShortHash)
                ).map { it.fromDto() }
            } catch (e: Exception) {
                when (e) {
                    is CouldNotFindEntityException ->
                        throw ResourceNotFoundException(
                            e::class.java.simpleName,
                            ExceptionDetails(e::class.java.name, e.message!!)
                        )

                    is ContextDeserializationException ->
                        throw InternalServerException(
                            title = e::class.java.simpleName,
                            exceptionDetails = ExceptionDetails(e::class.java.name, e.message!!)
                        )

                    is ServiceNotReadyException ->
                        throw throwServiceUnavailableException(e)

                    is CordaRPCAPIPartitionException ->
                        throw throwServiceUnavailableException(
                            e,
                            "Could not perform check registration operation: Repartition Event!"
                        )

                    else -> throw e
                }
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
            } catch (e: Exception) {
                when (e) {
                    is RegistrationProgressNotFoundException, is CouldNotFindEntityException ->
                        throw ResourceNotFoundException(
                            e::class.java.simpleName,
                            ExceptionDetails(e::class.java.name, e.message!!)
                        )

                    is ContextDeserializationException ->
                        throw InternalServerException(
                            title = e::class.java.simpleName,
                            exceptionDetails = ExceptionDetails(e::class.java.name, e.message!!)
                        )

                    is ServiceNotReadyException ->
                        throw throwServiceUnavailableException(e)

                    is CordaRPCAPIPartitionException ->
                        throw throwServiceUnavailableException(
                            e,
                            "Could not perform check specific registration operation: Repartition Event!"
                        )

                    else -> throw e
                }
            }
        }

        private fun throwServiceUnavailableException(e: Exception, reason: String = e.message!!): Throwable {
            throw ServiceUnavailableException(
                e::class.java.simpleName,
                ExceptionDetails(e::class.java.name, reason)
            )
        }
    }
}
