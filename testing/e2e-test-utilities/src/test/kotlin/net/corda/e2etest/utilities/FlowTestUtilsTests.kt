package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class FlowTestUtilsTests {

    @Test
    fun `Test test request id generator!`(testInfo: TestInfo){
        val generator = TestRequestIdGenerator(testInfo)
        assertThat(generator.nextId).isEqualTo("Test_test_request_id_generator_-0")
        assertThat(generator.nextId).isEqualTo("Test_test_request_id_generator_-1")

        val secondGenerator = TestRequestIdGenerator("Hello, World! -dash-")
        assertThat(secondGenerator.nextId).isEqualTo("Hello__World__-dash--0")
    }
}