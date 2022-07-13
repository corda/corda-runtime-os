package net.corda.db.admin.impl

import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.lang.UnsupportedOperationException

class StreamResourceAccessorTest {

    private val dbChange = mock<DbChange> {
        on { masterChangeLogFiles } doReturn(listOf("fred.xml", "jon.xml"))
        on { changeLogFileList } doReturn(setOf("fred.xml", "jon.xml", "another.xml"))
        on { fetch(any(), any()) } doReturn(mock())
    }
    private val classLoaderResourceAccessor = mock<ResourceAccessor>
    {
        on { openStreams(anyOrNull(), any()) } doReturn(mock())
    }
    private val sra = StreamResourceAccessor(
        "master.xml", dbChange, classLoaderResourceAccessor
    )

    @Test
    fun `when openStreams with master changelog return composite`() {
        val result = sra.openStreams(null, "master.xml")

        assertThat(result.size()).isEqualTo(1)
        assertThat(result.urIs[0].path).isEqualTo("master.xml")
        val fileContent = result.single().bufferedReader().use { it.readText() }
        assertThat(fileContent).contains("include file=\"fred.xml\"")
        assertThat(fileContent).contains("include file=\"jon.xml\"")
    }

    @Test
    fun `when openStreams with master changelog path and relativeTo does a fetch on the changelog rather than creating a master changelog `() {
        val result = sra.openStreams("flintstone", "master.xml")

        assertThat(result.size()).isEqualTo(1)
        verify(dbChange).fetch("master.xml", "flintstone")
    }
    @Test
    fun `when openStreams with liquibase schema URL delegate`() {
        sra.openStreams(null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd")

        verify(classLoaderResourceAccessor).openStreams(null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd")
    }

    @Test
    fun `when openStreams with known changelog fetch it`() {
        sra.openStreams(null, "fred.xml")

        verify(dbChange).fetch("fred.xml", null)
    }

    @Test
    fun `when openStreams with known changelog and relative path then fetch it`() {
        sra.openStreams("flintstone", "fred.xml")

        verify(dbChange).fetch("fred.xml", "flintstone")
    }

    @Test
    fun `when openStreams with null streamPath throw`() {
        assertThrows<UnsupportedOperationException> {
            sra.openStreams(null, null)
        }
    }

    @Test
    fun `when openStreams with null streamPath and non-null relativeTo throw`() {
        assertThrows<UnsupportedOperationException> {
            sra.openStreams("hello", null)
        }
    }

    @Test
    fun `when describeLocations return all file paths`() {
        val locations = sra.describeLocations()
        val dbChangeClassType = dbChange.javaClass.simpleName
        assertThat(locations).containsExactlyInAnyOrder(
            "[$dbChangeClassType]master.xml",
            "[$dbChangeClassType]fred.xml",
            "[$dbChangeClassType]jon.xml",
            "[$dbChangeClassType]another.xml"
        )
    }
}
