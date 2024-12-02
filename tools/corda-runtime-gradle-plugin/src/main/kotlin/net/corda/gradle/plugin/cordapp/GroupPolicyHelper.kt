package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.cordapp.PrintHelper.writeGroupPolicyToFile
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.sdk.network.GenerateStaticGroupPolicy
import net.corda.v5.base.types.MemberX500Name
import java.io.File

class GroupPolicyHelper {
    companion object {
        private val groupPolicyGenerator = GenerateStaticGroupPolicy()

        private const val ENDPOINT_URL = "http://localhost:1080"
        private const val ENDPOINT_PROTOCOL = 1
    }

    fun createStaticGroupPolicy(targetPolicyFile: File, x500Names: List<MemberX500Name>) {
        try {
            val members = groupPolicyGenerator.createMembersListFromListOfX500Names(
                x500Names,
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