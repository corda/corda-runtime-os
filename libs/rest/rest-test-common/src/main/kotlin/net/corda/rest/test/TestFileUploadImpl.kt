package net.corda.rest.test

import java.io.InputStream
import net.corda.rest.HttpFileUpload
import net.corda.rest.PluggableRestResource
import net.corda.rest.test.utils.ChecksumUtil

class TestFileUploadImpl : TestFileUploadAPI, PluggableRestResource<TestFileUploadAPI> {

    override fun upload(file: InputStream): String {
        return ChecksumUtil.generateChecksum(file)
    }

    override fun uploadWithName(name: String, file: InputStream): String {
        return "$name, ${ChecksumUtil.generateChecksum(file)}"
    }

    override fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String {
        return ChecksumUtil.generateChecksum(file)
    }

    override fun fileUpload(file: HttpFileUpload): String {
        return ChecksumUtil.generateChecksum(file.content)
    }

    override fun fileUpload(file1: HttpFileUpload, file2: HttpFileUpload): String {
        return ChecksumUtil.generateChecksum(file1.content) + ", " + ChecksumUtil.generateChecksum(file2.content)
    }

    override fun fileUploadWithFormParam(formParam: String, file: HttpFileUpload): String {
        return "formParam, ${ChecksumUtil.generateChecksum(file.content)}"
    }

    override fun fileUploadObjectList(files: List<HttpFileUpload>): String {
        val checksums = files.map {
            ChecksumUtil.generateChecksum(it.content)
        }

        return checksums.joinToString()
    }

    override fun multiInputStreamFileUpload(file1: InputStream, file2: InputStream): String {
        return ChecksumUtil.generateChecksum(file1) + ", " + ChecksumUtil.generateChecksum(file2)
    }

    override fun fileUploadWithQueryParam(tenant: String, file: HttpFileUpload): String {
        return "$tenant, ${ChecksumUtil.generateChecksum(file.content)}"
    }

    override fun fileUploadWithPathParam(tenant: String, file: HttpFileUpload): String {
        return "$tenant, ${ChecksumUtil.generateChecksum(file.content)}"
    }

    override fun fileUploadWithNameInAnnotation(file: HttpFileUpload): String {
        return ChecksumUtil.generateChecksum(file.content)
    }

    override val targetInterface: Class<TestFileUploadAPI>
        get() = TestFileUploadAPI::class.java

    override val protocolVersion = 1
}