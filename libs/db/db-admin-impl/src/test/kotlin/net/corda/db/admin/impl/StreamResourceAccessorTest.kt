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
import org.mockito.kotlin.verify
import java.lang.UnsupportedOperationException

class StreamResourceAccessorTest {

    private val dbChange = mock<DbChange> {
        on { masterChangeLogFiles } doReturn(listOf("fred.xml", "jon.xml"))
        on { changeLogFileList } doReturn(setOf("fred.xml", "jon.xml", "another.xml"))
        on { fetch(any()) } doReturn(mock())
    }
    private val classLoaderResourceAccessor = mock<ResourceAccessor>
    {
        on { getAll(anyOrNull()) } doReturn(mock())
    }
    private val sra = StreamResourceAccessor(
        "master.xml", dbChange, classLoaderResourceAccessor
    )

    @Test
    fun `when getAll with master changelog return composite`() {
        val result = sra.getAll( "master.xml")

        assertThat(result.size).isEqualTo(1)
        assertThat(result[0].uri.path).isEqualTo("master.xml")
        val fileContent = result.single().openInputStream().bufferedReader().use { it.readText() }
        assertThat(fileContent).contains("include file=\"fred.xml\"")
        assertThat(fileContent).contains("include file=\"jon.xml\"")
    }

    @Test
    fun `when getAll with liquibase schema URL delegate`() {
        sra.getAll("http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd")

        verify(classLoaderResourceAccessor)
            .getAll("http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd")
    }

    @Test
    fun `when getAll with known changelog fetch it`() {
        sra.getAll("fred.xml")

        verify(dbChange).fetch("fred.xml")
    }

    @Test
    fun `when getAll with null streamPath throw`() {
        assertThrows<UnsupportedOperationException> {
            sra.getAll(null)
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

    @Test
    fun `resolveSibling test`(){
        val sibling = sra.getAll("jon.xml").single().resolveSibling("another.xml")
        assertThat(sibling.path).isEqualTo("another.xml")
    }
}
