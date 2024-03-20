package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.rest.v1.MGMAdminRestResource
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRestResource::class])
class MGMAdminRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MGMResourceClient::class)
    private val mgmResourceClient: MGMResourceClient,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : MGMAdminRestResource, PluggableRestResource<MGMAdminRestResource>, Lifecycle {

    private interface InnerMGMAdminRestResource {
        fun forceDeclineRegistrationRequest(holdingIdentityShortHash: String, requestId: String)
    }

    override val targetInterface: Class<MGMAdminRestResource> = MGMAdminRestResource::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private var impl: InnerMGMAdminRestResource = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMAdminRestResource>(protocolVersion.toString())

    private val lifecycleHandler = RestResourceLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<MGMResourceClient>(),
        )
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun forceDeclineRegistrationRequest(holdingIdentityShortHash: String, requestId: String) =
        impl.forceDeclineRegistrationRequest(holdingIdentityShortHash, requestId)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMGMAdminRestResource {

        private fun <T> throwNotRunningException(): T {
            throw ServiceUnavailableException(
                "${MGMAdminRestResourceImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun forceDeclineRegistrationRequest(holdingIdentityShortHash: String, requestId: String): Unit =
            throwNotRunningException()
    }

    private inner class ActiveImpl : InnerMGMAdminRestResource {
        override fun forceDeclineRegistrationRequest(holdingIdentityShortHash: String, requestId: String) {
            try {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    parseRegistrationRequestId(requestId)
                )
            } catch (e: CouldNotFindEntityException) {
                throw ResourceNotFoundException(e.entity, holdingIdentityShortHash)
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf("holdingIdentityShortHash" to holdingIdentityShortHash),
                    title = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            } catch (e: CordaRPCAPIPartitionException) {
                throw ServiceUnavailableException(
                    "Corda RPC API Partition Exception",
                    ExceptionDetails(
                        e::class.java.name,
                        "Could not perform operation for $holdingIdentityShortHash: Repartition Event!"
                    )
                )
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("${e.message}")
            } catch (e: ContextDeserializationException) {
                throw InternalServerException("${e.message}")
            }
        }
    }
}
