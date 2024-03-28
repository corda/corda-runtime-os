@file:Suppress("DEPRECATION")

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
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySessionKeyAndCertificate
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.KeyPairIdentifier
import net.corda.rest.json.serialization.JsonObjectAsString
import net.corda.schema.configuration.ConfigKeys.RootConfigKey
import net.corda.sdk.config.ClusterConfig
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.Keys
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.RegistrationsLookup
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.sdk.rest.RestClientUtils.createRestClient
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

    internal class OnboardException(message: String) : Exception(message)

    protected fun uploadCpi(cpi: File, cpiName: String): String {
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )

        // Cpi upload can take longer than the default 10 seconds, wait for minimum of 30
        val longerWaitValue = getLongerWait()
        val uploadId = CpiUploader().uploadCPI(restClient, cpi.inputStream(), cpiName, longerWaitValue).id
        return checkCpiStatus(uploadId)
    }

    private fun getLongerWait(): Duration {
        return if (waitDurationSeconds.seconds > 30.seconds) {
            waitDurationSeconds.seconds
        } else {
            30.seconds
        }
    }

    private fun checkCpiStatus(id: String): String {
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        return CpiUploader().cpiChecksum(restClient = restClient, uploadRequestId = id, wait = waitDurationSeconds.seconds)
    }

    protected abstract val cpiFileChecksum: String

    protected abstract val memberRegistrationRequest: MemberRegistrationRequest

    protected val holdingId: ShortHash by lazy {
        val restClient = createRestClient(
            VirtualNodeRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val request = JsonCreateVirtualNodeRequest(
            x500Name = name.toString(),
            cpiFileChecksum = cpiFileChecksum,
            vaultDdlConnection = null,
            vaultDmlConnection = null,
            cryptoDdlConnection = null,
            cryptoDmlConnection = null,
            uniquenessDdlConnection = null,
            uniquenessDmlConnection = null,
        )
        val longerWait = getLongerWait()
        val shortHashId = VirtualNode().createAndWaitForActive(restClient, request, longerWait)
        println("Holding identity short hash of '$name' is: '$shortHashId'")
        shortHashId
    }

    protected fun assignSoftHsmAndGenerateKey(category: KeyCategory): KeyPairIdentifier {
        val hsmRestClient = createRestClient(
            HsmRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val keyRestClient = createRestClient(
            KeysRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        return Keys().assignSoftHsmAndGenerateKey(
            hsmRestClient = hsmRestClient,
            keysRestClient = keyRestClient,
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

    private fun extractHostsFromUrls(urls: List<String>): List<MemberX500Name> {
        return urls.map { extractHostFromUrl(it) }.distinct()
    }

    private fun extractHostFromUrl(url: String): MemberX500Name {
        return URI.create(url).host?.let { MemberX500Name.parse(it) } ?: throw IllegalArgumentException("Invalid URL: $url")
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
        val keyRestClient = createRestClient(
            KeysRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val keys = Keys()
        val hasKeys = keys.hasTlsKey(restClient = keyRestClient, wait = waitDurationSeconds.seconds)

        if (hasKeys) return

        val tlsKeyId = keys.generateTlsKey(restClient = keyRestClient, wait = waitDurationSeconds.seconds)

        val certificateRestClient = createRestClient(
            CertificatesRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val clientCertificates = ClientCertificates()

        val csrCertRequest = clientCertificates.generateP2pCsr(
            certificateRestClient,
            tlsKeyId,
            certificateSubject,
            p2pHosts,
            waitDurationSeconds.seconds
        )
        val certificate = ca.signCsr(csrCertRequest).toPem().byteInputStream()
        clientCertificates.uploadTlsCertificate(certificateRestClient, certificate, P2P_TLS_CERTIFICATE_ALIAS, waitDurationSeconds.seconds)
    }

    protected fun setupNetwork() {
        val restClient = createRestClient(
            NetworkRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )

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

        RegistrationRequester().configureAsNetworkParticipant(
            restClient = restClient,
            request = request,
            holdingId = holdingId,
            wait = waitDurationSeconds.seconds
        )
    }

    protected fun register(waitForFinalStatus: Boolean = true) {
        val restClient = createRestClient(
            MemberRegistrationRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val response = RegistrationRequester().requestRegistration(
            restClient = restClient,
            memberRegistrationRequest = memberRegistrationRequest,
            holdingId = holdingId
        )
        val registrationId = response.registrationId
        val submissionStatus = response.registrationStatus

        if (submissionStatus != "SUBMITTED") {
            throw OnboardException("Could not submit registration request: ${response.memberInfoSubmitted}")
        }

        println("Registration ID for '$name' is '$registrationId'")

        // Registrations can take longer than the default 10 seconds, wait for minimum of 30
        val longerWaitValue = getLongerWait()
        if (waitForFinalStatus) {
            RegistrationsLookup().waitForRegistrationApproval(
                restClient = restClient,
                registrationId = registrationId,
                holdingId = holdingId,
                wait = longerWaitValue
            )
        }
    }

    protected fun configureGateway() {
        val restClient = createRestClient(
            ConfigRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val clusterConfig = ClusterConfig()
        val currentConfig = clusterConfig.getCurrentConfig(restClient, RootConfigKey.P2P_GATEWAY, waitDurationSeconds.seconds)
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
            val objectMapper = ObjectMapper()
            val newConfig = objectMapper.createObjectNode()
            newConfig.set<ObjectNode>(
                "sslConfig",
                objectMapper.createObjectNode()
                    .put("tlsType", tlsType.uppercase())
                    .set<ObjectNode>(
                        "revocationCheck",
                        json.createObjectNode().put("mode", "OFF"),
                    ),
            )
            val payload = UpdateConfigParameters(
                section = "corda.p2p.gateway",
                version = currentConfig.version,
                config = JsonObjectAsString(objectMapper.writeValueAsString(newConfig)),
                schemaVersion = ConfigSchemaVersion(major = currentConfig.schemaVersion.major, minor = currentConfig.schemaVersion.minor),
            )
            clusterConfig.updateConfig(restClient = restClient, updateConfig = payload, wait = waitDurationSeconds.seconds)
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
        val restClient = createRestClient(
            CertificatesRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )

        val keyStore = KeyStore.getInstance(
            keyStoreFile,
            SIGNING_KEY_STORE_PASSWORD.toCharArray(),
        )
        keyStore.getCertificate(GRADLE_PLUGIN_DEFAULT_KEY_ALIAS)
            ?.toPem()
            ?.byteInputStream()
            ?.use { certificate ->
                ClientCertificates().uploadCertificate(
                    restClient = restClient,
                    certificate = certificate,
                    usage = CertificateUsage.CODE_SIGNER,
                    alias = GRADLE_PLUGIN_DEFAULT_KEY_ALIAS,
                    wait = waitDurationSeconds.seconds
                )
            }
        keyStore.getCertificate(SIGNING_KEY_ALIAS)
            ?.toPem()
            ?.byteInputStream()
            ?.use { certificate ->
                ClientCertificates().uploadCertificate(
                    restClient = restClient,
                    certificate = certificate,
                    usage = CertificateUsage.CODE_SIGNER,
                    alias = "signingkey1-2022",
                    wait = waitDurationSeconds.seconds
                )
            }
    }
}
