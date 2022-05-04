package net.corda.httprpc.test

import java.io.InputStream
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.test.utls.Checksum

class TestFileUploadImpl : TestFileUploadAPI, PluggableRPCOps<TestFileUploadAPI> {

    override fun upload(file: InputStream): String {
        return Checksum.generateChecksum(file)
    }

    override fun uploadWithName(name: String, file: InputStream): String {
        return "$name,${Checksum.generateChecksum(file)}"
    }

    override fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String {
        return Checksum.generateChecksum(file)
    }

    override fun fileUpload(file: HttpFileUpload): String {
        return Checksum.generateChecksum(file.content)
    }

    override fun fileUpload(file1: HttpFileUpload, file2: HttpFileUpload): String {
        return Checksum.generateChecksum(file1.content) + "," + Checksum.generateChecksum(file2.content)
    }

    override fun fileUploadObjectList(files: List<HttpFileUpload>): String {
        val result = StringBuilder()

        files.map {
            result.append(Checksum.generateChecksum(it.content))
            result.append(",")
        }

        return result.toString()
    }

    override fun multiInputStreamFileUpload(file1: InputStream, file2: InputStream): String {
        return Checksum.generateChecksum(file1) + "," + Checksum.generateChecksum(file2)
    }

    override val targetInterface: Class<TestFileUploadAPI>
        get() = TestFileUploadAPI::class.java

    override val protocolVersion = 1
}