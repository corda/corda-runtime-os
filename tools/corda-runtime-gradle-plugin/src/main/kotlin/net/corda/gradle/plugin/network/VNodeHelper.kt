package net.corda.gradle.plugin.network

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.ShortHash
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.sdk.network.config.VNode
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.membership.lib.MemberInfoExtension
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.ConfigSchemaVersion
import net.corda.restclient.generated.models.HostedIdentitySessionKeyAndCertificate
import net.corda.restclient.generated.models.HostedIdentitySetupRequest
import net.corda.restclient.generated.models.JsonCreateVirtualNodeRequest
import net.corda.restclient.generated.models.KeyPairIdentifier
import net.corda.restclient.generated.models.MemberRegistrationRequest
import net.corda.restclient.generated.models.RegistrationRequestProgress
import net.corda.restclient.generated.models.UpdateConfigParameters
import net.corda.restclient.generated.models.VirtualNodeInfo
import net.corda.schema.configuration.ConfigKeys
import net.corda.sdk.config.ClusterConfig
import net.corda.sdk.data.Checksum
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.Keys
import net.corda.sdk.network.MemberRole
import net.corda.sdk.network.RegistrationRequest
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.network.writeToTempFile
import net.corda.sdk.packaging.CpiUploader
import net.corda.v5.base.types.MemberX500Name
import java.io.File
import java.net.URI

class VNodeHelper {

    private val mapper = ObjectMapper()
    private val certificateAuthority by lazy {
        caHome.parentFile.mkdirs()
        CertificateAuthorityFactory
            .createFileSystemLocalAuthority(
                RSA_TEMPLATE.toFactoryDefinitions(),
                caHome,
            ).also { it.save() }
    }
    private val p2pGatewayUrls = listOf("https://localhost:8080")
    private lateinit var caHome: File // must be set before we call [certificateAuthority]

    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun createVNode(
        restClient: CordaRestClient,
        vNode: VNode,
        cpiUploadStatusFilePath: String
    ) {
        val cpiCheckSum = readCpiChecksumFromFile(cpiUploadStatusFilePath)
        if (!CpiUploader(restClient).cpiChecksumExists(checksum = cpiCheckSum)) {
            throw CordaRuntimeGradlePluginException("CPI $cpiCheckSum not uploaded.")
        }

        val request = JsonCreateVirtualNodeRequest(
            x500Name = vNode.x500Name,
            cpiFileChecksum = cpiCheckSum.value,
            vaultDdlConnection = null,
            vaultDmlConnection = null,
            cryptoDdlConnection = null,
            cryptoDmlConnection = null,
            uniquenessDdlConnection = null,
            uniquenessDmlConnection = null,
        )
        VirtualNode(restClient).create(request = request)
    }

    /**
     * Reads the latest CPI checksums from file.
     */
    fun readCpiChecksumFromFile(
        cpiChecksumFilePath: String
    ): Checksum {
        try {
            val fis = File(cpiChecksumFilePath)
            // Mapper won't parse directly into Checksum
            return Checksum(mapper.readValue(fis, String::class.java))
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to read CPI checksum from file, with error: $e")
        }
    }

    fun findMatchingVNodeFromList(existingNodes: List<VirtualNodeInfo>, requiredNode: VNode): VirtualNodeInfo {
        val matches = existingNodes.filter { en ->
            en.holdingIdentity.x500Name == requiredNode.x500Name &&
                    en.cpiIdentifier.cpiName == requiredNode.cpi
        }
        if (matches.isEmpty()) {
            throw CordaRuntimeGradlePluginException(
                "Registration failed because virtual node for '${requiredNode.x500Name}' not found."
            )
        } else if (matches.size > 1) {
            throw CordaRuntimeGradlePluginException(
                "Registration failed because more than one virtual node for '${requiredNode.x500Name}'"
            )
        }
        return matches.single()
    }

    /**
     * Build the registration request based on the type of VNode
     */
    @Suppress("LongParameterList")
    fun getRegistrationRequest(
        restClient: CordaRestClient,
        vNode: VNode,
        holdingId: ShortHash,
        clusterURI: URI,
        isDynamicNetwork: Boolean,
        certificateAuthorityFilePath: String
    ): MemberRegistrationRequest {
        caHome = File(certificateAuthorityFilePath)
        return if (vNode.mgmNode == "true") {
            getMgmRegRequest(
                restClient = restClient,
                holdingId = holdingId,
                clusterURI = clusterURI
            )
        } else if (vNode.serviceX500Name == null) {
            if (isDynamicNetwork) {
                getDynamicMemberRegRequest(
                    restClient = restClient,
                    holdingId = holdingId
                )
            } else {
                RegistrationRequest().createStaticMemberRegistrationRequest()
            }
        } else {
            val flowProtocolValue = vNode.flowProtocolName ?: "com.r3.corda.notary.plugin.nonvalidating"
            val backchainValue = vNode.backchainRequired ?: "true"
            val notaryServiceName = vNode.serviceX500Name!!

            if (isDynamicNetwork) {
                val customProps = mapOf(
                    MemberInfoExtension.NOTARY_SERVICE_NAME to notaryServiceName,
                    MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED to backchainValue,
                    MemberInfoExtension.NOTARY_SERVICE_PROTOCOL to flowProtocolValue
                )
                getDynamicNotaryRegRequest(
                    restClient = restClient,
                    holdingId = holdingId,
                    customProperties = customProps
                )
            } else {
                RegistrationRequest().createStaticNotaryRegistrationRequest(
                    notaryServiceName = notaryServiceName,
                    notaryServiceProtocol = flowProtocolValue,
                    isBackchainRequired = backchainValue.toBoolean()
                )
            }
        }
    }

    /**
     * Registers an individual Vnode
     */
    fun registerVNode(
        restClient: CordaRestClient,
        registrationRequest: MemberRegistrationRequest,
        shortHash: ShortHash
    ): RegistrationRequestProgress {
        return RegistrationRequester(restClient).requestRegistration(
            memberRegistrationRequest = registrationRequest,
            holdingId = shortHash
        )
    }

    internal fun getDynamicNotaryRegRequest(
        restClient: CordaRestClient,
        holdingId: ShortHash,
        customProperties: Map<String, String>
    ): MemberRegistrationRequest {
        val sessionKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.SESSION_INIT_KEY)
        val notaryKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.NOTARY_KEY)
        configureNodeAsNetworkParticipant(restClient, sessionKey, holdingId)
        return RegistrationRequest().createNotaryRegistrationRequest(
            preAuthToken = null,
            roles = setOf(MemberRole.NOTARY),
            customProperties = customProperties,
            p2pGatewayUrls = p2pGatewayUrls,
            sessionKey = sessionKey,
            notaryKey = notaryKey
        )
    }

    private fun getDynamicMemberRegRequest(
        restClient: CordaRestClient,
        holdingId: ShortHash
    ): MemberRegistrationRequest {
        val sessionKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.SESSION_INIT_KEY)
        val ledgerKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.LEDGER_KEY)
        configureNodeAsNetworkParticipant(restClient, sessionKey, holdingId)
        return RegistrationRequest().createMemberRegistrationRequest(
            preAuthToken = null,
            roles = null,
            customProperties = null,
            p2pGatewayUrls = p2pGatewayUrls,
            sessionKey = sessionKey,
            ledgerKey = ledgerKey
        )
    }

    private fun configureNodeAsNetworkParticipant(
        restClient: CordaRestClient,
        sessionKey: KeyPairIdentifier,
        holdingId: ShortHash
    ) {
        val request = HostedIdentitySetupRequest(
            p2pTlsCertificateChainAlias = Keys.P2P_TLS_CERTIFICATE_ALIAS,
            useClusterLevelTlsCertificateAndKey = true,
            sessionKeysAndCertificates = listOf(
                HostedIdentitySessionKeyAndCertificate(
                    sessionKeyId = sessionKey.id,
                    preferred = true,
                ),
            ),
        )
        RegistrationRequester(restClient).configureAsNetworkParticipant(
            request = request,
            holdingId = holdingId,
        )
    }

    @Suppress("LongParameterList")
    private fun getMgmRegRequest(
        restClient: CordaRestClient,
        holdingId: ShortHash,
        clusterURI: URI
    ): MemberRegistrationRequest {
        val sessionKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.SESSION_INIT_KEY)
        val preAuthKey = generateKey(restClient, holdingId, CryptoConsts.Categories.KeyCategory.PRE_AUTH_KEY)
        setupTlsKeyAndSignCsr(restClient, clusterURI)
        disableCrl(restClient)
        return RegistrationRequest().createMgmRegistrationRequest(
            mtls = false,
            p2pGatewayUrls = p2pGatewayUrls,
            sessionKey = sessionKey,
            ecdhKey = preAuthKey,
            tlsTrustRoot = certificateAuthority.caCertificate.toPem()
        )
    }

    fun generateSessionKey(
        restClient: CordaRestClient,
        holdingId: ShortHash
    ): KeyPairIdentifier {
        return Keys(restClient).assignSoftHsmAndGenerateKey(
            holdingIdentityShortHash = holdingId,
            category = CryptoConsts.Categories.KeyCategory.SESSION_INIT_KEY
        )
    }

    fun generateKey(
        restClient: CordaRestClient,
        holdingId: ShortHash,
        category: CryptoConsts.Categories.KeyCategory
    ): KeyPairIdentifier {
        return Keys(restClient).assignSoftHsmAndGenerateKey(
            holdingIdentityShortHash = holdingId,
            category = category
        )
    }

    private fun disableCrl(restClient: CordaRestClient) {
        val clusterConfig = ClusterConfig(restClient)
        val objectMapper = ObjectMapper()
        val currentConfig = clusterConfig.getCurrentConfig(configSection = ConfigKeys.RootConfigKey.P2P_GATEWAY)
        val rawConfig = currentConfig.configWithDefaults
        val rawConfigJson = objectMapper.readTree(rawConfig)
        val sslConfig = rawConfigJson["sslConfig"]
        val currentMode = sslConfig["revocationCheck"]?.get("mode")?.asText()

        if (currentMode == "OFF") {
            return
        }

        val newConfig = objectMapper.createObjectNode()
        newConfig.set<ObjectNode>(
            "sslConfig",
            objectMapper.createObjectNode()
                .set<ObjectNode>(
                    "revocationCheck",
                    objectMapper.createObjectNode().put("mode", "OFF"),
                ),
        )
        val payload = UpdateConfigParameters(
            section = "corda.p2p.gateway",
            version = currentConfig.version,
            config = newConfig,
            schemaVersion = ConfigSchemaVersion(major = currentConfig.schemaVersion.major, minor = currentConfig.schemaVersion.minor),
        )
        clusterConfig.updateConfig(updateConfig = payload)
    }

    private fun setupTlsKeyAndSignCsr(
        restClient: CordaRestClient,
        cordaClusterUri: URI
    ) {
        val keysHelper = Keys(restClient)
        if (!keysHelper.hasTlsKey()) {
            val tlsKey = keysHelper.generateTlsKey()

            val clientCertificates = ClientCertificates(restClient)
            val p2pHosts = cordaClusterUri.host

            val csrCertRequest = clientCertificates.generateP2pCsr(
                tlsKey = tlsKey,
                subjectX500Name = MemberX500Name.parse("CN=CordaOperator, C=GB, L=London, O=Org"),
                p2pHostNames = listOf(p2pHosts),
            )
            certificateAuthority.signCsr(csrCertRequest).toPem().writeToTempFile("CSR.csr").also {
                clientCertificates.uploadTlsCertificate(certificateFile = it)
            }
        }
    }
}
