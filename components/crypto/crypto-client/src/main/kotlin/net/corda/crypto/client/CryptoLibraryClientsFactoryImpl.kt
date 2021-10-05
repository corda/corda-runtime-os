package net.corda.crypto.client

import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.client.rpc.FreshKeySigningServiceClient
import net.corda.crypto.client.rpc.SigningServiceClient
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.time.Duration

@Suppress("LongParameterList")
class CryptoLibraryClientsFactoryImpl(
    private val memberId: String,
    private val requestingComponent: String,
    private val clientTimeout: Duration,
    private val clientRetries: Long,
    private val schemeMetadata: CipherSchemeMetadata,
    private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>,
    private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>
) : CryptoLibraryClientsFactory {

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