package net.corda.libs.packaging.tests.legacy

import net.corda.v5.crypto.extensions.DigestAlgorithm
import java.util.function.Supplier

// WARNING - "legacy" corda5 code *only* used to make tests pass.
class DigestSupplier(private val algorithm: String) : Supplier<DigestAlgorithm> {
    override fun get(): DigestAlgorithm = DigestAlgorithmFactoryImpl.getInstance(algorithm)
    val digestLength: Int = get().digestLength
}
