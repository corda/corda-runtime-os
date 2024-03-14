package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.cordapp.PrintHelper.writeGroupPolicyToFile
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.sdk.network.GenerateStaticGroupPolicy
import java.io.File

class GroupPolicyHelper {
    companion object {
        private val groupPolicyGenerator = GenerateStaticGroupPolicy()

        private const val ENDPOINT_URL = "http://localhost:1080"
        private const val ENDPOINT_PROTOCOL = 1
    }

    fun createStaticGroupPolicy(targetPolicyFile: File, x500Names: List<String?>) {
        try {
            val members = groupPolicyGenerator.createMembersListFromListOfX500Strings(
                x500Names.filterNotNull(),
                ENDPOINT_URL,
                ENDPOINT_PROTOCOL,
            )
            val groupPolicy = groupPolicyGenerator.generateStaticGroupPolicy(members)
            writeGroupPolicyToFile(targetPolicyFile, groupPolicy)
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Unable to create group policy: ${e.message}", e)
        }
    }
}