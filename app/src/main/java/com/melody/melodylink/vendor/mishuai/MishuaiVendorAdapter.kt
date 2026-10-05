package com.melody.melodylink.vendor.mishuai

import com.melody.melodylink.domain.DeviceIdentity
import com.melody.melodylink.domain.DeviceMatch
import com.melody.melodylink.domain.Vendor
import com.melody.melodylink.mishuai.config.MishuaiDeviceCatalog
import com.melody.melodylink.vendor.VendorAdapter

class MishuaiVendorAdapter : VendorAdapter {
    override val vendor = Vendor.XIAOMI   // 先用 XIAOMI
    override fun match(identity: DeviceIdentity): DeviceMatch? =
        MishuaiDeviceCatalog.find(identity)?.let {
            DeviceMatch(vendor, it.route.profile.id, it.confidence)
        }
}
