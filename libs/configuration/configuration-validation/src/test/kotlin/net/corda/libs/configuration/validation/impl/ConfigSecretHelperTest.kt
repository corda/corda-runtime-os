package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigSecretHelperTest {

    private val passwordsObject = """
                {
                    "passwords": {
                        "foo": [1, 2, 3.14],
                        "bar": false
                    }
                }"""

    private val input = """
       {
        "testString": "hello",
        "testReference": {
            "foo": [1, 2, 3.14],
            "bar": "test",
            "bool": true,
            "bar.secret": "1111111111111",
            "secret": $passwordsObject
        }
       }
    """.trimIndent()



    private val helper = ConfigSecretHelper()

    @Test
    fun `test secrets`() {
        val inputNode = convertJSONToNode(input)!!
        val secrets = helper.hideSecrets(inputNode)

        assertThat(inputNode["testReference"]["secret"].textValue()).isEqualTo(MaskedSecretsLookupService.MASK_VALUE)
        assertThat(inputNode["testReference"]["bar.secret"].textValue()).isEqualTo(MaskedSecretsLookupService.MASK_VALUE)
        assertThat(inputNode["testReference"]["bar"].textValue()).isEqualTo("test")

        helper.insertSecrets(inputNode, secrets)

        val secretsNode = convertJSONToNode(passwordsObject)!!

        assertThat(inputNode["testReference"]["secret"]).isEqualTo(secretsNode)
        assertThat(inputNode["testReference"]["bar.secret"].textValue()).isEqualTo("1111111111111")
        assertThat(inputNode["testReference"]["bar"].textValue()).isEqualTo("test")
    }

    private fun convertJSONToNode(json: String?): JsonNode? {
        val mapper = ObjectMapper()
        return mapper.readTree(json)
    }
}
