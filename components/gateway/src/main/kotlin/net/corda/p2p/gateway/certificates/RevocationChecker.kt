package net.corda.p2p.gateway.certificates

import net.corda.crypto.utils.AllowAllRevocationChecker
import net.corda.crypto.utils.convertToKeyStore
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.RPCSubscriptionDominoTile
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_REVOCATION_CHECK_REQUEST
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.util.concurrent.CompletableFuture

class RevocationChecker(
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    certificateFactory: CertificateFactory = CertificateFactory.getInstance(certificateFactoryType),
    certPathValidator: CertPathValidator = CertPathValidator.getInstance(certificateAlgorithm)
): LifecycleWithDominoTile {

    companion object {
        private const val groupAndClientName = "GatewayRevocationChecker"
        private const val certificateAlgorithm = "PKIX"
        private const val certificateFactoryType = "X.509"

        fun getCertCheckingParameters(trustStore: KeyStore, revocationConfig: RevocationConfig): PKIXBuilderParameters {
            val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
            val revocationChecker = when (revocationConfig.mode) {
                RevocationConfigMode.OFF -> AllowAllRevocationChecker
                RevocationConfigMode.SOFT_FAIL, RevocationConfigMode.HARD_FAIL -> {
                    val certPathBuilder = CertPathBuilder.getInstance("PKIX")
                    val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
                    // We only set SOFT_FAIL as a checker option if specified. Everything else is left as default, which means
                    // OCSP is used if possible, CRL as a fallback
                    if (revocationConfig.mode == RevocationConfigMode.SOFT_FAIL) {
                        pkixRevocationChecker.options = setOf(PKIXRevocationChecker.Option.SOFT_FAIL)
                    }
                    pkixRevocationChecker
                }
            }
            pkixParams.addCertPathChecker(revocationChecker)
            return pkixParams
        }
    }

    internal val subscriptionConfig = RPCConfig(
        groupAndClientName,
        groupAndClientName,
        GATEWAY_REVOCATION_CHECK_REQUEST,
        RevocationCheckRequest::class.java,
        RevocationCheckStatus::class.java
    )

    private val processor = RevocationCheckProcessor(certificateFactory, certPathValidator)

    private class RevocationCheckProcessor(
        private val certificateFactory: CertificateFactory,
        private val certPathValidator: CertPathValidator
    ): RPCResponderProcessor<RevocationCheckRequest, RevocationCheckStatus> {
        override fun onNext(request: RevocationCheckRequest, respFuture: CompletableFuture<RevocationCheckStatus>) {
            val revocationMode = request.mode
            if (revocationMode == null) {
                respFuture.completeExceptionally(IllegalStateException("The revocation mode cannot be null."))
                return
            }
            val trustStore = request.trustedCertificates?.let {convertToKeyStore(certificateFactory, it, "trusted") }
            if (trustStore == null) {
                respFuture.completeExceptionally(IllegalStateException("The trusted certificates cannot be null."))
                return
            }
            val certificateChain = certificateFactory.generateCertPath(request.certificates.map { pemCertificate ->
                ByteArrayInputStream(pemCertificate.toByteArray()).use {
                    certificateFactory.generateCertificate(it)
                }
            })
            if (certificateChain == null) {
                respFuture.completeExceptionally(IllegalStateException("The revocation mode cannot be null."))
                return
            }
            val pkixRevocationChecker = getCertCheckingParameters(trustStore, revocationMode.toRevocationConfig())

            try {
                certPathValidator.validate(certificateChain, pkixRevocationChecker)
            } catch (exception: CertPathValidatorException) {
                respFuture.complete(RevocationCheckStatus.REVOKED)
                return
            }
            respFuture.complete(RevocationCheckStatus.ACTIVE)
        }

        private fun RevocationMode.toRevocationConfig(): RevocationConfig {
            return when (this) {
                RevocationMode.SOFT_FAIL -> RevocationConfig(RevocationConfigMode.SOFT_FAIL)
                RevocationMode.HARD_FAIL -> RevocationConfig(RevocationConfigMode.HARD_FAIL)
            }
        }
    }

    private val subscription = {
        subscriptionFactory.createRPCSubscription(subscriptionConfig, messagingConfig, processor)
    }

    override val dominoTile = RPCSubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptySet(),
        emptySet()
    )
}