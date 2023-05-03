package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHashAlgoName
import net.corda.crypto.core.parseSecureHashHexString
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope
import java.io.InputStream

@Component(
    service = [DigestService::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class],
    scope = ServiceScope.PROTOTYPE
)
class DigestServiceImpl @Activate constructor(
    @Reference(service = PlatformDigestService::class)
    private val platformDigestService: PlatformDigestService,
    @Reference(
        service = DigestAlgorithmFactoryProvider::class,
        scope = PROTOTYPE_REQUIRED,
        cardinality = OPTIONAL
    )
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestService, UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {

    override fun hash(inputStream: InputStream, digestName: DigestAlgorithmName): SecureHash =
        try {
            platformDigestService.hash(inputStream, digestName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestName)
                ?.digest(inputStream)
                ?.let {
                    SecureHashImpl(digestName.name, it)
                } ?: throw e
        }

    override fun hash(bytes: ByteArray, digestName: DigestAlgorithmName): SecureHash =
        try {
            platformDigestService.hash(bytes, digestName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestName)
                ?.digest(bytes)
                ?.let {
                    SecureHashImpl(digestName.name, it)
                } ?: throw e
        }

    override fun parseSecureHash(algoNameAndHexString: String) =
        try {
            platformDigestService.parseSecureHash(algoNameAndHexString)
        } catch (e: IllegalArgumentException) {
            val digestName = parseSecureHashAlgoName(algoNameAndHexString)
            lookForCustomAlgorithm(DigestAlgorithmName(digestName))?.let {
                val digestHexStringLength = it.digestLength * 2
                val hexString = parseSecureHashHexString(algoNameAndHexString)
                require(digestHexStringLength == hexString.length) {
                    "Digest algorithm's: \"$digestName\" required hex string length: $digestHexStringLength " +
                            "is not met by hex string: \"$hexString\""
                }
                SecureHashImpl(digestName, ByteArrays.parseAsHex(hexString))
            } ?: throw e
        }

    override fun digestLength(digestName: DigestAlgorithmName): Int =
        try {
            platformDigestService.digestLength(digestName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestName)
                ?.digestLength
                ?: throw e
        }

    private fun lookForCustomAlgorithm(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm? =
        // Check any custom registered versions.
        customFactoriesProvider?.get(digestAlgorithmName.name)
            ?.instance

    override fun defaultDigestAlgorithm(): DigestAlgorithmName =
        platformDigestService.defaultDigestAlgorithm()

    override fun supportedDigestAlgorithms(): Set<DigestAlgorithmName> {
        return platformDigestService.supportedDigestAlgorithms() +
                (customFactoriesProvider?.let {
                    it.getAllDigestAlgorithmNames().map { algorithmName ->
                        DigestAlgorithmName(algorithmName)
                    }
                } ?: setOf())
    }
}