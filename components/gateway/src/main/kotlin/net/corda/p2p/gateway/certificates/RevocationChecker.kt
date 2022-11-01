package net.corda.p2p.gateway.certificates

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.RPCSubscriptionDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.http.AllowAllRevocationChecker
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_REVOCATION_CHECK_REQUEST
import net.corda.schema.configuration.ConfigKeys
import java.security.KeyStore
import java.security.cert.CertPathBuilder
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.util.concurrent.CompletableFuture

class RevocationChecker(
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
): LifecycleWithDominoTile {

    private companion object {
        const val groupAndClientName = "GatewayRevocationChecker"
    }

    private val subscriptionConfig = RPCConfig(
        groupAndClientName,
        groupAndClientName,
        GATEWAY_REVOCATION_CHECK_REQUEST,
        RevocationCheckRequest::class.java,
        RevocationCheckStatus::class.java
    )

    private object Processor: RPCResponderProcessor<RevocationCheckRequest, RevocationCheckStatus> {
        override fun onNext(request: RevocationCheckRequest, respFuture: CompletableFuture<RevocationCheckStatus>) {
            val keyStore: KeyStore? = null
            val pkixParams = PKIXBuilderParameters(keyStore, X509CertSelector())
            val revocationChecker = when (request.mode) {
                RevocationMode.SOFT_FAIL, RevocationMode.HARD_FAIL -> {
                    val certPathBuilder = CertPathBuilder.getInstance("PKIX")
                    val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
                    // We only set SOFT_FAIL as a checker option if specified. Everything else is left as default, which means
                    // OCSP is used if possible, CRL as a fallback
                    if (request.mode == RevocationMode.SOFT_FAIL) {
                        pkixRevocationChecker.options = setOf(PKIXRevocationChecker.Option.SOFT_FAIL)
                    }
                    pkixRevocationChecker
                }
            }
            pkixParams.addCertPathChecker(revocationChecker)
        }
    }

    private val subscription = {
        subscriptionFactory.createRPCSubscription(subscriptionConfig, messagingConfig, Processor)
    }

    override val dominoTile = RPCSubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptySet(),
        emptySet()
    )
}