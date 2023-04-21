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
        val proxy = toProxy(OptionalSerializer(), opt)
        assertThat(proxy.item).isEqualTo("GenericTestString")
    }

    @Test
    fun `should convert optional without item to empty proxy`() {
        val opt = Optional.ofNullable<String>(null)
        val proxy = toProxy(OptionalSerializer(), opt)
        assertThat(proxy.item).isNull()
    }

    @Test
    fun `should convert proxy without item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy(null)
        val opt = fromProxy<Any>(OptionalSerializer(), proxy)
        assertThat(opt.isPresent).isFalse
    }

    @Test
    fun `should convert proxy with item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy("GenericTestString")
        val opt = fromProxy<String>(OptionalSerializer(), proxy)
        assertThat(opt.get()).isEqualTo("GenericTestString")
    }

    private fun toProxy(serializer: OptionalSerializer, opt: Optional<*>): OptionalSerializer.OptionalProxy {
        val method = serializer::class.java.getDeclaredMethod("toProxy", Optional::class.java).also {
            it.isAccessible = true
        }
        return method.invoke(serializer, opt) as OptionalSerializer.OptionalProxy
    }

    private fun <T> fromProxy(serializer: OptionalSerializer, proxy: OptionalSerializer.OptionalProxy): Optional<T> {
        val method = serializer::class.java.getDeclaredMethod("fromProxy", OptionalSerializer.OptionalProxy::class.java).also {
            it.isAccessible = true
        }
        @Suppress("unchecked_cast")
        return method.invoke(serializer, proxy) as Optional<T>
    }
}
