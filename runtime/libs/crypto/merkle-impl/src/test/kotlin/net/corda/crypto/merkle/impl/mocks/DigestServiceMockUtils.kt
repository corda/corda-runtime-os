@file:JvmName("DigestServiceMockUtils")

package net.corda.crypto.merkle.impl.mocks

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val hashConstants: ConcurrentMap<String, HashConstants> = ConcurrentHashMap()

fun DigestService.getZeroHash(digestAlgorithmName: DigestAlgorithmName): SecureHash {
    return getConstantsFor(digestAlgorithmName).zero
}

private fun DigestService.getConstantsFor(digestAlgorithmName: DigestAlgorithmName): HashConstants {
    val algorithm = digestAlgorithmName.name
    return hashConstants.getOrPut(algorithm) {
        val digestLength = digestLength(digestAlgorithmName)
        HashConstants(
            zero = SecureHashImpl(algorithm, ByteArray(digestLength) { 0.toByte() })
        )
    }
}

private class HashConstants(val zero: SecureHash)