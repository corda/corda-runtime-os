package net.corda.internal.serialization.amqp.custom

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Optional
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OptionalSerializerTest {

    @Test
	fun `should convert optional with item to proxy`() {
        val opt = Optional.of("GenericTestString")
        val proxy = OptionalSerializer().toProxy(opt)
        assertThat(proxy.item).isEqualTo("GenericTestString")
    }

    @Test
	fun `should convert optional without item to empty proxy`() {
        val opt = Optional.ofNullable<String>(null)
        val proxy = OptionalSerializer().toProxy(opt)
        assertThat(proxy.item).isNull()
    }

    @Test
	fun `should convert proxy without item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy(null)
        val opt = OptionalSerializer().fromProxy(proxy)
        assertThat(opt.isPresent).isFalse
    }

    @Test
	fun `should convert proxy with item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy("GenericTestString")
        val opt = OptionalSerializer().fromProxy(proxy)
        assertThat(opt.get()).isEqualTo("GenericTestString")
    }
}
