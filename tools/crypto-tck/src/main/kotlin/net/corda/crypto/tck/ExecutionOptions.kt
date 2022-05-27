package net.corda.crypto.tck

import net.corda.v5.crypto.SignatureSpec
import java.nio.file.Path
import java.time.Duration

@Suppress("LongParameterList")
class ExecutionOptions(
    val serviceName: String,
    val serviceConfig: Any,
    val signatureSpecs: Map<String, List<SignatureSpec>>,
    val testResultsDirectory: Path,
    val retries: Int = 2,
    val timeout: Duration = Duration.ofSeconds(10),
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