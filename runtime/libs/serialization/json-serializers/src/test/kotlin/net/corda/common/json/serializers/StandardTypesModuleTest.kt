package net.corda.common.json.serializers

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StandardTypesModuleTest {

    val mapper = ObjectMapper().apply {
        registerModule(standardTypesModule())
    }

    @Test
    fun `Can deserialize member X500 name`() {
        val name = mapper.readValue("\"C=GB, O=Alice, L=London\"", MemberX500Name::class.java)

        assertThat(name.organization).isEqualTo("Alice")
    }

    @Test
    fun `Can serialize member X500 name`() {
        val json = mapper.writeValueAsString(MemberX500Name.parse("C=GB, O=Alice, L=London"))

        assertThat(json).isEqualTo("\"O=Alice, L=London, C=GB\"")
    }
}
