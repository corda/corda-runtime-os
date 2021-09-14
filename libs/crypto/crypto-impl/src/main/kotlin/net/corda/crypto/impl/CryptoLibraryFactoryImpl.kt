package net.corda.crypto.impl

import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.impl.rpc.FreshKeySigningServiceClient
import net.corda.crypto.impl.rpc.SigningServiceClient
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.osgi.service.component.annotations.Activate
import java.time.Duration

class CryptoLibraryFactoryImpl @Activate constructor(
    private val memberId: String,
    private val requestingComponent: String,
    private val clientTimeout: Duration,
    private val clientRetries: Long,
    private val cipherSuiteFactory: CipherSuiteFactory,
    private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>,
    private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>
) : CryptoLibraryFactory {

    private val schemeMetadata = cipherSuiteFactory.getSchemeMap()

    override fun getSignatureVerificationService(): SignatureVerificationService =
        cipherSuiteFactory.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        cipherSuiteFactory.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        cipherSuiteFactory.getSchemeMap()

    override fun getDigestService(): DigestService =
        cipherSuiteFactory.getDigestService()

    override fun getFreshKeySigningService(): FreshKeySigningService = FreshKeySigningServiceClient(
        memberId = memberId,
        requestingComponent = requestingComponent,
        clientTimeout = clientTimeout,
        clientRetries = clientRetries,
        schemeMetadata = schemeMetadata,
        sender = freshKeysServiceSender
    )

    override fun getSigningService(category: String): SigningService = SigningServiceClient(
        memberId = memberId,
        requestingComponent = requestingComponent,
        category = category,
        clientTimeout = clientTimeout,
        clientRetries = clientRetries,
        schemeMetadata = schemeMetadata,
        sender = signingServiceSender
    )
}