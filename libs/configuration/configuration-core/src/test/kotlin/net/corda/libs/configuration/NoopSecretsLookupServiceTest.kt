package net.corda.libs.configuration

import com.typesafe.config.ConfigValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NoopSecretsLookupServiceTest {

    @Test
    fun getValueReturnsNothingUsefull() {
        val key = mock<ConfigValue>()
        assertThat(NoopSecretsLookupService().getValue(key)).isEqualTo("*****")
    }
}