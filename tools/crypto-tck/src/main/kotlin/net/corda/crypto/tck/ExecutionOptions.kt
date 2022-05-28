package net.corda.crypto.tck

import net.corda.v5.crypto.SignatureSpec
import java.nio.file.Path
import java.time.Duration

/**
 * Options governing the execution of the compliance tests.
 *
 * @property serviceName which implementation of CryptoService to execute, must match the CryptoServiceProvider.name property.
 * @property serviceConfig the CryptoService implementation service configuration.
 * @property signatureSpecs map of the SignatureSpec(s) which should be used for signing when running the tests.
 * @property testResultsDirectory path where to output the test results.
 * @property concurrency number of threads to execute, together with the number of SignatureSpecs defined in
 * [signatureSpecs] has direct impact on number of generated keys. The recommended value is 20, the minimum is 4.
 * @property sessionComplianceSpec the spec that should be used for the session inactivity test suite.
 * @property sessionComplianceTimeout the session timeout to test, must exceed the login session timeout.
 * @property retries number of retries when the call to the HSM fails.
 * @property timeout the HSM call execution timeout for each try.
 * @property tests which test suites to execute.
 */
@Suppress("LongParameterList")
class ExecutionOptions(
    val serviceName: String,
    val serviceConfig: Any,
    val signatureSpecs: Map<String, List<SignatureSpec>>,
    val testResultsDirectory: Path,
    val concurrency: Int = 20,
    val sessionComplianceSpec: Pair<String, SignatureSpec>? = null,
    val sessionComplianceTimeout: Duration = Duration.ofMinutes(20),
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
        require(concurrency >= 4) {
            "The minimum value of concurrency is 4, you submitted $concurrency"
        }
        if (tests.contains(ComplianceTestType.SESSION_INACTIVITY)) {
            require(sessionComplianceSpec != null) {
                "Please specify the ${::sessionComplianceSpec.name}"
            }
        }
        if(tests.contains(ComplianceTestType.CRYPTO_SERVICE)) {
            require(signatureSpecs.isNotEmpty()) {
                "Please specify at least one signature spec mapping."
            }
            signatureSpecs.forEach {
                require(it.value.isNotEmpty()) {
                    "There must be at least one signatures spec for ${it.key}"
                }
            }
        }
    }
}