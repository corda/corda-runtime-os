package net.corda.membership.impl.synchronisation.dummy

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.ShortHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * Created for mocking and simplifying crypto functionalities used by the membership services.
 */

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CertificatesClient::class, TestCertificatesClient::class])
class TestCertificatesClient @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : CertificatesClient {
    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<CertificatesClient>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsServerCertificateChainAlias: String,
        p2pTlsClientCertificateChainAlias: String?,
        useClusterLevelTlsCertificateAndKey: Boolean,
        useClusterLevelSessionCertificateAndKey: Boolean,
        sessionKeyId: String?,
        sessionCertificateChainAlias: String?,
    ) {

    }


    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun importCertificates(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
        alias: String,
        certificates: String,
    ) {

    }

    override fun getCertificateAliases(usage: CertificateUsage, holdingIdentityId: ShortHash?): Collection<String> {
        return emptyList()
    }

    override fun retrieveCertificates(holdingIdentityId: ShortHash?, usage: CertificateUsage, alias: String): String? {
        return null
    }

    override fun allowCertificate(holdingIdentityId: ShortHash, subject: String) {

    }

    override fun disallowCertificate(holdingIdentityId: ShortHash, subject: String) {

    }

    override fun listAllowedCertificates(holdingIdentityId: ShortHash): Collection<String> {
        return emptyList()
    }
}