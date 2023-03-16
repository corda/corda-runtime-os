package net.corda.p2p.gateway.messaging.http

import io.netty.util.concurrent.GlobalEventExecutor
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIterable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress

class RandomSelectionAddressResolverTest {
    @Test
    @Timeout(30)
    fun `random selection ends up selecting all the resolved IP`() {
        val resolver = RandomSelectionAddressResolver()
        val executor = GlobalEventExecutor.INSTANCE
        val address = InetSocketAddress.createUnresolved("www.shared_ips.net", 1001)
        val allIps = mutableSetOf<String>()
        eventually(
            duration = 20.seconds,
            waitBetween = 0.seconds
        ) {
            val resolved = resolver.getResolver(executor)
                .resolve(address)
                .get()
            allIps.add(
                resolved.address.address.joinToString(".")
            )

            assertThatIterable(allIps).hasSize(9)
                .containsExactlyInAnyOrder(
                    "1.2.3.1",
                    "1.2.3.2",
                    "1.2.3.3",
                    "1.2.3.4",
                    "1.2.3.5",
                    "1.2.3.6",
                    "1.2.3.7",
                    "1.2.3.8",
                    "1.2.3.9",
                )
        }
    }

    @Test
    @Timeout(30)
    fun `random selection can return different addresses`() {
        val firsResolver = RandomSelectionAddressResolver()
        val secondResolver = RandomSelectionAddressResolver()
        val executor = GlobalEventExecutor.INSTANCE
        val address = InetSocketAddress.createUnresolved("www.shared_ips.net", 1001)
        eventually(
            duration = 20.seconds,
            waitBetween = 0.seconds
        ) {
            val resolvedByFirst = firsResolver.getResolver(executor)
                .resolve(address)
                .get()
            val resolvedBySecond = secondResolver.getResolver(executor)
                .resolve(address)
                .get()

            assertThat(resolvedByFirst).isNotEqualTo(resolvedBySecond)
        }
    }

    @Test
    @Timeout(30)
    fun `random selection can return the same address`() {
        val firsResolver = RandomSelectionAddressResolver()
        val secondResolver = RandomSelectionAddressResolver()
        val executor = GlobalEventExecutor.INSTANCE
        val address = InetSocketAddress.createUnresolved("www.shared_ips.net", 1001)
        eventually(
            duration = 20.seconds,
            waitBetween = 0.seconds
        ) {
            val resolvedByFirst = firsResolver.getResolver(executor)
                .resolve(address)
                .get()
            val resolvedBySecond = secondResolver.getResolver(executor)
                .resolve(address)
                .get()

            assertThat(resolvedByFirst).isEqualTo(resolvedBySecond)
        }
    }
}
