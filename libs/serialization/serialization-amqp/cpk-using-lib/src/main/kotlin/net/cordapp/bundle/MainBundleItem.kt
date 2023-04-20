package net.cordapp.bundle

import com.example.serialization.PrivateBundleItem
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SerializationCustomSerializer

@CordaSerializable
data class MainBundleItem(val privateBundleItem: PrivateBundleItem) {
    companion object {
        @JvmStatic
        fun newInstance(): MainBundleItem = MainBundleItem(PrivateBundleItem(5))
    }
}

// Custom serializer to try and serialize `PrivateBundleItem`. It should fail because it targets a private CPK type
class SerializerTargetingPrivateType :
    SerializationCustomSerializer<PrivateBundleItem, SerializerTargetingPrivateType.PrivateBundleItemProxy> {
    override fun toProxy(obj: PrivateBundleItem): PrivateBundleItemProxy =
        PrivateBundleItemProxy(obj.i)

    override fun fromProxy(proxy: PrivateBundleItemProxy): PrivateBundleItem =
        PrivateBundleItem(proxy.i)

    class PrivateBundleItemProxy(val i: Int)
}

// Custom serializer to try and serialize MainBundleItem. It should fail because its proxy type references a private CPK type
class SerializerUsingPrivateProxyType :
    SerializationCustomSerializer<MainBundleItem, SerializerUsingPrivateProxyType.MainBundleItemProxy> {
    override fun toProxy(obj: MainBundleItem): MainBundleItemProxy =
        MainBundleItemProxy(obj.privateBundleItem)

    override fun fromProxy(proxy: MainBundleItemProxy): MainBundleItem =
        MainBundleItem(proxy.i)

    // Proxy references private CPK type
    class MainBundleItemProxy(val i: PrivateBundleItem)
}

// Custom serializer to try and serialize MainBundleItem. This one uses a proxy for private CPK type, so the private CPK type
// is not actually returned and therefore not exposed to AMQP serialization
class SerializerUsingPublicProxyType :
    SerializationCustomSerializer<MainBundleItem, SerializerUsingPublicProxyType.MainBundleItemProxy> {
    override fun toProxy(obj: MainBundleItem): MainBundleItemProxy {
        val privateBundleItem = obj.privateBundleItem
        return MainBundleItemProxy(PrivateBundleItemProxy(privateBundleItem.i))
    }

    override fun fromProxy(proxy: MainBundleItemProxy): MainBundleItem =
        MainBundleItem(PrivateBundleItem(proxy.privateBundleItemProxy.i))

    @CordaSerializable
    class PrivateBundleItemProxy(val i: Int)
    class MainBundleItemProxy(val privateBundleItemProxy: PrivateBundleItemProxy)
}