package net.corda.httprpc.client.processing

import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.test.TestFileUploadAPI

class FileParametersResolverTest {
    @Test
    fun `test files from multipart file upload method with an input stream`() {
        val file = "def".byteInputStream()
        val result = TestFileUploadAPI::uploadWithName.javaMethod!!.filesFrom(arrayOf("abc", file))

        assertNotNull(result["file"])
        assertEquals(RestClientFileUpload(file, "file"), result["file"]!!.first())
    }

    @Test
    fun `test files from multipart file upload method with a HttpFileUpload and form param`() {
        val stream = "def".byteInputStream()
        val file = HttpFileUpload(stream, "filename.xml")
        val result = TestFileUploadAPI::fileUploadWithFormParam.javaMethod!!.filesFrom(arrayOf("abc", file))

        assertNotNull(result["file"])
        assertEquals(RestClientFileUpload(stream, "filename.xml"), result["file"]!!.first())
    }

    @Test
    fun `test files from multipart file upload method with a list of HttpFileUploads`() {
        val stream1 = "def".byteInputStream()
        val stream2 = "ghi".byteInputStream()

        val file1 = HttpFileUpload(stream1, "filename1.xml")
        val file2 = HttpFileUpload(stream2, "filename2.xml")

        val result = TestFileUploadAPI::fileUploadObjectList.javaMethod!!.filesFrom(arrayOf(listOf(file1, file2)))

        val files = result["files"]
        assertNotNull(files)
        assertEquals(2, files.size)
        assertEquals(stream1, files[0].content)
        assertEquals("filename1.xml", files[0].fileName)
        assertEquals(stream2, files[1].content)
        assertEquals("filename2.xml", files[1].fileName)
    }
}