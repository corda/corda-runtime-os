package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class FlowTestUtilsTests {

    @Test
    fun `Test test request id generator!`(testInfo: TestInfo){
        val generator = TestRequestIdGenerator(testInfo)
        assertThat(generator.getNextId()).isEqualTo("Test_test_request_id_generator_-0")
    }
}