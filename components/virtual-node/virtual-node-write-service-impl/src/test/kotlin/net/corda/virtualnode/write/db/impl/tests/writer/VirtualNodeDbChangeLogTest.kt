package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.style.RFC4519Style.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class VirtualNodeDbChangeLogTest {
    private val fakeId = UUID.randomUUID()
    private val masterFile1 = CpkDbChangeLogEntity(
        CpkDbChangeLogKey(
            "CPK1",
            "1.0",
            "hash",
            VirtualNodeDbChangeLog.MASTER_CHANGE_LOG
        ),
        "cpk1-checksum",
        "migration1",
        fakeId
    )
    private val otherFile1 = CpkDbChangeLogEntity(
        CpkDbChangeLogKey(
            "CPK1",
            "1.0",
            "hash",
            "another-one.xml"
        ),
        "cpk1-checksum",
        "migration2",
        fakeId
    )
    private val masterFile2 = CpkDbChangeLogEntity(
        CpkDbChangeLogKey(
            "CPK2",
            "1.0",
            "hash",
            VirtualNodeDbChangeLog.MASTER_CHANGE_LOG
        ),
        "cpk1-checksum",
        "migration3",
        fakeId
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
