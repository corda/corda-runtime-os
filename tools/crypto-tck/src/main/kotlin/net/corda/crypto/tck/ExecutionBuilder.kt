package net.corda.crypto.tck

import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import java.nio.file.Path
import java.time.Duration

/**
 * [ExecutionOptions] builder.
 *
 * @property tck the instance of [CryptoTCK]
 * @property serviceName which implementation of CryptoService to execute, must match the CryptoServiceProvider.name property.
 * @property serviceConfig the CryptoService implementation service configuration.
 */
class ExecutionBuilder(
    private val tck: CryptoTCK,
    private val serviceName: String,
    private val serviceConfig: Any
) {
    init {
        require(serviceName.isNotBlank()) {
            "The Service Name must not be blank."
        }
    }

    private var testResultsDirectory: Path = Path.of("", serviceName).toAbsolutePath()

    private var concurrency: Int = 20

    private var sessionComplianceSpec: Pair<String, SignatureSpec>? = null

    private var sessionComplianceTimeout: Duration = Duration.ofMinutes(20)

    private var retries: Int = 2

    private var timeout: Duration = Duration.ofSeconds(10)

    private var tests = listOf<ComplianceTestType>()

    private var secrets: ConfigurationSecrets = object : ConfigurationSecrets {
        @Suppress("UNCHECKED_CAST")
        override fun getSecret(secret: Map<String, Any>): String =
            ((secret["configSecret"] as? Map<String, Any>)?.get("encryptedSecret") as? String)
                ?: throw IllegalArgumentException("The map doesn't conform to the secret's structure.")
    }

    /**
     * Sets the path where to output the test results. The default value is `Path.of("", serviceName).toAbsolutePath()`
     */
    fun withResultsDirectory(directory: Path): ExecutionBuilder {
        testResultsDirectory = directory
        return this
    }

    /**
     * Sets the custom secret's implementation.
     */
    fun withSecrets(secrets: ConfigurationSecrets): ExecutionBuilder {
        this.secrets = secrets
        return this
    }

    /**
     * Sets number of threads to use for Crypto Service compliance test suite. Default value is 20.
     */
    fun withConcurrency(threads: Int): ExecutionBuilder {
        concurrency = threads
        return this
    }

    /**
     * Sets the spec that should be used for the session inactivity test suite.
     */
    fun withSessionSpec(codeName: String, signatureSpec: SignatureSpec): ExecutionBuilder {
        tck.schemeMetadata.findKeyScheme(codeName)
        sessionComplianceSpec = Pair(codeName, signatureSpec)
        return this
    }

    /**
     * Sets the session timeout to test, must exceed the login session timeout. Default value is 20 minutes.
     */
    fun withSessionTimeout(timeout: Duration): ExecutionBuilder {
        sessionComplianceTimeout = timeout
        return this
    }

    /**
     * Sets number of retries when the call to the HSM fails. Default value is 2.
     */
    fun withServiceRetries(retries: Int): ExecutionBuilder {
        this.retries = retries
        return this
    }

    /**
     * Sets the HSM call execution timeout for each try. Default value is 10 seconds.
     */
    fun withServiceTimeout(timeout: Duration): ExecutionBuilder {
        this.timeout = timeout
        return this
    }

    /**
     * Sets which test suites to execute.
     */
    fun withTestSuites(vararg tests: ComplianceTestType): ExecutionBuilder {
        this.tests = tests.toList()
        return this
    }

    /**
     * Builds the [ExecutionOptions].
     *
     * If the [sessionComplianceSpec] wasn't defined and the SESSION_INACTIVITY is specified then the builder will try
     * to specify supported by [CipherSchemeMetadata] key scheme and spec in next order [ECDSA_SECP256R1_CODE_NAME],
     * [ECDSA_SECP256K1_CODE_NAME], [RSA_CODE_NAME]
     *
     * If the [tests] asn;t defined then it'll be set to CRYPTO_SERVICE by default.
     *
     * @return [Runnable] which can be used to start the configured test suites.
     *
     * @throws IllegalArgumentException if the [signatureSpecs] is empty or if session spec is not defined and none
     * of the default is supported by the [CipherSchemeMetadata].
     */
    fun build(): Runnable {
        val tests = this.tests.ifEmpty {
            listOf(ComplianceTestType.CRYPTO_SERVICE)
        }
        return RunnableImpl(
            tck = tck,
            options = ExecutionOptions(
                serviceName = serviceName,
                serviceConfig = serviceConfig,
                testResultsDirectory = testResultsDirectory,
                concurrency = concurrency,
                sessionComplianceSpec = sessionComplianceSpec
                    ?: if (tests.contains(ComplianceTestType.SESSION_INACTIVITY)) {
                        defaultSessionComplianceSpec()
                    } else {
                        null
                    },
                sessionComplianceTimeout = sessionComplianceTimeout,
                maxAttempts = retries,
                attemptTimeout = timeout,
                secrets = secrets,
                tests = tests
            )
        )
    }

    private fun defaultSessionComplianceSpec() = if (ifSupported(ECDSA_SECP256R1_CODE_NAME)) {
        Pair(ECDSA_SECP256R1_CODE_NAME, SignatureSpec.ECDSA_SHA256)
    } else if (ifSupported(ECDSA_SECP256K1_CODE_NAME)) {
        Pair(ECDSA_SECP256K1_CODE_NAME, SignatureSpec.ECDSA_SHA256)
    } else if (ifSupported(RSA_CODE_NAME)) {
        Pair(RSA_CODE_NAME, SignatureSpec.RSA_SHA256)
    } else {
        throw IllegalArgumentException(
            "Please specify the session spec explicitly as none of the default schemes are supported."
        )
    }

    private fun ifSupported(codeName: String) =
        tck.schemeMetadata.schemes.any { it.codeName == codeName }

    /**
     * TCK suite runner.
     */
    interface Runnable {
        /**
         * Test options.
         */
        val options: ExecutionOptions

        /**
         * Runs the TCK suit with the defined [options]
         */
        fun run()
    }

    private class RunnableImpl(
        private val tck: CryptoTCK,
        override val options: ExecutionOptions
    ) : Runnable {
        override fun run() {
            tck.run(options)
        }
    }
}