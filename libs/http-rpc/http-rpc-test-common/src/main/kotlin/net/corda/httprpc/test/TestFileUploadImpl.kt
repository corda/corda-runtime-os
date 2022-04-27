package net.corda.httprpc.test

import java.io.InputStream
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRPCOps

class TestFileUploadImpl : TestFileUploadAPI, PluggableRPCOps<TestFileUploadAPI> {

    override fun upload(file: InputStream): String {
        return "File uploaded via input stream"
    }

    override fun uploadWithName(fileName: String, file: InputStream): String {
        return "File $fileName uploaded via input stream and string parameter"
    }

    override fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String {
        return "File $fileName uploaded via input stream and string parameter without annotations"
    }

    override fun fileUpload(file: HttpFileUpload): String {
        return "File ${file.fileName} uploaded with size ${file.size}, extension ${file.extension} and content type ${file.contentType}"
    }

    override fun fileUpload(file1: HttpFileUpload, file2: HttpFileUpload): String {
        return "Two files uploaded with names: [${file1.fileName}, ${file2.fileName}]."
    }

    override fun fileUploadObjectList(files: List<HttpFileUpload>): String {
        return "List of size ${files.size} HttpFileUpload files uploaded."
    }

    override fun multiInputStreamFileUpload(file1: InputStream, file2: InputStream): String {
        return "File1 and File2 input stream files uploaded."
    }

    override val targetInterface: Class<TestFileUploadAPI>
        get() = TestFileUploadAPI::class.java

    override val protocolVersion = 1
}