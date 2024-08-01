package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.GroupPolicyDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.network.NetworkTasksImpl
import net.corda.gradle.plugin.network.VNodeHelper
import net.corda.restclient.generated.models.HostedIdentitySessionKeyAndCertificate
import net.corda.restclient.generated.models.HostedIdentitySetupRequest
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.v5.base.types.MemberX500Name
import java.io.File
import java.io.FileInputStream

class CordappTasksImpl(var pc: ProjectContext) {

    /**
     * Creates a group Policy based on the network config file and outputs it to the Group Policy json file.
     */
    fun createGroupPolicy() {
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        val networkConfigFile = File("${pc.project.rootDir}${pc.networkConfig.configFilePath}")
        if (pc.networkConfig.mgmNodeIsPresentInNetworkDefinition) {
            pc.logger.warn("Skipping create policy, as policy is generated by an MGM in a dynamic network.")
            return
        }

        if (!groupPolicyFile.exists() || groupPolicyFile.lastModified() < networkConfigFile.lastModified()) {
            pc.logger.quiet("Creating the Group policy.")
            val configX500Names = pc.networkConfig.x500Names.map { MemberX500Name.parse(it) }

            GroupPolicyHelper().createStaticGroupPolicy(
                groupPolicyFile,
                configX500Names,
            )
        } else {
            pc.logger.quiet("Group policy up to date.")
        }
        validateGroupPolicy()
    }

    private fun validateGroupPolicy() {
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        val groupPolicy = try {
            val mapper = ObjectMapper()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            FileInputStream(groupPolicyFile).use { fis ->
                mapper.readValue(fis, GroupPolicyDTO::class.java)
            }
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to read GroupPolicy from group policy file with exception: $e.", e)
        }
        validateGroupPolicyHasAllMembers(groupPolicy)
    }

    private fun validateGroupPolicyHasAllMembers(groupPolicy: GroupPolicyDTO) {
        val membersNames = groupPolicy.protocolParameters?.staticNetwork?.members?.map { it.name }
        val requiredNodes = pc.networkConfig.vNodes
        if (requiredNodes.isNotEmpty() && membersNames == null) {
            throw CordaRuntimeGradlePluginException(
                "GroupPolicy File does not contain any members"
            )
        } else {
            requiredNodes.forEach { requiredNode ->
                if (!membersNames!!.contains(requiredNode.x500Name)) {
                    throw CordaRuntimeGradlePluginException(
                        "GroupPolicy File does not contain member ${requiredNode.x500Name} specified in the networkConfigFile"
                    )
                }
            }
        }
    }

    /**
     * Creates the key pairs and keystore required to sign Cpis.
     */
    fun createKeyStore() {
        val keystoreFile = File(pc.keystoreFilePath)
        if (!keystoreFile.exists()) {
            pc.logger.quiet("Creating a keystore and signing certificate.")
            keystoreFile.parentFile.mkdirs()
            KeyStoreHelper().generateKeyStore(
                keyStoreFile = keystoreFile,
                alias = pc.keystoreAlias,
                password = pc.keystorePassword
            )
            pc.logger.quiet("Importing default gradle certificate")
            File(pc.gradleDefaultCertFilePath).inputStream().use {
                KeyStoreHelper().importCertificateIntoKeyStore(
                    keyStoreFile = keystoreFile,
                    keyStorePassword = pc.keystorePassword,
                    certificateInputStream = it,
                    certificateAlias = pc.gradleDefaultCertAlias
                )
            }
            pc.logger.quiet("Importing R3 signing certificate")
            File(pc.r3RootCertFile).inputStream().use {
                KeyStoreHelper().importCertificateIntoKeyStore(
                    keyStoreFile = keystoreFile,
                    keyStorePassword = pc.keystorePassword,
                    certificateInputStream = it,
                    certificateAlias = pc.r3RootCertKeyAlias
                )
            }

            KeyStoreHelper().exportCertificateFromKeyStore(
                keyStoreFile = keystoreFile,
                keyStorePassword = pc.keystorePassword,
                certificateAlias = pc.keystoreAlias,
                exportedCertFile = File(pc.keystoreCertFilePath)
            )
        } else {
            pc.logger.quiet("Keystore and signing certificate already created.")
        }
    }

    /**
     * Builds the CPIs for the CordDapp and the Notary
     */
    fun buildCPIs() {
        pc.logger.quiet("Creating ${pc.corDappCpiName} CPI.")
        BuildCpiHelper().createCPI(
            pc.groupPolicyFilePath,
            pc.keystoreFilePath,
            pc.keystoreAlias,
            pc.keystorePassword,
            pc.corDappCpbFilePath,
            pc.corDappCpiFilePath,
            pc.corDappCpiName,
            pc.project.version.toString()
        )

        pc.logger.quiet("Creating ${pc.notaryCpiName} CPI.")
        val notaryCpb = if (pc.isNotaryNonValidating) {
            pc.nonValidatingNotaryCpbFilePath
        } else {
            pc.contractVerifyingNotaryCpbFilePath
        }
        BuildCpiHelper().createCPI(
            pc.groupPolicyFilePath,
            pc.keystoreFilePath,
            pc.keystoreAlias,
            pc.keystorePassword,
            notaryCpb,
            pc.notaryCpiFilePath,
            pc.notaryCpiName,
            pc.project.version.toString()
        )
    }

    /**
     * Uploads the required certificates and uploads the CorDapp and Notary CPIs
     */
    fun deployCPIs() {
        val helper = DeployCpiHelper()
        if (!pc.networkConfig.mgmNodeIsPresentInNetworkDefinition) {
            // only upload for static network
            uploadCerts()
        }

        val cpiChecksum = helper.uploadCpi(
            pc.restClient,
            pc.corDappCpiFilePath,
            pc.corDappCpiName,
            pc.project.version.toString(),
            pc.corDappCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.corDappCpiName} uploaded: ${cpiChecksum.value}")
        val notaryChecksum = helper.uploadCpi(
            pc.restClient,
            pc.notaryCpiFilePath,
            pc.notaryCpiName,
            pc.project.version.toString(),
            pc.notaryCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.notaryCpiName} uploaded: ${notaryChecksum.value}")
    }

    private fun uploadCerts() {
        val clientCertificate = ClientCertificates(pc.restClient)
        clientCertificate.uploadClusterCertificate(
            certificateFile = File(pc.gradleDefaultCertFilePath),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.gradleDefaultCertAlias
        )
        pc.logger.quiet("Certificate '${pc.gradleDefaultCertAlias}' uploaded.")

        clientCertificate.uploadClusterCertificate(
            certificateFile = File(pc.keystoreCertFilePath),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.keystoreAlias
        )
        pc.logger.quiet("Certificate '${pc.keystoreAlias}' uploaded.")

        clientCertificate.uploadClusterCertificate(
            certificateFile = File(pc.r3RootCertFile),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.r3RootCertKeyAlias
        )
        pc.logger.quiet("Certificate '${pc.r3RootCertKeyAlias}' uploaded.")
    }

    fun deployMgmCpi() {
        require(pc.networkConfig.mgmNodeIsPresentInNetworkDefinition) { "An MGM must be included in the network definition" }
        require(pc.mgmCpiName != null) { "MGM CPI name should be defined in network config file" }
        val cpiName = pc.mgmCpiName!!
        pc.logger.quiet("Creating $cpiName CPI.")
        BuildCpiHelper().createMgmCpi(
            keystoreFilePath = pc.keystoreFilePath,
            keystoreAlias = pc.keystoreAlias,
            keystorePassword = pc.keystorePassword,
            cpiFilePath = pc.mgmCorDappCpiFilePath,
            cpiName = cpiName,
            cpiVersion = pc.project.version.toString()
        )
        uploadCerts()

        val cpiChecksum = DeployCpiHelper().uploadCpi(
            pc.restClient,
            pc.mgmCorDappCpiFilePath,
            cpiName,
            pc.project.version.toString(),
            pc.mgmCorDappCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI $cpiName uploaded: ${cpiChecksum.value}")

        val mgmVNode = pc.networkConfig.getMgmNode()!!
        NetworkTasksImpl(pc).createVNodes(requiredNodes = listOf(mgmVNode))
        pc.logger.quiet("Registering MGM")
        NetworkTasksImpl(pc).registerVNodes(requiredNodes = listOf(mgmVNode))
        val mgmHoldingId = getMgmHoldingId()
        val sessionKeyId = VNodeHelper().generateSessionKey(
            restClient = pc.restClient,
            holdingId = mgmHoldingId
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
        RegistrationRequester(pc.restClient).configureAsNetworkParticipant(
            request = request,
            holdingId = mgmHoldingId,
        )
    }

    private fun getMgmHoldingId(): ShortHash {
        val existingNodes = VirtualNode(pc.restClient).getAllVirtualNodes().virtualNodes
        val nodeDetails = VNodeHelper().findMatchingVNodeFromList(existingNodes = existingNodes, pc.networkConfig.getMgmNode()!!)
        return ShortHash.parse(nodeDetails.holdingIdentity.shortHash)
    }

    fun extractGroupPolicyFromMgm() {
        pc.logger.quiet("Extracting policy from MGM")
        val mgmHoldingId = getMgmHoldingId()
        val groupPolicyResponse = ExportGroupPolicyFromMgm(pc.restClient).exportPolicy(holdingIdentityShortHash = mgmHoldingId)
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        PrintHelper.writeGroupPolicyToFile(groupPolicyFile, ObjectMapper().readTree(groupPolicyResponse))
        pc.logger.quiet("Group policy file created at $groupPolicyFile")
    }
}
