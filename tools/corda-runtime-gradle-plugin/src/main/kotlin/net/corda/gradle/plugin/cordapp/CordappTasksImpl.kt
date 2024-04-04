@file:Suppress("DEPRECATION")
// used for CertificatesRestResource

package net.corda.gradle.plugin.cordapp

import net.corda.data.certificates.CertificateUsage
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.GroupPolicyDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.sdk.rest.RestClientUtils
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream

class CordappTasksImpl(var pc: ProjectContext) {

    /**
     * Creates a group Policy based on the network config file and outputs it to the Group Policy json file.
     */
    fun createGroupPolicy() {
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        val networkConfigFile = File("${pc.project.rootDir}${pc.networkConfig.configFilePath}")

        if (!groupPolicyFile.exists() || groupPolicyFile.lastModified() < networkConfigFile.lastModified()) {

            pc.logger.quiet("Creating the Group policy.")
            val configX500Ids = pc.networkConfig.x500Names

            GroupPolicyHelper().createStaticGroupPolicy(
                groupPolicyFile,
                configX500Ids,
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
        val clientCertificate = ClientCertificates()
        val restClient = RestClientUtils.createRestClient(
            CertificatesRestResource::class,
            insecure = true,
            username = pc.cordaRestUser,
            password = pc.cordaRestPassword,
            targetUrl = pc.cordaClusterURL
        )
        clientCertificate.uploadCertificate(
            restClient = restClient,
            certificate = File(pc.gradleDefaultCertFilePath).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.gradleDefaultCertAlias
        )
        pc.logger.quiet("Certificate '${pc.gradleDefaultCertAlias}' uploaded.")

        clientCertificate.uploadCertificate(
            restClient = restClient,
            certificate = File(pc.keystoreCertFilePath).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.keystoreAlias
        )
        pc.logger.quiet("Certificate '${pc.keystoreAlias}' uploaded.")

        clientCertificate.uploadCertificate(
            restClient = restClient,
            certificate = File(pc.r3RootCertFile).inputStream(),
            usage = CertificateUsage.CODE_SIGNER,
            alias = pc.r3RootCertKeyAlias
        )
        pc.logger.quiet("Certificate '${pc.r3RootCertKeyAlias}' uploaded.")
        val cpiUploadStatus = helper.uploadCpi(
            pc.cordaClusterURL,
            pc.cordaRestUser,
            pc.cordaRestPassword,
            pc.corDappCpiFilePath,
            pc.corDappCpiName,
            pc.project.version.toString(),
            pc.corDappCpiUploadStatusFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.corDappCpiName} uploaded: ${cpiUploadStatus.cpiFileChecksum}")
        val notaryUploadStatus = helper.uploadCpi(
            pc.cordaClusterURL,
            pc.cordaRestUser,
            pc.cordaRestPassword,
            pc.notaryCpiFilePath,
            pc.notaryCpiName,
            pc.project.version.toString(),
            pc.notaryCpiUploadStatusFilePath,
            pc.cpiUploadTimeout
        )
        pc.logger.quiet("CPI ${pc.notaryCpiName} uploaded: ${notaryUploadStatus.cpiFileChecksum}")
    }
}