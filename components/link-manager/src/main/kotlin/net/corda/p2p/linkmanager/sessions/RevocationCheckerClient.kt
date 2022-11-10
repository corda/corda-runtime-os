package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationCheckStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.RPCSenderWithDominoLogic
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.util.concurrent.TimeoutException

class RevocationCheckerClient(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig): LifecycleWithDominoTile {

    private companion object {
        const val groupAndClientName = "GatewayRevocationChecker"
        val timeout: Duration = Duration.ofSeconds(60)
        val logger = contextLogger()
    }

    private val publisherConfig = RPCConfig(
        groupAndClientName,
        groupAndClientName,
        Schemas.P2P.GATEWAY_REVOCATION_CHECK_REQUEST,
        RevocationCheckRequest::class.java,
        RevocationCheckResponse::class.java
    )
    private val rpcSender = RPCSenderWithDominoLogic(
        publisherFactory, coordinatorFactory, publisherConfig, messagingConfiguration
    )

    fun checkRevocation(request: RevocationCheckRequest): RevocationCheckStatus {
        return try {
            rpcSender.sendRequest(request).getOrThrow(timeout).status
        } catch (except: TimeoutException) {
            logger.warn("Revocation request sent to the gateway timed out.")
            return RevocationCheckStatus.REVOKED
        }
    }

    override val dominoTile = rpcSender.dominoTile
}