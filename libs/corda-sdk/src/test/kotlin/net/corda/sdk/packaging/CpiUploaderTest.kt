package net.corda.sdk.packaging

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class CpiUploaderTest {

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseNoMatch() {
        val client = CordaRestClient.createHttpClient()
        client.cpiClient = mock {
            on { getCpi() } doReturn GetCPIsResponse(
                listOf(
                    CpiMetadata(
                        id = CpiIdentifier(
                            cpiName = "bar",
                            cpiVersion = "999",
                            signerSummaryHash = ""
                        ),
                        cpiFileChecksum = "",
                        cpiFileFullChecksum = "",
                        cpks = emptyList(),
                        groupPolicy = "",
                        timestamp = Instant.now()
                    )
                )
            )
        }
        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseEmpty() {
        val client = CordaRestClient.createHttpClient()
        client.cpiClient = mock {
            on { getCpi() } doReturn GetCPIsResponse(emptyList())
        }
        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsTrue() {
        val client = CordaRestClient.createHttpClient()
        client.cpiClient = mock {
            on { getCpi() } doReturn GetCPIsResponse(
                listOf(
                    CpiMetadata(
                        id = CpiIdentifier(
                            cpiName = "foo",
                            cpiVersion = "1.0",
                            signerSummaryHash = ""
                        ),
                        cpiFileChecksum = "",
                        cpiFileFullChecksum = "",
                        cpks = emptyList(),
                        groupPolicy = "",
                        timestamp = Instant.now()
                    )
                )
            )
        }
        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isTrue
    }

    @Test
    fun testCpiChecksumExistsReturnsFalseNoMatch() {
        val client = CordaRestClient.createHttpClient()
        client.cpiClient = mock {
            on { getCpi() } doReturn GetCPIsResponse(
                listOf(
                    CpiMetadata(
                        id = CpiIdentifier(
                            cpiName = "",
                            cpiVersion = "",
                            signerSummaryHash = ""
                        ),
                        cpiFileChecksum = "123",
                        cpiFileFullChecksum = "",
                        cpks = emptyList(),
                        groupPolicy = "",
                        timestamp = Instant.now()
                    )
                )
            )
        }
        val result = CpiUploader(client).cpiChecksumExists(
            checksum = Checksum("abc")
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiChecksumExistsReturnsTrue() {
        val client = CordaRestClient.createHttpClient()
        client.cpiClient = mock {
            on { getCpi() } doReturn GetCPIsResponse(
                listOf(
                    CpiMetadata(
                        id = CpiIdentifier(
                            cpiName = "",
                            cpiVersion = "",
                            signerSummaryHash = ""
                        ),
                        cpiFileChecksum = "abc",
                        cpiFileFullChecksum = "",
                        cpks = emptyList(),
                        groupPolicy = "",
                        timestamp = Instant.now()
                    )
                )
            )
        }
        val result = CpiUploader(client).cpiChecksumExists(
            checksum = Checksum("abc")
        )
        assertThat(result).isTrue
    }
}
