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
        assertEquals(HttpRpcClientFileUpload(file, "file"), result["file"])
    }

    @Test
    fun `test files from multipart file upload method with a HttpFileUpload and form param`() {
        val stream = "def".byteInputStream()
        val file = HttpFileUpload(stream, "123", "xml", "filename.xml", 123L)
        val result = TestFileUploadAPI::fileUploadWithFormParam.javaMethod!!.filesFrom(arrayOf("abc", file))

        assertNotNull(result["file"])
        assertEquals(HttpRpcClientFileUpload(stream, "filename.xml"), result["file"])
    }
}