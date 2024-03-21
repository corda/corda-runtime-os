package net.corda.membership.impl.rest.v1

import net.corda.crypto.core.ShortHash
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySessionKeyAndCertificate
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.virtualnode.read.rest.extensions.createKeyIdOrHttpThrow
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class NetworkRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : NetworkRestResource, PluggableRestResource<NetworkRestResource>, Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun setupHostedIdentities(
        holdingIdentityShortHash: String,
        request: HostedIdentitySetupRequest
    ) {
        val operation = "set up locally hosted identities"
        if (request.sessionKeysAndCertificates.isEmpty()) {
            throw BadRequestException("No session keys where defined.")
        }
        val preferredSessionKeys = request.sessionKeysAndCertificates.filter {
            it.preferred
        }
        if (preferredSessionKeys.isEmpty()) {
            throw BadRequestException("No preferred session key was selected.")
        }
        if (preferredSessionKeys.size > 1) {
            throw BadRequestException("Can not have more than one preferred session key.")
        }
        val preferredSessionKey = preferredSessionKeys.first()
        val alternativeSessionKeys = request.sessionKeysAndCertificates.filter {
            !it.preferred
        }
        try {
            certificatesClient.setupLocallyHostedIdentity(
                ShortHash.parseOrThrow(holdingIdentityShortHash),
                request.p2pTlsCertificateChainAlias,
                request.useClusterLevelTlsCertificateAndKey != false,
                preferredSessionKey.toSessionKey(),
                alternativeSessionKeys.map { it.toSessionKey() },
            )
        } catch (e: CertificatesResourceNotFoundException) {
            throw ResourceNotFoundException(e.message)
        } catch (e: BadRequestException) {
            logger.warn("Could not $operation", e)
            throw e
        } catch (e: CordaRPCAPIPartitionException) {
            logger.warn("Could not $operation", e)
            throw ServiceUnavailableException(
                e::class.java.simpleName,
                ExceptionDetails(e::class.java.name, "Could not $operation: Repartition Event!")
            )
        } catch (e: Throwable) {
            logger.warn("Could not publish to locally hosted identities", e)
            throw InternalServerException(
                title = e::class.java.simpleName,
                exceptionDetails = ExceptionDetails(e::class.java.name, "Could not import certificate: ${e.message}")
            )
        }
    }

    override val targetInterface = NetworkRestResource::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val coordinatorName = LifecycleCoordinatorName.forComponent<NetworkRestResource>(
        protocolVersion.toString()
    )
    private fun updateStatus(status: LifecycleStatus, reason: String) {
        coordinator.updateStatus(status, reason)
    }

    private fun activate(reason: String) {
        updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
    }

    private val lifecycleHandler = RestResourceLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<CertificatesClient>(),
        )
    )
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun HostedIdentitySessionKeyAndCertificate.toSessionKey(): CertificatesClient.SessionKeyAndCertificate =
        CertificatesClient.SessionKeyAndCertificate(
            createKeyIdOrHttpThrow(this.sessionKeyId),
            this.sessionCertificateChainAlias,
        )
}
