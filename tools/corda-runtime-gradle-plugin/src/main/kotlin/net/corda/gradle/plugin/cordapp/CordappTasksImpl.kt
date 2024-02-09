package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.GroupPolicyDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.io.File
import java.io.FileInputStream

class CordappTasksImpl(var pc: ProjectContext) {

    /**
     * Creates a group Policy based on the network config file and outputs it to the Group Policy json file.
     */
    fun createGroupPolicy() {
        val groupPolicyFile = File(pc.groupPolicyFilePath)
        val networkConfigFile = File("${pc.project.rootDir}${pc.networkConfig.configFilePath}")

        val pluginsDir = "${pc.cordaCliBinDir}/plugins/"

        if (!groupPolicyFile.exists() || groupPolicyFile.lastModified() < networkConfigFile.lastModified()) {

            pc.logger.quiet("Creating the Group policy.")
            val configX500Ids = pc.networkConfig.x500Names

            GroupPolicyHelper().createStaticGroupPolicy(
                groupPolicyFile,
                configX500Ids,
                pc.javaBinDir,
                pluginsDir,
                pc.cordaCliBinDir
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
            val firstLine = groupPolicyFile.useLines { it.firstOrNull() }
            if ((firstLine != null) && firstLine.contains("Unable to access jarfile \\S*corda-cli.jar".toRegex())) {
                throw CordaRuntimeGradlePluginException("Unable to find the Corda CLI, has it been installed?")
            }

            throw CordaRuntimeGradlePluginException("Failed to read GroupPolicy from group policy file with exception: $e.")
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
            KeyStoreHelper().generateKeyPair(
                pc.javaBinDir,
                pc.keystoreAlias,
                pc.keystorePassword,
                pc.keystoreFilePath
            )

            pc.logger.quiet("Importing default gradle certificate")
            KeyStoreHelper().importKeystoreCert(
                pc.javaBinDir,
                pc.keystorePassword,
                pc.keystoreFilePath,
                pc.gradleDefaultCertAlias,
                pc.gradleDefaultCertFilePath
            )
            pc.logger.quiet("Importing R3 signing certificate")
            KeyStoreHelper().importKeystoreCert(
                pc.javaBinDir,
                pc.keystorePassword,
                pc.keystoreFilePath,
                pc.r3RootCertKeyAlias,
                pc.r3RootCertFile
            )
            KeyStoreHelper().exportCert(
                pc.javaBinDir,
                pc.keystoreAlias,
                pc.keystoreFilePath,
                pc.keystoreCertFilePath
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
            pc.javaBinDir,
            pc.cordaCliBinDir,
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
        BuildCpiHelper().createCPI(
            pc.javaBinDir,
            pc.cordaCliBinDir,
            pc.groupPolicyFilePath,
            pc.keystoreFilePath,
            pc.keystoreAlias,
            pc.keystorePassword,
            pc.notaryCpbFilePath,
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
        helper.uploadCertificate(
            pc.cordaClusterURL,
            pc.cordaRestUser,
            pc.cordaRestPassword,
            pc.gradleDefaultCertAlias,
            pc.gradleDefaultCertFilePath
        )
        pc.logger.quiet("Certificate '${pc.gradleDefaultCertAlias}' uploaded.")
        helper.uploadCertificate(
            pc.cordaClusterURL,
            pc.cordaRestUser,
            pc.cordaRestPassword,
            pc.keystoreAlias,
            pc.keystoreCertFilePath
        )
        pc.logger.quiet("Certificate '${pc.keystoreAlias}' uploaded.")
        helper.uploadCertificate(
            pc.cordaClusterURL,
            pc.cordaRestUser,
            pc.cordaRestPassword,
            pc.r3RootCertKeyAlias,
            pc.r3RootCertFile
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