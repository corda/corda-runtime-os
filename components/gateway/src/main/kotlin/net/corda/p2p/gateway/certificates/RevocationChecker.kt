package net.corda.p2p.gateway.certificates

import net.corda.crypto.utils.AllowAllRevocationChecker
import net.corda.crypto.utils.convertToKeyStore
import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.data.p2p.gateway.certificates.Revoked
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.RPCSubscriptionDominoTile
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.schema.Schemas.P2P.GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
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
                    val certPathBuilder = CertPathBuilder.getInstance(certificateAlgorithm)
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
        GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC,
        RevocationCheckRequest::class.java,
        RevocationCheckResponse::class.java
    )

    private val processor = RevocationCheckProcessor(certificateFactory, certPathValidator)

    private class RevocationCheckProcessor(
        private val certificateFactory: CertificateFactory,
        private val certPathValidator: CertPathValidator
    ): RPCResponderProcessor<RevocationCheckRequest, RevocationCheckResponse> {
        override fun onNext(request: RevocationCheckRequest, respFuture: CompletableFuture<RevocationCheckResponse>) {
            val revocationMode = request.mode
            if (revocationMode == null) {
                respFuture.completeExceptionally(
                    IllegalStateException("Revocation check request cannot be made, revocation mode is null.")
                )
                return
            }
            val trustStore = request.trustedCertificates?.let { convertToKeyStore(certificateFactory, it, "trusted") }
            if (trustStore == null) {
                respFuture.completeExceptionally(
                    IllegalStateException("Revocation check request cannot be made, trust store could not be parsed from pem format.")
                )
                return
            }
            val certificateChain = try {
                certificateFactory.generateCertPath(request.certificates.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                })
            } catch (exception: CertificateException) {
                respFuture.completeExceptionally(IllegalStateException(
                    "Revocation check request cannot be made, certificate chain could not be parsed from pem format. Cause by:\n"
                        + exception.message
                ))
                return
            }
            if (certificateChain == null) {
                respFuture.completeExceptionally(
                    IllegalStateException("Revocation check request cannot be made, certificate chain could not be parsed from pem format.")
                )
                return
            }
            val pkixParams = getCertCheckingParameters(trustStore, revocationMode.toRevocationConfig())

            try {
                certPathValidator.validate(certificateChain, pkixParams)
            } catch (exception: CertPathValidatorException) {
                respFuture.complete(RevocationCheckResponse(Revoked(exception.message, exception.index)))
                return
            }
            respFuture.complete(RevocationCheckResponse(Active()))
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
