package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.temporal.ChronoUnit
import java.util.stream.Stream

class FlowTestUtilsTests {

    companion object {
        @JvmStatic
        fun stringProvider(): Stream<String> {
            return Stream.of("param1")
        }
    }

    @Test
    fun `Test test request id generator!`(testInfo: TestInfo){
        val generator = TestRequestIdGenerator(testInfo)
        assertThat(generator.nextId).isEqualTo("Test_test_request_id_generator_-0")
        assertThat(generator.nextId).isEqualTo("Test_test_request_id_generator_-1")

        val secondGenerator = TestRequestIdGenerator("Hello, World! -dash-")
        assertThat(secondGenerator.nextId).isEqualTo("Hello__World__-dash--0")
    }

    @ParameterizedTest
    @ValueSource(strings = ["foo"])
    fun `test test parameters! id generator`(param: String, testInfo: TestInfo){
        println(param)
        val idGenerator = TestRequestIdGenerator(testInfo)
        assertThat(idGenerator.nextId).isEqualTo("test_test_parameters__id_generator-param_1-0")
    }

    @ParameterizedTest
    @MethodSource("stringProvider")
    fun `test test parameters from method! id generator`(param: String, testInfo: TestInfo ){
        println(param)
        val idGenerator = TestRequestIdGenerator(testInfo)
        assertThat(idGenerator.nextId).isEqualTo("test_test_parameters_from_method__id_generator-param_1-0")
    }

    @ParameterizedTest
    @EnumSource(names = arrayOf("DAYS"))
    fun `test test parameters from enum! id generator`(param: ChronoUnit, testInfo: TestInfo){
        println(param)
        val idGenerator = TestRequestIdGenerator(testInfo)
        assertThat(idGenerator.nextId).isEqualTo("test_test_parameters_from_enum__id_generator-param_1-0")
    }

    @Suppress("MaxLineLength")
    @Test
    fun `This is a test with a stupid long name so that we exceed the allowed length of a request id - therefore we need to put a lot more text here because we need to hit over two hundred and fourty characters! Are we there yet (question mark) Not quite, but nearly, this should do it`(testInfo: TestInfo){
        val idGenerator = TestRequestIdGenerator(testInfo)
        assertThat(idGenerator.nextId).matches("ore_text_here_because_we_need_to_hit_over_two_hundred_and_fourty_characters__Are_we_there_yet__question_mark__Not_quite__but_nearly__this_should_do_it-[-\\da-f]{36}-[-\\da-f]{36}-0")
    }
}