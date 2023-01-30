package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.data.p2p.gateway.certificates.Revoked
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.RPCSenderWithDominoLogic
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.utilities.concurrent.getOrThrow
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeoutException

class RevocationCheckerClient(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig): LifecycleWithDominoTile {

    private companion object {
        const val groupAndClientName = "GatewayRevocationChecker"
        val timeout: Duration = Duration.ofSeconds(60)
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val publisherConfig = RPCConfig(
        groupAndClientName,
        groupAndClientName,
        Schemas.P2P.GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC,
        RevocationCheckRequest::class.java,
        RevocationCheckResponse::class.java
    )
    private val rpcSender = RPCSenderWithDominoLogic(
        publisherFactory, coordinatorFactory, publisherConfig, messagingConfiguration
    )

    fun checkRevocation(request: RevocationCheckRequest): RevocationCheckResponse {
        return try {
            rpcSender.sendRequest(request).getOrThrow(timeout)
        } catch (except: TimeoutException) {
            when (request.mode) {
                RevocationMode.SOFT_FAIL -> {
                    logger.warn("Revocation request sent to the gateway timed out. As revocation mode is soft fail, the certificate was " +
                        "treated as valid.")
                    RevocationCheckResponse(Active())
                }
                RevocationMode.HARD_FAIL -> {
                    RevocationCheckResponse(Revoked("Revocation request sent to the gateway timed out. As revocation mode is hard " +
                        "fail, the certificate is treated as invalid.", -1))
                }
                null -> {
                    throw IllegalStateException("The revocation mode in the RevocationCheckRequest must not be null.")
                }
            }
        }
    }

    override val dominoTile = rpcSender.dominoTile
}