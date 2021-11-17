package net.corda.bundle

import com.example.serialization.PrivateBundleItem

data class MainBundleItem(val privateBundleItem: PrivateBundleItem) {
    companion object {
        @JvmStatic
        fun newInstance(): MainBundleItem = MainBundleItem(PrivateBundleItem(5))
    }
}