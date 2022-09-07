package net.corda.application.impl.services.json

import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonMarshallingServiceImplTest {

    @Test
    fun `Can serialize object to json string`() {
        val dto = SimpleDto().also {
            it.name = "n1"
            it.quantity = 1
        }

        val json = JsonMarshallingServiceImpl().format(dto)
        assertThat(json).isEqualTo("{\"name\":\"n1\",\"quantity\":1}")
    }

    @Test
    fun `Can deserialize object from json string`() {
        val dto = JsonMarshallingServiceImpl().parse(
            "{\"name\":\"n1\",\"quantity\":1}", SimpleDto::class.java
        )
        assertThat(dto.name).isEqualTo("n1")
        assertThat(dto.quantity).isEqualTo(1)
    }

    @Test
    fun `Can deserialize list from json string`() {
        val dtoList = JsonMarshallingServiceImpl().parseList(
            "[{\"name\":\"n1\",\"quantity\":1},{\"name\":\"n2\",\"quantity\":2}]", SimpleDto::class.java
        )
        assertThat(dtoList).hasSize(2)
        assertThat(dtoList[0].name).isEqualTo("n1")
        assertThat(dtoList[0].quantity).isEqualTo(1)
        assertThat(dtoList[1].name).isEqualTo("n2")
        assertThat(dtoList[1].quantity).isEqualTo(2)
    }

    @Test
    fun `Can deserialize member X500 name`() {
        val name = JsonMarshallingServiceImpl().parse(
            "\"C=GB, O=Alice, L=London\"",
            MemberX500Name::class.java
        )

        assertThat(name.organisation).isEqualTo("Alice")
    }

    @Test
    fun `Can serialize member X500 name`() {
        val json = JsonMarshallingServiceImpl().format(MemberX500Name.parse("C=GB, O=Alice, L=London"))

        assertThat(json).isEqualTo("\"O=Alice, L=London, C=GB\"")
    }

    class SimpleDto {
        var name: String? = null
        var quantity: Int? = null
    }
}

