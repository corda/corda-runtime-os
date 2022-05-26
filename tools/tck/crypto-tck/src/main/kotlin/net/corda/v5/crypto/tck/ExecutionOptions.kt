package net.corda.v5.crypto.tck

import java.nio.file.Path

class ExecutionOptions(
    val serviceName: String,
    val testResultsDirectory: Path,
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