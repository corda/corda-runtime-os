package net.corda.sdk.packaging

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.rest.client.RestClient
import net.corda.rest.client.RestConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class CpiUploaderTest {

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseNoMatch() {
        val mockedCpiUpload = mock<CpiUploadRestResource> {
            on { getAllCpis() } doReturn GetCPIsResponse(
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
        val restConnection = mock<RestConnection<CpiUploadRestResource>> { on { proxy } doReturn mockedCpiUpload }
        val mockedRestClient = mock<RestClient<CpiUploadRestResource>> { on { start() } doReturn restConnection }
        val result = CpiUploader().cpiPreviouslyUploaded(
            restClient = mockedRestClient,
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsFalseEmpty() {
        val mockedCpiUpload = mock<CpiUploadRestResource> {
            on { getAllCpis() } doReturn GetCPIsResponse(
                emptyList()
            )
        }
        val restConnection = mock<RestConnection<CpiUploadRestResource>> { on { proxy } doReturn mockedCpiUpload }
        val mockedRestClient = mock<RestClient<CpiUploadRestResource>> { on { start() } doReturn restConnection }
        val result = CpiUploader().cpiPreviouslyUploaded(
            restClient = mockedRestClient,
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isFalse
    }

    @Test
    fun testCpiPreviouslyUploadedReturnsTrue() {
        val mockedCpiUpload = mock<CpiUploadRestResource> {
            on { getAllCpis() } doReturn GetCPIsResponse(
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
        val restConnection = mock<RestConnection<CpiUploadRestResource>> { on { proxy } doReturn mockedCpiUpload }
        val mockedRestClient = mock<RestClient<CpiUploadRestResource>> { on { start() } doReturn restConnection }
        val result = CpiUploader().cpiPreviouslyUploaded(
            restClient = mockedRestClient,
            cpiName = "foo",
            cpiVersion = "1.0"
        )
        assertThat(result).isTrue
    }
}
