package net.corda.applications.workers.smoketest.virtualnode.helpers

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object CpiLoader {
    private fun createCertificate() = CpiLoader::class.java.classLoader.getResource("certificate.pem")!!.readText()

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
        staticMemberNames: List<String> = emptyList()
    ) {
        zipOutputStream.putNextEntry(ZipEntry("META-INF/GroupPolicy.json"))
        val groupPolicy = mapOf(
            "fileFormatVersion" to 1,
            "groupId" to groupId,
            "registrationProtocol"
                    to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol"
                    to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to
                        staticMemberNames.map {
                            mapOf(
                                "name" to it,
                                "memberStatus" to "ACTIVE",
                                "endpointUrl-1" to "http://localhost:1080",
                                "endpointProtocol-1" to 1
                            )
                        }
                )
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    createCertificate(),
                    createCertificate()
                ),
                "tlsTrustRoots" to listOf(
                    createCertificate()
                ),
                "sessionPki" to "Standard",
                "tlsPki" to "Standard",
                "tlsVersion" to "1.3",
                "protocolMode" to "Authentication_Encryption"
            ),
            "cipherSuite" to mapOf(
                "corda.provider" to "default",
                "corda.signature.provider" to "default",
                "corda.signature.default" to "ECDSA_SECP256K1_SHA256",
                "corda.signature.FRESH_KEYS" to "ECDSA_SECP256K1_SHA256",
                "corda.digest.default" to "SHA256",
                "corda.cryptoservice.provider" to "default"
            )
        )
        ObjectMapper().writeValueAsString(groupPolicy).byteInputStream().use { it.copyTo(zipOutputStream) }
        zipOutputStream.closeEntry()
    }
}
