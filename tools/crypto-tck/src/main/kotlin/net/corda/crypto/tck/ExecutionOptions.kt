package net.corda.crypto.tck

import net.corda.v5.crypto.SignatureSpec
import java.nio.file.Path

class ExecutionOptions(
    val serviceName: String,
    val testResultsDirectory: Path,
    val schemeSpec: Map<String, List<SignatureSpec>>,
    val tests: List<ComplianceTestType> = listOf(
        ComplianceTestType.CRYPTO_SERVICE
    )
) {
    init {
        require(serviceName.isNotBlank()) {
            "The service name must not be blank."
        }
        require(tests.isNotEmpty()) {
            "Define at least compliance test suite."
        }
    }
}