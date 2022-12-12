package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRPCOps::class])
class NetworkRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
) : NetworkRpcOps, PluggableRPCOps<NetworkRpcOps>, Lifecycle {

    private companion object {
        private val logger = contextLogger()
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
        } catch (e: Throwable) {
            logger.warn("Could not publish to locally hosted identities", e)
            throw InternalServerException("Could not import certificate: ${e.message}")
        }
    }

    override val targetInterface = NetworkRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<NetworkRpcOps>(
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
