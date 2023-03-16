package net.corda.cpi.upload.endpoints.v1

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

internal class EndpointTypeConvertersTest {

    fun cpk() : CpkMetadata {
        val id = CpkIdentifier("cpk", "1.0", parseSecureHash("DONT_CARE:1234"))
        return CpkMetadata(
            cpkId = id,
            manifest = mock(),
            mainBundle = "MainBundle",
            libraries = listOf("LIBRARY_ONE"),
            cordappManifest = mock(),
            type = CpkType.CORDA_API,
            fileChecksum = parseSecureHash("DONT_CARE:1234"),
            cordappCertificates = emptySet(),
            timestamp = Instant.now()
        )
    }

    @Test
    fun `CpiMetadata toEndpointType`() {
        val id = CpiIdentifier("abc", "1.0", parseSecureHash("DONT_CARE:1234"))
        val expectedHexString = "1234567890AB"
        val hash = parseSecureHash("LONG_ENOUGH:$expectedHexString")
        val cpks = listOf(cpk())
        val groupPolicy = "{}"
        val obj = CpiMetadata(id, hash, cpks, groupPolicy, 99, Instant.now())
        val endpoint = obj.toEndpointType()
        assertThat(endpoint.cpiFileChecksum).isEqualTo(expectedHexString)
        assertThat(endpoint.cpiFileFullChecksum).isEqualTo(expectedHexString)
    }

    @Test
    fun `CpiMetadata toEndpointType with long file hash`() {
        val id = CpiIdentifier("abc", "1.0", parseSecureHash("DONT_CARE:1234"))
        val expectedHexString = "1234567890AB"
        val longHexString = "${expectedHexString}CDEF1234567890ABCDEF1234567890"
        val hash = parseSecureHash("LONG:$longHexString")
        val cpks = listOf(cpk())
        val groupPolicy = "{}"
        val obj = CpiMetadata(id, hash, cpks, groupPolicy, 99, Instant.now())
        val endpoint = obj.toEndpointType()
        assertThat(endpoint.cpiFileChecksum).isEqualTo(expectedHexString)
        assertThat(endpoint.cpiFileFullChecksum).isEqualTo(longHexString)
    }

    @Test
    fun `CpiMetadata toEndpointType with short file hash`() {
        val id = CpiIdentifier("abc", "1.0", parseSecureHash("DONT_CARE:1234"))
        val expectedHexString = "123456"
        val shortHexString = "${expectedHexString}"
        val hash = parseSecureHash("LONG:$shortHexString")
        val cpks = listOf(cpk())
        val groupPolicy = "{}"
        val obj = CpiMetadata(id, hash, cpks, groupPolicy, 99, Instant.now())
        val endpoint = obj.toEndpointType()
        assertThat(endpoint.cpiFileChecksum).isEqualTo(expectedHexString)
        assertThat(endpoint.cpiFileFullChecksum).isEqualTo(shortHexString)
    }
}
