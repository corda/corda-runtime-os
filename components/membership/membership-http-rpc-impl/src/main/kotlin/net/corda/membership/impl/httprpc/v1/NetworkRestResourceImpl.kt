package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.httprpc.v1.NetworkRestResource
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.security.SignatureException

@Component(service = [PluggableRestResource::class])
class NetworkRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
) : NetworkRestResource, PluggableRestResource<NetworkRestResource>, Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun setupHostedIdentities(
        holdingIdentityShortHash: String,
        request: HostedIdentitySetupRequest
    ) {
        try {
            certificatesClient.setupLocallyHostedIdentity(
                ShortHash.parseOrThrow(holdingIdentityShortHash),
                request.p2pTlsCertificateChainAlias,
                request.useClusterLevelTlsCertificateAndKey != false,
                request.useClusterLevelSessionCertificateAndKey == true,
                request.sessionKeyId,
                request.sessionCertificateChainAlias
            )
        } catch (e: CertificatesResourceNotFoundException) {
            throw ResourceNotFoundException(e.message)
        } catch (e: BadRequestException) {
            logger.warn(e.message)
            throw e
        } catch (e: SignatureException) {
            logger.warn("Could not set up locally hosted identities", e)
            throw BadRequestException("The certificate was not signed by the correct certificate authority. ${e.message}")
        } catch (e: Throwable) {
            logger.warn("Could not publish to locally hosted identities", e)
            throw InternalServerException("Could not import certificate: ${e.message}")
        }
    }

    override val targetInterface = NetworkRestResource::class.java

    override val protocolVersion = 1

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

    private val lifecycleHandler = RpcOpsLifecycleHandler(
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
}
