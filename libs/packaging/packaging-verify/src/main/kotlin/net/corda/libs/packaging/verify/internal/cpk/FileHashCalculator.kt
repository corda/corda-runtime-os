package net.corda.libs.packaging.verify.internal.cpk

import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/**
 * Calculates file's hash.
 * Calculated hashes are stored so that multiple requests for specific algorithm don't require recalculation.
 * */
internal class FileHashCalculator(private val inputStreamSupplier: () -> InputStream) {
    private val hashes = mutableMapOf<String, SecureHash>()

    fun hash(algorithm: String): SecureHash =
        hashes.getOrPut(algorithm) {
            net.corda.libs.packaging.verify.internal.hash(
                inputStreamSupplier.invoke(),
                algorithm
            )
        }
}