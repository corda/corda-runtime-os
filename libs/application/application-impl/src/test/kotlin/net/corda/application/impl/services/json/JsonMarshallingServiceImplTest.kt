package net.corda.application.impl.services.json

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonMarshallingServiceImplTest {

    @Test
    fun `Can serialize object to json string`() {
        val dto = SimpleDto().also {
            it.name = "n1"
            it.quantity = 1
        }

        val json = JsonMarshallingServiceImpl().formatJson(dto)
        assertThat(json).isEqualTo("{\"name\":\"n1\",\"quantity\":1}")
    }

    @Test
    fun `Can deserialize object from json string`() {
        val dto = JsonMarshallingServiceImpl().parseJson(
            "{\"name\":\"n1\",\"quantity\":1}", SimpleDto::class.java
        )
        assertThat(dto.name).isEqualTo("n1")
        assertThat(dto.quantity).isEqualTo(1)
    }

    @Test
    fun `Can deserialize list from json string`() {
        val dtoList = JsonMarshallingServiceImpl().parseJsonList(
            "[{\"name\":\"n1\",\"quantity\":1},{\"name\":\"n2\",\"quantity\":2}]", SimpleDto::class.java
        )
        assertThat(dtoList).hasSize(2)
        assertThat(dtoList[0].name).isEqualTo("n1")
        assertThat(dtoList[0].quantity).isEqualTo(1)
        assertThat(dtoList[1].name).isEqualTo("n2")
        assertThat(dtoList[1].quantity).isEqualTo(2)
    }

    class SimpleDto {
        var name: String? = null
        var quantity: Int? = null
    }
}

