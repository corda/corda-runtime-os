package net.corda.bundle

import com.example.serialization.PrivateBundleItem
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class MainBundleItem(val privateBundleItem: PrivateBundleItem) {
    companion object {
        @JvmStatic
        fun newInstance(): MainBundleItem = MainBundleItem(PrivateBundleItem(5))
    }
}