package net.corda.gradle.plugin.configuration

import net.corda.gradle.plugin.cordalifecycle.EnvironmentSetupHelper
import net.corda.restclient.CordaRestClient
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.net.URI

/**
 * Class which holds all the context properties for the gradle build. This is split between:
 * - Properties which are obtained from the cordaRuntimeGradlePlugin block in the build.gradle file
 * - The network config
 * - Properties which are non-configurable by the user
 * A version of this class will typically be passed to each of the helper classes.
 */
class ProjectContext(val project: Project, pluginConfig: PluginConfiguration) {

    // Capture values of user configurable context properties items from pluginConfig
    val cordaClusterURL: String = pluginConfig.cordaClusterURL.get()
    val cordaRestUser: String = pluginConfig.cordaRestUser.get()
    val cordaRestPassword: String = pluginConfig.cordaRestPasswd.get()
    val workspaceDir: String = pluginConfig.cordaRuntimePluginWorkspaceDir.get()
    val composeFilePath: String = pluginConfig.composeFilePath.get()
    val composeNetworkName: String = pluginConfig.composeNetworkName.get()
    val notaryVersion: String = pluginConfig.notaryVersion.get()
    val runtimeVersion: String = pluginConfig.runtimeVersion.get()
    val cordaBinDir: String = pluginConfig.cordaBinDir.get()
    val artifactoryUsername: String = pluginConfig.artifactoryUsername.get()
    val artifactoryPassword: String = pluginConfig.artifactoryPassword.get()
    val notaryCpiName: String = pluginConfig.notaryCpiName.get()
    val corDappCpiName: String = pluginConfig.corDappCpiName.get()
    val cpiUploadTimeout: Long = pluginConfig.cpiUploadTimeout.get().toLong()
    val vnodeRegistrationTimeout: Long = pluginConfig.vnodeRegistrationTimeout.get().toLong()
    val cordaProcessorTimeout: Long = pluginConfig.cordaProcessorTimeout.get().toLong()
    val workflowsModuleName: String = pluginConfig.workflowsModuleName.get()
    val notaryModuleName: String = pluginConfig.notaryModuleName.get()
    val networkConfigFile: String = pluginConfig.networkConfigFile.get()
    val r3RootCertFile: String = "${project.rootDir}/${pluginConfig.r3RootCertFile.get()}"

    // Set Non user configurable context properties
    val javaBinDir: String = "${System.getProperty("java.home")}/bin"
    val cordaPidCache: String = "${project.rootDir}/$workspaceDir/CordaPIDCache.dat"
    val notaryServiceDir: String = "$cordaBinDir/notaryServer"
    val workflowBuildDir: String = "${project.rootDir}/${workflowsModuleName}/build"

    val cordaClusterHost: String = cordaClusterURL.split("://").last().split(":").first()
    val cordaClusterPort: Int = cordaClusterURL.split("://").last().split(":").last().toInt()

    val nonValidatingNotaryCpbFilePath: String = "$notaryServiceDir/notary-plugin-non-validating-server-$notaryVersion-package.cpb"
    val contractVerifyingNotaryCpbFilePath: String = "${project.rootDir}/${notaryModuleName}/build/libs/" +
            "${notaryModuleName}-${project.version}-package.cpb"
    val notaryCpiFilePath: String = "$workflowBuildDir/$notaryCpiName-${project.version}.cpi"
    val corDappCpbFilePath: String = "$workflowBuildDir/libs/${workflowsModuleName}-${project.version}-package.cpb"
    val corDappCpiFilePath: String = "$workflowBuildDir/$corDappCpiName-${project.version}.cpi"
    val corDappCpiChecksumFilePath: String = "${project.rootDir}/$workspaceDir/corDappCpiChecksum.json"
    val notaryCpiChecksumFilePath: String = "${project.rootDir}/$workspaceDir/notaryCpiChecksum.json"
    val mgmCorDappCpiChecksumFilePath: String = "${project.rootDir}/$workspaceDir/mgmCorDappCpiChecksum.json"

    val networkConfig: NetworkConfig = NetworkConfig("${project.rootDir}/${networkConfigFile}")
    val isNotaryNonValidating: Boolean = EnvironmentSetupHelper().isNotaryNonValidating(networkConfig)
    val groupPolicyFilePath: String = "${project.rootDir}/$workspaceDir/GroupPolicy.json"
    val gradleDefaultCertAlias: String = "gradle-plugin-default-key"
    val gradleDefaultCertFilePath: String = "${project.rootDir}/config/gradle-plugin-default-key.pem"
    val keystoreAlias: String = "my-signing-key"
    val keystorePassword: String = "keystore password"
    val keystoreFilePath: String = "${project.rootDir}/$workspaceDir/signingkeys.pfx"
    val keystoreCertFilePath: String = "${project.rootDir}/$workspaceDir/signingkey1.pem"
    val r3RootCertKeyAlias: String = "digicert-ca"
    val mgmCpiName: String? = networkConfig.getMgmNode()?.cpi
    val mgmCorDappCpiFilePath: String = "${project.rootDir}/$workspaceDir/$corDappCpiName.cpi"
    val certificateAuthorityFilePath: String = "${project.rootDir}/$workspaceDir/ca"

    val restClient: CordaRestClient = CordaRestClient.createHttpClient(URI.create(cordaClusterURL), cordaRestUser, cordaRestPassword, true)
    val logger: Logger = project.logger

    init {
        println("!!!!!!!!!!!!")
        println("artifactoryUsername: $artifactoryUsername")
    }

}
