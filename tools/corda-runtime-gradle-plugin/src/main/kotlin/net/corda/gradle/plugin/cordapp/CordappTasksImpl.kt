@file:Suppress("DEPRECATION")
// used for CertificatesRestResource

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
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySessionKeyAndCertificate
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.sdk.rest.RestClientUtils
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
            return
        }

        if (!groupPolicyFile.exists() || groupPolicyFile.lastModified() < networkConfigFile.lastModified()) {
            pc.logger.quiet("Creating the Group policy.")
            val configX500Names = pc.networkConfig.x500Names.filterNotNull().map { MemberX500Name.parse(it) }

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
            val fis = FileInputStream(groupPolicyFile)
            val mapper = ObjectMapper()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            mapper.readValue(fis, GroupPolicyDTO::class.java)
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
            KeyStoreHelper().generateKeyStore(
                keyStoreFile = keystoreFile,
                alias = pc.keystoreAlias,
                password = pc.keystorePassword
            )
            pc.logger.quiet("Importing default gradle certificate")
            KeyStoreHelper().importCertificateIntoKeyStore(
                keyStoreFile = keystoreFile,
                keyStorePassword = pc.keystorePassword,
                certificateInputStream = File(pc.gradleDefaultCertFilePath).inputStream(),
                certificateAlias = pc.gradleDefaultCertAlias
            )
            pc.logger.quiet("Importing R3 signing certificate")
            KeyStoreHelper().importCertificateIntoKeyStore(
                keyStoreFile = keystoreFile,
                keyStorePassword = pc.keystorePassword,
                certificateInputStream = File(pc.r3RootCertFile).inputStream(),
                certificateAlias = pc.r3RootCertKeyAlias
            )

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

        val uploaderRestClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val forceUploaderRestClient = RestClientUtils.createRestClient(
            VirtualNodeMaintenanceRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val cpiChecksum = helper.uploadCpi(
            uploaderRestClient,
            forceUploaderRestClient,
            pc.corDappCpiFilePath,
            pc.corDappCpiName,
            pc.project.version.toString(),
            pc.corDappCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.corDappCpiName} uploaded: ${cpiChecksum.value}")
        val notaryChecksum = helper.uploadCpi(
            uploaderRestClient,
            forceUploaderRestClient,
            pc.notaryCpiFilePath,
            pc.notaryCpiName,
            pc.project.version.toString(),
            pc.notaryCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.notaryCpiName} uploaded: ${notaryChecksum.value}")
        uploaderRestClient.close()
        forceUploaderRestClient.close()
    }

    private fun uploadCerts() {
        val clientCertificate = ClientCertificates()
        val certificateRestClient = RestClientUtils.createRestClient(
            CertificatesRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        clientCertificate.uploadCertificate(
            restClient = certificateRestClient,
            certificate = File(pc.gradleDefaultCertFilePath).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.gradleDefaultCertAlias
        )
        pc.logger.quiet("Certificate '${pc.gradleDefaultCertAlias}' uploaded.")

        clientCertificate.uploadCertificate(
            restClient = certificateRestClient,
            certificate = File(pc.keystoreCertFilePath).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.keystoreAlias
        )
        pc.logger.quiet("Certificate '${pc.keystoreAlias}' uploaded.")

        clientCertificate.uploadCertificate(
            restClient = certificateRestClient,
            certificate = File(pc.r3RootCertFile).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.r3RootCertKeyAlias
        )
        pc.logger.quiet("Certificate '${pc.r3RootCertKeyAlias}' uploaded.")
        certificateRestClient.close()
    }

    fun deployMgmCpi() {
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

        if (pc.networkConfig.mgmNodeIsPresentInNetworkDefinition) {
            // only upload for dynamic network
            uploadCerts()
        }

        val uploaderRestClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val forceUploaderRestClient = RestClientUtils.createRestClient(
            VirtualNodeMaintenanceRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val cpiChecksum = DeployCpiHelper().uploadCpi(
            uploaderRestClient,
            forceUploaderRestClient,
            pc.mgmCorDappCpiFilePath,
            cpiName,
            pc.project.version.toString(),
            pc.mgmCorDappCpiChecksumFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI $cpiName uploaded: ${cpiChecksum.value}")
        uploaderRestClient.close()
        forceUploaderRestClient.close()

        val mgmVNode = pc.networkConfig.getMgmNode()!!
        NetworkTasksImpl(pc).createVNodes(requiredNodes = listOf(mgmVNode))
        pc.logger.quiet("Registering MGM")
        NetworkTasksImpl(pc).registerVNodes(requiredNodes = listOf(mgmVNode))
        val mgmHoldingId = getMgmHoldingId()
        val hsmRestClient = RestClientUtils.createRestClient(
            HsmRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val keyRestClient = RestClientUtils.createRestClient(
            KeysRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val sessionKeyId = VNodeHelper().generateSessionKey(
            hsmRestClient = hsmRestClient,
            keyRestClient = keyRestClient,
            holdingId = mgmHoldingId
        )
        hsmRestClient.close()
        keyRestClient.close()

        val networkRestClient = RestClientUtils.createRestClient(
            NetworkRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
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
            restClient = networkRestClient,
            request = request,
            holdingId = mgmHoldingId,
        )
        networkRestClient.close()
    }

    private fun getMgmHoldingId(): ShortHash {
        val vNodeRestClient = RestClientUtils.createRestClient(
            VirtualNodeRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val existingNodes = VirtualNode().getAllVirtualNodes(vNodeRestClient).virtualNodes
        vNodeRestClient.close()
        val nodeDetails = VNodeHelper().findMatchingVNodeFromList(existingNodes = existingNodes, pc.networkConfig.getMgmNode()!!)
        return ShortHash.parse(nodeDetails.holdingIdentity.shortHash)
    }

    fun extractGroupPolicyFromMgm() {
        pc.logger.quiet("Extracting policy from MGM")
        val mgmHoldingId = getMgmHoldingId()
        val restClient = RestClientUtils.createRestClient(
            MGMRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        val groupPolicyResponse = ExportGroupPolicyFromMgm().exportPolicy(restClient = restClient, holdingIdentityShortHash = mgmHoldingId)
        restClient.close()
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        PrintHelper.writeGroupPolicyToFile(groupPolicyFile, ObjectMapper().readTree(groupPolicyResponse))
        pc.logger.quiet("Group policy file created at $groupPolicyFile")
    }
}