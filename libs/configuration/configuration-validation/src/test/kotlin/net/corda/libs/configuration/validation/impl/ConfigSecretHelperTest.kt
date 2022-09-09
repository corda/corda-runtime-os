package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.configuration.SmartConfig
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
            "bool": true,
            "hidden": {
                "configSecret": $passwordsObject
            }
        }
       }
    """.trimIndent()

    private val helper = ConfigSecretHelper()

    @Test
    fun `test secrets`() {
        val inputNode = convertJSONToNode(input)!!
        val secrets = helper.hideSecrets(inputNode)

        assertThat(inputNode["testReference"]["hidden"].textValue()).isEqualTo(MaskedSecretsLookupService
            .MASK_VALUE)

        helper.insertSecrets(inputNode, secrets)

        val secretsNode = convertJSONToNode(passwordsObject)!!

        assertThat(inputNode["testReference"]["hidden"][SmartConfig.SECRET_KEY]).isEqualTo(secretsNode)
    }

    private fun convertJSONToNode(json: String?): JsonNode? {
        val mapper = ObjectMapper()
        return mapper.readTree(json)
    }
}
