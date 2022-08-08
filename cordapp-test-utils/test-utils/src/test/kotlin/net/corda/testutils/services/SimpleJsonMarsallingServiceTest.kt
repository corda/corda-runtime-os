package net.corda.testutils.services

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.jupiter.api.Test

class SimpleJsonMarsallingServiceTest {

    @Test
    fun `should be able to create an object and list of objects from valid Json data`() {
        val input = """
            {
                "name" : "Clint",
                "age" : 42,
                "children" : [{"name" : "Lila", "age" : 14}, {"name" : "Cooper"}, {"name" : "Nathaniel"}]
            }
        """.trimIndent()
        val record = SimpleJsonMarshallingService().parse(input, Record::class.java)
        assertThat(record, `is`(
            Record("Clint", 42, listOf(
                Record("Lila", 14),
                Record("Cooper"),
                Record("Nathaniel")
            ))
        ))
    }

    data class Record(val name : String, val age : Int? = null, val children : List<Record> = listOf())
}