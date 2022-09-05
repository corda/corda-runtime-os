package net.corda.cordapptestutils.internal.tools

import net.corda.cordapptestutils.RequestData
import net.corda.cordapptestutils.internal.RPCRequestDataWrapperFactory
import net.corda.cordapptestutils.internal.testflows.HelloFlow
import net.corda.cordapptestutils.internal.testflows.InputMessage
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

    @Test
    fun `should be able to serialize and deserialize RequestData`() {
        val requestId = "r1"
        val flowClass = HelloFlow::class.java
        val requestBody = InputMessage("IRunCordapps")
        val requestData : RequestData = RPCRequestDataWrapperFactory().create(requestId, flowClass, requestBody)

        val service = SimpleJsonMarshallingService()

        val jsonified = service.format(requestData)
        val result = service.parse(jsonified, RequestData::class.java)

        assertThat(result, `is`(requestData))
    }

    data class Record(val name : String, val age : Int? = null, val children : List<Record> = listOf())
}