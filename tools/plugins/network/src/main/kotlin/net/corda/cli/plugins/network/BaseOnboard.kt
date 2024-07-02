package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.cli.plugins.typeconverter.X500NameConverter
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.CryptoConsts.Categories.KeyCategory
import net.corda.crypto.core.ShortHash
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.certificates.CertificateUsage
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.ConfigSchemaVersion
import net.corda.restclient.generated.models.HostedIdentitySessionKeyAndCertificate
import net.corda.restclient.generated.models.HostedIdentitySetupRequest
import net.corda.restclient.generated.models.JsonCreateVirtualNodeRequest
import net.corda.restclient.generated.models.KeyPairIdentifier
import net.corda.restclient.generated.models.MemberRegistrationRequest
import net.corda.restclient.generated.models.UpdateConfigParameters
import net.corda.schema.configuration.ConfigKeys.RootConfigKey
import net.corda.sdk.config.ClusterConfig
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.Keys
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.RegistrationsLookup
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.network.writeToTempFile
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.net.URI
import java.security.KeyStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
abstract class BaseOnboard : Runnable, RestCommand() {
    private companion object {
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
        const val SIGNING_KEY_ALIAS = "signing key 1"
        const val SIGNING_KEY_STORE_PASSWORD = "keystore password"
        const val GRADLE_PLUGIN_DEFAULT_KEY_ALIAS = "gradle-plugin-default-key"
    }

    @Parameters(
        description = ["The X500 name of the virtual node."],
        arity = "1",
        index = "0",
        converter = [X500NameConverter::class]
    )
    lateinit var name: MemberX500Name

    @Option(
        names = ["--mutual-tls", "-m"],
        description = ["Enable mutual TLS"],
    )
    var mtls: Boolean = false

    @Option(
        names = ["--tls-certificate-subject", "-c"],
        description = [
            "The TLS certificate subject. Leave empty to use random certificate subject." +
                "Will only be used on the first onboard to the cluster.",
        ],
        converter = [X500NameConverter::class]
    )
    var tlsCertificateSubject: MemberX500Name? = null

    @Option(
        names = ["--p2p-gateway-url", "-g"],
        description = ["P2P Gateway URL. Multiple URLs may be provided. Defaults to https://localhost:8080."],
    )
    var p2pGatewayUrls: List<String> = listOf("https://localhost:8080")

    protected val json by lazy {
        ObjectMapper()
    }

    private val caHome: File = File(File(File(System.getProperty("user.home")), ".corda"), "ca")
    protected val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    internal class OnboardException(message: String) : Exception(message)

    protected fun uploadCpi(cpi: File): Checksum {
        val uploadId = with(CpiUploader(restClient).uploadCPI(cpi)) {
            RequestId(this.id)
        }
        return checkCpiStatus(uploadId)
    }

    private fun getLongerWait(): Duration {
        return if (waitDurationSeconds.seconds > 30.seconds) {
            waitDurationSeconds.seconds
        } else {
            30.seconds
        }
    }

    private fun checkCpiStatus(id: RequestId): Checksum {
        return CpiUploader(restClient).cpiChecksum(uploadRequestId = id, wait = waitDurationSeconds.seconds)
    }

    protected abstract val cpiFileChecksum: Checksum

    protected abstract val memberRegistrationRequest: MemberRegistrationRequest

    protected val holdingId: ShortHash by lazy {
        val request = JsonCreateVirtualNodeRequest(
            x500Name = name.toString(),
            cpiFileChecksum = cpiFileChecksum.value,
            vaultDdlConnection = null,
            vaultDmlConnection = null,
            cryptoDdlConnection = null,
            cryptoDmlConnection = null,
            uniquenessDdlConnection = null,
            uniquenessDmlConnection = null,
        )
        // Suspected flakiness here - https://r3-cev.atlassian.net/browse/CORE-20760
        Thread.sleep(3000)
        val longerWait = getLongerWait()
        println("Creating Virtual Node: ${request.x500Name}")
        val shortHashId = VirtualNode(restClient).createAndWaitForActive(request, longerWait)
        println("Holding identity short hash of '$name' is: '$shortHashId'")
        shortHashId
    }

    protected fun assignSoftHsmAndGenerateKey(category: KeyCategory): KeyPairIdentifier {
        return Keys(restClient).assignSoftHsmAndGenerateKey(
            holdingIdentityShortHash = holdingId,
            category = category,
            wait = waitDurationSeconds.seconds
        )
    }

    protected val sessionKeyId by lazy {
        assignSoftHsmAndGenerateKey(KeyCategory.SESSION_INIT_KEY)
    }
    protected val ecdhKeyId by lazy {
        assignSoftHsmAndGenerateKey(KeyCategory.PRE_AUTH_KEY)
    }
    protected val certificateSubject by lazy {
        tlsCertificateSubject ?: MemberX500Name.parse("O=P2P Certificate, OU=$p2pHosts, L=London, C=GB")
    }

    private val p2pHosts = extractHostsFromUrls(p2pGatewayUrls)

    private fun extractHostsFromUrls(urls: List<String>): List<String> {
        return urls.map { extractHostFromUrl(it) }.distinct()
    }

    private fun extractHostFromUrl(url: String): String {
        return URI.create(url).host ?: throw IllegalArgumentException("Invalid URL: $url")
    }

    protected val ca by lazy {
        caHome.parentFile.mkdirs()
        CertificateAuthorityFactory
            .createFileSystemLocalAuthority(
                RSA_TEMPLATE.toFactoryDefinitions(),
                caHome,
            ).also { it.save() }
    }

    protected fun createTlsKeyIdNeeded() {
        val keys = Keys(restClient)
        val hasKeys = keys.hasTlsKey(wait = waitDurationSeconds.seconds)

        if (hasKeys) return

        println("Creating TLS key.")

        val tlsKeyId = keys.generateTlsKey()

        val clientCertificates = ClientCertificates(restClient)
        val csrCertRequest = clientCertificates.generateP2pCsr(
            tlsKey = tlsKeyId,
            subjectX500Name = certificateSubject,
            p2pHostNames = p2pHosts
        )

        ca.signCsr(csrCertRequest).toPem().writeToTempFile("CSR.csr").also {
            clientCertificates.uploadTlsCertificate(
                certificateFile = it,
                alias = P2P_TLS_CERTIFICATE_ALIAS,
                wait = waitDurationSeconds.seconds
            )
        }
    }

    protected fun setupNetwork() {
        val request = HostedIdentitySetupRequest(
            p2pTlsCertificateChainAlias = P2P_TLS_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = true,
            sessionKeysAndCertificates = listOf(
                HostedIdentitySessionKeyAndCertificate(
                    sessionKeyId = sessionKeyId.id,
                    preferred = true,
                ),
            ),
        )
        RegistrationRequester(restClient).configureAsNetworkParticipant(
            request = request,
            holdingId = holdingId,
            wait = waitDurationSeconds.seconds
        )
    }

    protected fun register(waitForFinalStatus: Boolean = true) {
        // Invoke to instantiate lazy fields so that print line happens after the setup
        val registrationPayload = memberRegistrationRequest
        val shortHashHoldingId = holdingId
        println("Registering Virtual Node into the network.")

        val response = RegistrationRequester(restClient).requestRegistration(
            memberRegistrationRequest = registrationPayload,
            holdingId = shortHashHoldingId
        )
        val registrationId = RequestId(response.registrationId)
        val submissionStatus = response.registrationStatus

        if (submissionStatus != "SUBMITTED") {
            throw OnboardException("Could not submit registration request: ${response.memberInfoSubmitted}")
        }

        println("Registration ID for '$name' is '${registrationId.value}'")

        // Registrations can take longer than the default 10 seconds, wait for minimum of 30
        val longerWaitValue = getLongerWait()
        if (waitForFinalStatus) {
            RegistrationsLookup(restClient).waitForRegistrationApproval(
                registrationId = registrationId,
                holdingId = holdingId,
                wait = longerWaitValue
            )
        }
    }

    protected fun configureGateway() {
        val clusterConfig = ClusterConfig(restClient)
        val currentConfig = clusterConfig.getCurrentConfig(configSection = RootConfigKey.P2P_GATEWAY, wait = waitDurationSeconds.seconds)
        val rawConfig = currentConfig.configWithDefaults
        val rawConfigJson = json.readTree(rawConfig)
        val sslConfig = rawConfigJson["sslConfig"]
        val currentMode = sslConfig["revocationCheck"]?.get("mode")?.asText()
        val currentTlsType = sslConfig["tlsType"]?.asText()
        val tlsType = if (mtls) {
            "MUTUAL"
        } else {
            "ONE_WAY"
        }

        if ((currentMode != "OFF") || (currentTlsType != tlsType)) {
            val newConfig = json.createObjectNode()
            newConfig.set<ObjectNode>(
                "sslConfig",
                json.createObjectNode()
                    .put("tlsType", tlsType.uppercase())
                    .set<ObjectNode>(
                        "revocationCheck",
                        json.createObjectNode().put("mode", "OFF"),
                    ),
            )
            val payload = UpdateConfigParameters(
                section = "corda.p2p.gateway",
                version = currentConfig.version,
                config = newConfig,
                schemaVersion = ConfigSchemaVersion(major = currentConfig.schemaVersion.major, minor = currentConfig.schemaVersion.minor),
            )
            println("Configuring Corda.")
            clusterConfig.updateConfig(updateConfig = payload, wait = waitDurationSeconds.seconds)
        }
    }

    private val keyStoreFile by lazy {
        File(File(File(System.getProperty("user.home")), ".corda"), "signingkeys.pfx")
    }

    protected fun createDefaultSingingOptions(): SigningOptions {
        val options = SigningOptions()
        options.keyAlias = SIGNING_KEY_ALIAS
        options.keyStorePass = SIGNING_KEY_STORE_PASSWORD
        options.keyStoreFileName = keyStoreFile.absolutePath
        val keyStoreHelper = KeyStoreHelper()
        if (!keyStoreFile.canRead()) {
            keyStoreHelper.generateKeyStore(
                keyStoreFile = keyStoreFile,
                alias = SIGNING_KEY_ALIAS,
                password = SIGNING_KEY_STORE_PASSWORD
            )
            val defaultGradleCert = keyStoreHelper.getDefaultGradleCertificateStream()
            KeyStoreHelper().importCertificateIntoKeyStore(
                keyStoreFile = keyStoreFile,
                keyStorePassword = SIGNING_KEY_STORE_PASSWORD,
                certificateInputStream = defaultGradleCert,
                certificateAlias = GRADLE_PLUGIN_DEFAULT_KEY_ALIAS,
                certificateFactoryType = "X.509"
            )
        }

        return options
    }

    protected fun uploadSigningCertificates() {
        println("Uploading code-signer certificates.")
        val keyStore = KeyStore.getInstance(
            keyStoreFile,
            SIGNING_KEY_STORE_PASSWORD.toCharArray(),
        )
        keyStore.getCertificate(GRADLE_PLUGIN_DEFAULT_KEY_ALIAS)?.toPem()?.writeToTempFile("defaultGradleFile.pem")?.also {
            ClientCertificates(restClient).uploadClusterCertificate(
                certificateFile = it,
                usage = CertificateUsage.CODE_SIGNER,
                alias = GRADLE_PLUGIN_DEFAULT_KEY_ALIAS,
                wait = waitDurationSeconds.seconds
            )
        }

        keyStore.getCertificate(SIGNING_KEY_ALIAS)?.toPem()?.writeToTempFile("signingKey.pem")?.also {
            ClientCertificates(restClient).uploadClusterCertificate(
                certificateFile = it,
                usage = CertificateUsage.CODE_SIGNER,
                alias = "signingkey1-2022",
                wait = waitDurationSeconds.seconds
            )
        }
    }
}
