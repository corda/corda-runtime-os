package net.corda.processors.rpc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestUtilsTest {
    @Test
    fun testRemoveDocContentFromJson() {
        val jsonString = """
{
  "openapi" : "3.0.1",
  "info" : {
    "title" : "Corda HTTP RPC API",
    "description" : "All the endpoints for publicly visible Open API calls",
    "version" : "1"
  },
  "servers" : [ {
    "url" : "/api/v1"
  } ],
  "tags" : [ {
    "name" : "CPI Upload API",
    "description" : "CPI Upload management endpoints.",
    "keep": "this"
  } ],
  "nested" : {
    "example": {
        "title" : "Nested",
        "description" : "Example",
        "version" : "1"
      }
  }
}
        """.trimIndent()

        val expectedJson = """
            {
              "openapi" : "3.0.1",
              "info" : {
                "version" : "1"
              },
              "servers" : [ {
                "url" : "/api/v1"
              } ],
              "tags" : [ {
                "keep" : "this"
              } ],
              "nested" : {
                "example" : {
                  "version" : "1"
                }
              }
            }
        """.trimIndent()

        val result = removeDocContentFromJson(jsonString)
        assertThat(result).isEqualTo(expectedJson)
    }
}