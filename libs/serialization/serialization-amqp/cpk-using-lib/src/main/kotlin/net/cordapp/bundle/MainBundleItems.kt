package net.cordapp.bundle

import com.example.serialization.PrivateBundleItem1
import com.example.serialization.PrivateBundleItem2
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SerializationCustomSerializer

@CordaSerializable
data class MainBundleItem1(val privateBundleItem: PrivateBundleItem1) {
    companion object {
        @JvmStatic
        fun newInstance(): MainBundleItem1 = MainBundleItem1(PrivateBundleItem1(5))
    }
}

@CordaSerializable
data class MainBundleItem2(val privateBundleItem: PrivateBundleItem2) {
    companion object {
        @JvmStatic
        fun newInstance(): MainBundleItem2 = MainBundleItem2(PrivateBundleItem2(5))
    }

    class PrivateBundleItemSerializer :
        SerializationCustomSerializer<PrivateBundleItem2, PrivateBundleItemSerializer.PrivateBundleItemProxy> {
        override fun toProxy(obj: PrivateBundleItem2): PrivateBundleItemProxy =
            PrivateBundleItemProxy(obj.i)

        override fun fromProxy(proxy: PrivateBundleItemProxy): PrivateBundleItem2 =
            PrivateBundleItem2(proxy.i)

        class PrivateBundleItemProxy(val i: Int)
    }
}