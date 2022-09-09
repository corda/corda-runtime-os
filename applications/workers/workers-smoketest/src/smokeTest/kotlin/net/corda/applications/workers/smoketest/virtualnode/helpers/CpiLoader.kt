package net.corda.applications.workers.smoketest.virtualnode.helpers

import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.virtualnode.helpers.GroupPolicyUtils.getDefaultStaticNetworkGroupPolicy
import net.corda.cli.plugins.packaging.CreateCpiV2
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

        createTempDirectory()

        CreateCpiV2().apply {
            cpbFileName
        }
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
    }

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
}
