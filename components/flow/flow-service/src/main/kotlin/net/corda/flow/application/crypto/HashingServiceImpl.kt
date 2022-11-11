package net.corda.flow.application.crypto

import java.io.InputStream
import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.crypto.HashingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [DigestService::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class],
    scope = ServiceScope.PROTOTYPE
)
class HashingServiceImpl(
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(
        service = DigestAlgorithmFactoryProvider::class,
        scope = PROTOTYPE_REQUIRED,
        cardinality = OPTIONAL
    )
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : HashingService, UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {

    override fun hash(inputStream: InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash =
        try {
            digestService.hash(inputStream, digestAlgorithmName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestAlgorithmName)
                ?.digest(inputStream)
                ?.let {
                    SecureHash(digestAlgorithmName.name, it)
                }
                ?: throw e
        }

    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash =
        try {
            digestService.hash(bytes, digestAlgorithmName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestAlgorithmName)
                ?.digest(bytes)
                ?.let {
                    SecureHash(digestAlgorithmName.name, it)
                }
                ?: throw e
        }

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
        try {
            digestService.digestLength(digestAlgorithmName)
        } catch (e: IllegalArgumentException) {
            lookForCustomAlgorithm(digestAlgorithmName)
                ?.digestLength
                ?: throw e
        }

    private fun lookForCustomAlgorithm(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm? =
        // Check any custom registered versions.
        customFactoriesProvider?.get(digestAlgorithmName.name)
            ?.getInstance()
}