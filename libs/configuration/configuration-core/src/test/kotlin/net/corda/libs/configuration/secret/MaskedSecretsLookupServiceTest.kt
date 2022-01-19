package net.corda.libs.configuration.secret

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MaskedSecretsLookupServiceTest {

    @Test
    fun getValueReturnsNothingUseful() {
        val key = mock<Config>()
        assertThat(MaskedSecretsLookupService().getValue(key)).isEqualTo(MaskedSecretsLookupService.MASK_VALUE)
    }
}