package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.network.VNodeHelper
import net.corda.sdk.data.Checksum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChecksumWriterTest {

    @Test
    fun testChecksumCanBeWrittenAndRead() {
        val checksumIn = Checksum("123456")
        val targetFile = kotlin.io.path.createTempFile("checksum").toFile()
        DeployCpiHelper().writeChecksumToFile(checksum = checksumIn, cpiChecksumFilePath = targetFile.absolutePath)
        val checksumOut = VNodeHelper().readCpiChecksumFromFile(targetFile.absolutePath)
        assertThat(checksumOut).isEqualTo(checksumIn)
    }
}