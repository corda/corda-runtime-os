package net.corda.applications.workers.smoketest.virtualnode.helpers

import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.virtualnode.helpers.GroupPolicyUtils.getDefaultStaticNetworkGroupPolicy
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.utilities.deleteRecursively
import net.corda.utilities.readAll
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempDirectory

object CpiLoader {
    private const val groupIdPlaceholder = "group-id-placeholder"
    private fun getInputStream(resourceName: String): InputStream {
        return this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    fun get(resourceName: String, groupId: String) = cpbToCpi(getInputStream(resourceName), groupId)

    fun getRawResource(resourceName: String) = getInputStream(resourceName)

    /** Returns a new input stream
     * Don't use this method when we have actual CPIs
     */
    private fun cpbToCpi(inputStream: InputStream, groupId: String): InputStream {

        val tempDirectory = createTempDirectory()
        try {
            // Save CPB to disk
            val cpbPath = tempDirectory.resolve("cpb.cpb")
            Files.newOutputStream(cpbPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                inputStream.copyTo(it)
            }

            // Save group policy to disk
            val groupPolicyPath = tempDirectory.resolve("groupPolicy")
            val networkPolicyStr = getStaticNetworkPolicy(groupId)
            Files.newBufferedWriter(groupPolicyPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(networkPolicyStr)
            }

            // Save keystore to disk
            val keyStorePath = tempDirectory.resolve("cordadevcodesign.p12")
            Files.newOutputStream(keyStorePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(getKeyStore())
            }

            // Create CPI
            val cpiPath = tempDirectory.resolve("cpi")
            CreateCpiV2().apply {
                cpbFileName = cpbPath.toString()
                cpiName = "test-cordapp"
                cpiVersion = "1.0.0.0-SNAPSHOT"
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyPath.toString()
                outputFileName = cpiPath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStorePath.toString()
                    keyStorePass = "cordacadevpass"
                    keyAlias = "cordacodesign"
                }
            }.run()

            // Read CPI
            return cpiPath.readAll().inputStream()
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun getKeyStore() = javaClass.classLoader.getResourceAsStream("cordadevcodesign.p12")?.use { it.readAllBytes() }
        ?: throw Exception("alice.p12 not found")

    private fun getStaticNetworkPolicy(groupId: String) =
        getDefaultStaticNetworkGroupPolicy(groupId, listOf(X500_ALICE, X500_BOB, X500_CHARLIE, X500_DAVID))
}
