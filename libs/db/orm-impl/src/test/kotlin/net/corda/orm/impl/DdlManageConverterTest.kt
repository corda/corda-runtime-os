package net.corda.orm.impl

import net.corda.orm.DdlManage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DdlManageConverterTest {

    fun convertParameters(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(DdlManage.UPDATE, "update"),
            Arguments.of(DdlManage.CREATE, "create"),
            Arguments.of(DdlManage.VALIDATE, "validate"),
            Arguments.of(DdlManage.NONE, "none"),
        )
    }

    @ParameterizedTest
    @MethodSource("convertParameters")
    fun convert(enumValue: DdlManage, stringValue: String) {
        assertThat(enumValue.convert()).isEqualTo(stringValue)
    }
}
