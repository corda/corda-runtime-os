package net.corda.sdk.packaging

import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.apis.CPIApi
import net.corda.restclient.generated.models.CpiIdentifier
import net.corda.restclient.generated.models.CpiMetadata
import net.corda.restclient.generated.models.GetCPIsResponse
import net.corda.sdk.data.Checksum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class CpiUploaderTest {

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseNoMatch() {
        val mockCpiClient: CPIApi = mock {
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
                        timestamp = Instant.now().toString()
                    )
                )
            )
        }
        val client = CordaRestClient.createHttpClient(cpiClient = mockCpiClient)
        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseEmpty() {
        val mockCpiClient: CPIApi = mock {
            on { getCpi() } doReturn GetCPIsResponse(emptyList())
        }
        val client = CordaRestClient.createHttpClient(cpiClient = mockCpiClient)

        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsTrue() {
        val mockCpiClient: CPIApi = mock {
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
                        timestamp = Instant.now().toString()
                    )
                )
            )
        }
        val client = CordaRestClient.createHttpClient(cpiClient = mockCpiClient)
        val result = CpiUploader(client).cpiPreviouslyUploaded(
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isTrue
    }

    @Test
    fun testCpiChecksumExistsReturnsFalseNoMatch() {
        val mockCpiClient: CPIApi = mock {
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
                        timestamp = Instant.now().toString()
                    )
                )
            )
        }
        val client = CordaRestClient.createHttpClient(cpiClient = mockCpiClient)
        val result = CpiUploader(client).cpiChecksumExists(
            checksum = Checksum("abc")
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiChecksumExistsReturnsTrue() {
        val cpiClient: CPIApi = mock {
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
                        timestamp = Instant.now().toString()
                    )
                )
            )
        }
        val client = CordaRestClient.createHttpClient(cpiClient = cpiClient)
        val result = CpiUploader(client).cpiChecksumExists(
            checksum = Checksum("abc")
        )
        assertThat(result).isTrue
    }
}
