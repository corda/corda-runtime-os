package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VirtualNodeDbChangeLogTest {
    private val masterFile1 = CpkDbChangeLog(
        CpkDbChangeLogIdentifier(
            SecureHash("SHA1", "abc".toByteArray()),
            VirtualNodeDbChangeLog.MASTER_CHANGE_LOG
        ), "migration1"
    )
    private val otherFile1 = CpkDbChangeLog(
        CpkDbChangeLogIdentifier(SecureHash("SHA1", "abc".toByteArray()), "another-one.xml"),
        "migration2"
    )
    private val masterFile2 = CpkDbChangeLog(
        CpkDbChangeLogIdentifier(
            SecureHash("SHA1", "abc".toByteArray()),
            VirtualNodeDbChangeLog.MASTER_CHANGE_LOG
        ), "migration3"
    )

    @Test
    fun `find all master changelog files`() {
        val changeLog = VirtualNodeDbChangeLog(
            listOf(
                masterFile1,
                otherFile1,
                masterFile2,
            )
        )

        val masterFiles = changeLog.masterChangeLogFiles

        assertThat(masterFiles).containsAll(
            listOf(masterFile1.id.filePath, masterFile2.id.filePath)
        )
    }

    @Test
    fun `find all changelog files`() {
        val changeLog = VirtualNodeDbChangeLog(
            listOf(
                masterFile1,
                otherFile1,
                masterFile2,
            )
        )

        val files = changeLog.changeLogFileList

        assertThat(files).containsAll(
            listOf(
                masterFile1.id.filePath,
                otherFile1.id.filePath,
                masterFile2.id.filePath
            )
        )
    }

    @Test
    fun `fetch changelog file contents`() {
        val changeLog = VirtualNodeDbChangeLog(
            listOf(
                masterFile1,
                otherFile1,
            )
        )

        changeLog.fetch(masterFile1.id.filePath).reader().use {
            assertThat(it.readText()).isEqualTo(masterFile1.content)
        }
        changeLog.fetch(otherFile1.id.filePath).reader().use {
            assertThat(it.readText()).isEqualTo(otherFile1.content)
        }
    }

    @Test
    fun `fetch throws when changelog does not exist`() {
        val changeLog = VirtualNodeDbChangeLog(
            listOf(
                masterFile1,
            )
        )

        assertThrows<CordaRuntimeException> {
            changeLog.fetch(otherFile1.id.filePath)
        }
    }
}