package net.corda.crypto.tck

import net.corda.crypto.cipher.suite.CipherSchemeMetadata

/**
 * Crypto Technical Compliance Kit provider. It's an OSGi component.
 * Use the junit capability to run OSGi tests.
 *
 * If the service supports the key deletion extension then the tests will do the best to delete the generated keys.
 *
 * Example (Kotlin):
 *  @ExtendWith(ServiceExtension::class)
 *  class CryptoTCKTests {
 *      companion object {
 *          @InjectService(timeout = 5000L)
 *          lateinit var tck: CryptoTCK
 *  }
 *
 *  @Test
 *  fun `TCK should be able to test AllWrappedKeysHSM`() {
 *      tck.run(
 *          ExecutionOptions(
 *              serviceName = AllWrappedKeysHSMProvider.NAME,
 *              serviceConfig = AllWrappedKeysHSMProvider.Configuration("corda"),
 *              testResultsDirectory = Path.of("", AllWrappedKeysHSMProvider::class.simpleName).toAbsolutePath(),
 *              tests = listOf(
 *                  ComplianceTestType.CRYPTO_SERVICE,
 *                  ComplianceTestType.SESSION_INACTIVITY,
 *              ),
 *              sessionComplianceSpec = Pair(EDDSA_ED25519_CODE_NAME, SignatureSpec.EDDSA_ED25519),
 *              sessionComplianceTimeout = Duration.ofMinutes(30),
 *              signatureSpecs = mapOf(
 *                  RSA_CODE_NAME to listOf(
 *                      SignatureSpec.RSA_SHA512,
 *                      SignatureSpec.RSASSA_PSS_SHA384
 *                  ),
 *                  ECDSA_SECP256R1_CODE_NAME to listOf(
 *                      SignatureSpec.ECDSA_SHA384
 *                  ),

 *              )
 *          )
 *      )
 *  }
 */
interface CryptoTCK {
    /**
     * The service that can be used to introspect what key schemes are supported and some other aspects of the
     * current cipher suite.
     */
    val schemeMetadata: CipherSchemeMetadata

    /**
     * returns a new instance of [ExecutionBuilder] to build the test options and run the tests.
     */
    fun builder(serviceName: String, serviceConfig: Any): ExecutionBuilder

    /**
     * Executes the compliance tests.
     */
    fun run(options: ExecutionOptions)
}