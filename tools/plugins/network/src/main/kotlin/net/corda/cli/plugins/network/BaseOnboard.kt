@file:Suppress("DEPRECATION")

package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.CreateVirtualNodeRequest
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySessionKeyAndCertificate
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.sdk.config.ClusterConfig
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.Keys
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Date

@Suppress("TooManyFunctions")
abstract class BaseOnboard : Runnable, RestCommand() {
    private companion object {
        const val P2P_TLS_KEY_ALIAS = "p2p-tls-key"
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
        const val SIGNING_KEY_ALIAS = "signing key 1"
        const val SIGNING_KEY_STORE_PASSWORD = "keystore password"
        const val GRADLE_PLUGIN_DEFAULT_KEY_ALIAS = "gradle-plugin-default-key"

        fun createKeyStoreFile(keyStoreFile: File) {
            val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
            val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(
                SignatureSpecs.RSA_SHA256.signatureName,
            )
            val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
            val parameter = PrivateKeyFactory.createKey(keyPair.private.encoded)
            val sigGen = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(parameter)
            val now = System.currentTimeMillis()
            val startDate = Date(now)
            val dnName = X500Name("CN=Default Signing Key, O=R3, L=London, c=GB")
            val certSerialNumber = BigInteger.TEN
            val endDate = Date(now + 100L * 60 * 60 * 24 * 1000)
            val certificateBuilder =
                JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.public)
            val certificate = JcaX509CertificateConverter().getCertificate(
                certificateBuilder.build(sigGen),
            )
            val keyStore = KeyStore.getInstance("pkcs12")
            keyStore.load(null, SIGNING_KEY_STORE_PASSWORD.toCharArray())
            keyStore.setKeyEntry(
                SIGNING_KEY_ALIAS,
                keyPair.private,
                SIGNING_KEY_STORE_PASSWORD.toCharArray(),
                arrayOf(certificate),
            )
            BaseOnboard::class.java
                .getResourceAsStream(
                    "/certificates/gradle-plugin-default-key.pem",
                ).use { certificateInputStream ->
                    keyStore.setCertificateEntry(
                        GRADLE_PLUGIN_DEFAULT_KEY_ALIAS,
                        CertificateFactory.getInstance("X.509")
                            .generateCertificate(certificateInputStream),
                    )
                }
            keyStoreFile.outputStream().use {
                keyStore.store(it, SIGNING_KEY_STORE_PASSWORD.toCharArray())
            }
        }
    }

    @Parameters(
        description = ["The X500 name of the virtual node."],
        arity = "1",
        index = "0",
    )
    lateinit var name: String

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
    )
    var tlsCertificateSubject: String? = null

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
        val uploadId = CpiUploader().uploadCPI(restClient, cpi, cpiName).id
        return checkCpiStatus(uploadId)
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
        return CpiUploader().cpiChecksum(restClient = restClient, uploadRequestId = id)
    }

    protected abstract val cpiFileChecksum: String

    protected abstract val registrationContext: Map<String, Any?>

    protected val holdingId: String by lazy {
        val restClient = createRestClient(
            VirtualNodeRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val request = CreateVirtualNodeRequest(
            x500Name = name,
            cpiFileChecksum = cpiFileChecksum,
            vaultDdlConnection = null,
            vaultDmlConnection = null,
            cryptoDdlConnection = null,
            cryptoDmlConnection = null,
            uniquenessDdlConnection = null,
            uniquenessDmlConnection = null,
        )
        val shortHashId = VirtualNode().createAndWaitForActive(restClient, request)
        println("Holding identity short hash of '$name' is: '$shortHashId'")
        shortHashId
    }

    protected fun assignSoftHsmAndGenerateKey(category: String): String {
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
        return Keys().assignSoftHsmAndGenerateKey(hsmRestClient, keyRestClient, holdingId, category)
    }

    protected val sessionKeyId by lazy {
        assignSoftHsmAndGenerateKey("SESSION_INIT")
    }
    protected val ecdhKeyId by lazy {
        assignSoftHsmAndGenerateKey("PRE_AUTH")
    }
    protected val certificateSubject by lazy {
        tlsCertificateSubject ?: "O=P2P Certificate, OU=$p2pHosts, L=London, C=GB"
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
        val keyRestClient = createRestClient(
            KeysRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val keys = Keys()
        val hasKeys = keys.hasTlsKey(keyRestClient)

        if (hasKeys) return

        val tlsKeyId = keys.generateTlsKey(keyRestClient)

        val certificateRestClient = createRestClient(
            CertificatesRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val clientCertificates = ClientCertificates()

        val csrCertRequest = clientCertificates.generateP2pCsr(certificateRestClient, tlsKeyId, certificateSubject, p2pHosts)
        val certificate = ca.signCsr(csrCertRequest).toPem().byteInputStream()
        clientCertificates.uploadTlsCertificate(certificateRestClient, certificate)
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
                    sessionKeyId = sessionKeyId,
                    preferred = true,
                ),
            ),
        )

        RegistrationRequester().configureAsNetworkParticipant(restClient = restClient, request = request, holdingId = holdingId)
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
            registrationContext = registrationContext,
            holdingId = holdingId
        )
        val registrationId = response.registrationId
        val submissionStatus = response.registrationStatus

        if (submissionStatus != "SUBMITTED") {
            throw OnboardException("Could not submit registration request: ${response.memberInfoSubmitted}")
        }

        println("Registration ID for '$name' is '$registrationId'")

        if (waitForFinalStatus) {
            RegistrationRequester().waitForRegistrationApproval(
                restClient = restClient,
                registrationId = registrationId,
                holdingId = holdingId
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
        var currentConfig = clusterConfig.getCurrentConfig(restClient, "corda.p2p.gateway")
        val rawConfig = currentConfig.configWithDefaults
        val rawConfigJson = json.readTree(rawConfig)
        val sslConfig = rawConfigJson["sslConfig"]
        val currentMode = sslConfig["revocationCheck"]?.get("mode")?.asText()
        val currentTlsType = sslConfig["tlsType"]?.asText()

        if (currentMode != "OFF") {
            clusterConfig.configureCrl(restClient, "OFF", currentConfig)
            // Update currentConfig ahead of next check
            currentConfig = clusterConfig.getCurrentConfig(restClient, "corda.p2p.gateway")
        }

        val tlsType = if (mtls) {
            "MUTUAL"
        } else {
            "ONE_WAY"
        }
        if (currentTlsType != tlsType) {
            clusterConfig.configureTlsType(restClient, tlsType, currentConfig)
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
        if (!keyStoreFile.canRead()) {
            createKeyStoreFile(keyStoreFile)
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
                    usage = "code-signer",
                    alias = GRADLE_PLUGIN_DEFAULT_KEY_ALIAS
                )
            }
        keyStore.getCertificate(SIGNING_KEY_ALIAS)
            ?.toPem()
            ?.byteInputStream()
            ?.use { certificate ->
                ClientCertificates().uploadCertificate(
                    restClient = restClient,
                    certificate = certificate,
                    usage = "code-signer",
                    alias = "signingkey1-2022"
                )
            }
    }
}
