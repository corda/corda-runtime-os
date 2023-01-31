package net.corda.crypto.tck

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.nio.file.Path
import java.time.Duration

/**
 * Options governing the execution of the compliance tests.
 *
 * @property serviceName which implementation of CryptoService to execute, must match the CryptoServiceProvider.name property.
 * @property serviceConfig the CryptoService implementation service configuration.
 * @property testResultsDirectory path where to output the test results.
 * @property concurrency number of threads to use for Crypto Service compliance test suite,
 * together with the number of SignatureSpecs supported by the service has direct impact on number of generated keys.
 * The recommended value is 20, the minimum is 4.
 * @property sessionComplianceSpec the spec that should be used for the session inactivity test suite.
 * @property sessionComplianceTimeout the session timeout to test, must exceed the login session timeout.
 * @property maxAttempts number of retries when the call to the HSM fails.
 * @property attemptTimeout the HSM call execution timeout for each try.
 * @property secrets the service which resolves the secrets in the configuration, as the Cord configuration subsystem
 * is not available for the TCK, the tests must supply it, the default implementation in the TCK will return the
 * value as it's in the "encryptedSecret" (see bellow) or throw [IllegalArgumentException] if the secret's structure
 * doesn't conform.
 * @property tests which test suites to execute.
 *
 * "passphrase": {
 *      "configSecret": {
 *          "encryptedSecret": "<encrypted-value>"
 *      }
 *  }
 */
@Suppress("LongParameterList")
class ExecutionOptions(
    val serviceName: String,
    val serviceConfig: Any,
    val testResultsDirectory: Path,
    val concurrency: Int = 20,
    val sessionComplianceSpec: Pair<String, SignatureSpec>? = null,
    val sessionComplianceTimeout: Duration = Duration.ofMinutes(20),
    val maxAttempts: Int = 2,
    val attemptTimeout: Duration = Duration.ofSeconds(10),
    val secrets: ConfigurationSecrets,
    val tests: List<ComplianceTestType> = listOf(
        ComplianceTestType.CRYPTO_SERVICE
    )
) {
    internal var usedSignatureSpecs: Map<KeyScheme, List<SignatureSpec>> = emptyMap()

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
    }
}