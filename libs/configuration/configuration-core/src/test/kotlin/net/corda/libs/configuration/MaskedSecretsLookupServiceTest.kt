package net.corda.libs.configuration

import com.typesafe.config.ConfigValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MaskedSecretsLookupServiceTest {

    @Test
    fun getValueReturnsNothingUseful() {
        val key = mock<ConfigValue>()
        assertThat(MaskedSecretsLookupService().getValue(key)).isEqualTo("*****")
    }
}