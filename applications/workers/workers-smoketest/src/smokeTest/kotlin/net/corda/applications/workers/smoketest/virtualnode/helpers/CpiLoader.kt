package net.corda.applications.workers.smoketest.virtualnode.helpers

import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.virtualnode.helpers.GroupPolicyUtils.getDefaultStaticNetworkGroupPolicy
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.utilities.deleteRecursively
import net.corda.utilities.write
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
            val cpbPath = tempDirectory.resolve("cpb")
            cpbPath.write { inputStream.copyTo(it) }

            // Save group policy to disk
            val groupPolicyPath = tempDirectory.resolve("groupPolicy")
            groupPolicyPath.write { it.bufferedWriter().write(getStaticNetworkPolicy(groupId)) }

            // Save keystore to disk
            val keyStorePath = tempDirectory.resolve("alice.p12")
            keyStorePath.write { it.write(getKeyStore()) }

            // Create CPI
            val cpiPath = tempDirectory.resolve("cpi")
            CreateCpiV2().apply {
                cpbFileName = cpbPath.toString()
                cpiName = "cpi name"
                cpiVersion = "1.0.0.0-SNAPSHOT"
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyPath.toString()
                outputFileName = cpiPath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStorePath.toString()
                    keyStorePass = "cordadevpass"
                    keyAlias = "alice"
                }

            }.run()

            val bytes = ByteArrayOutputStream().use { byteStream ->
                ZipOutputStream(byteStream).use { zout ->
                    val zin = ZipInputStream(inputStream)
                    var zipEntry: ZipEntry?
                    while (zin.nextEntry.apply { zipEntry = this } != null) {
                        zout.apply {
                            putNextEntry(zipEntry!!)
                            zin.copyTo(zout)
                            closeEntry()
                        }
                    }
                    addGroupPolicy(zout, groupId)
                }
                byteStream.toByteArray()
            }
            return bytes.inputStream()
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun getKeyStore() = javaClass.classLoader.getResourceAsStream("alice.p12")?.use { it.readAllBytes() }
        ?: throw Exception("alice.p12 not found")

    private fun addGroupPolicy(
        zipOutputStream: ZipOutputStream,
        groupId: String,
        staticMemberNames: List<String> = listOf(
            X500_ALICE,
            X500_BOB,
            X500_CHARLIE,
            X500_DAVID
        )
    ) {
        zipOutputStream.putNextEntry(ZipEntry("META-INF/GroupPolicy.json"))
        val groupPolicy = getDefaultStaticNetworkGroupPolicy(
            groupId,
            staticMemberNames
        )
        groupPolicy.byteInputStream().use { it.copyTo(zipOutputStream) }
        zipOutputStream.closeEntry()
    }

    private fun getStaticNetworkPolicy(groupId: String) = getDefaultStaticNetworkGroupPolicy(
        groupId,
        listOf(
            X500_ALICE,
            X500_BOB,
            X500_CHARLIE,
            X500_DAVID
        ))
        .reader().use { it.readText() }
        .replace(groupIdPlaceholder, groupId)
}
