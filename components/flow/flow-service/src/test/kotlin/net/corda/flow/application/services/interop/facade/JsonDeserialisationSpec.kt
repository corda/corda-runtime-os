package net.corda.flow.application.services.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.v5.application.interop.facade.FacadeId

class JsonDeserialisationSpec : DescribeSpec({

    val mapper = ObjectMapper().registerKotlinModule().configure(SerializationFeature.INDENT_OUTPUT, true)

    infix fun String.assertDeserialisationFails(expectedMessage: String) {
        shouldThrow<IllegalArgumentException> {
            mapper.readValue(this, FacadeRequestImpl::class.java)
        }.message shouldContain expectedMessage
    }
    describe("The JSON deserialiser") {

        it("fails if no method name is given") {
            """
                {
                    "parameters": {}
                }
            """ assertDeserialisationFails "No 'method' field"
        }

        it("fails if method name is invalid") {
            """
                {
                    "method": "some nonsense"
                }
            """ assertDeserialisationFails "Invalid method id"
        }

        it("succeeds if no parameters are given") {
            mapper.readValue("""
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method"
                }
            """, FacadeRequestImpl::class.java) should {
                it.facadeId shouldBe FacadeId.of("org.corda.test/facades/serialisation/v1.0")
                it.methodName shouldBe "test-method"
                it.inParameters shouldHaveSize 0
            }
        }

        it("fails if a parameter does not declare its type") {
            """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "typeless": {
                            "value": true
                        }
                    }
                }
            """ assertDeserialisationFails "No parameter type given for parameter typeless"
        }

        it("fails if a parameter does not have a value") {
            """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "valueless": {
                            "type": "string"
                        }
                    }
                }
            """ assertDeserialisationFails "No parameter value given for parameter valueless"
        }

        it("fails if boolean parameter does not have boolean value") {
            """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "not-a-real-boolean": {
                            "type": "boolean",
                            "value": "yes"
                        }
                    }
                }
            """ assertDeserialisationFails "Parameter not-a-real-boolean expected to have a boolean value"
        }
    }

    it("fails if decimal parameter does not have decimal value") {
        """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "not-a-real-decimal": {
                            "type": "decimal",
                            "value": "six dozen"
                        }
                    }
                }
            """ assertDeserialisationFails "Parameter not-a-real-decimal expected to have a decimal value"
    }

    it("fails if uuid parameter does not have uuid value") {
        """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "not-a-real-uuid": {
                            "type": "uuid",
                            "value": "my very unique id"
                        }
                    }
                }
            """ assertDeserialisationFails "Parameter not-a-real-uuid expected to have a UUID value"
    }

    it("fails if bytes parameter does not have base64-encoded blob value") {
        """
                {
                    "method": "org.corda.test/facades/serialisation/v1.0/test-method",
                    "parameters": {
                        "not-a-real-blob": {
                            "type": "bytes",
                            "value": "blobby blobby blobby"
                        }
                    }
                }
            """ assertDeserialisationFails "Parameter not-a-real-blob expected to have a Base64-encoded byte blob value"
    }
})