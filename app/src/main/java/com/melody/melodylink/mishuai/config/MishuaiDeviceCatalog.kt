package com.melody.melodylink.mishuai.config

import com.melody.melodylink.domain.DeviceIdentity
import com.melody.melodylink.domain.DeviceProfile
import com.melody.melodylink.domain.BatteryPart
import com.melody.melodylink.domain.EarbudsCapabilities
import com.melody.melodylink.domain.AncMode
import com.melody.melodylink.domain.Vendor

/**
 * 咪帅设备目录 —— 用于设备匹配
 *
 * 注意：咪帅耳机没有稳定的设备名关键词，主要靠 MAC 地址或 SPP UUID 匹配。
 * 如果你的咪帅耳机有固定名称（比如 "MiShuai xxx"），把关键词填进 KEYWORDS。
 */
object MishuaiDeviceCatalog {

    private val KEYWORDS = listOf(
        "mishuai",
        "咪帅",
    )

    val DEFAULT_PROFILE = DeviceProfile(
        vendor = Vendor.XIAOMI,   // 先用 XIAOMI，后面可以加 MIShuai
        id = "mishuai_default",
        displayName = "MiShuai Earbuds",
        capabilities = EarbudsCapabilities(
            ancModes = setOf(AncMode.OFF, AncMode.NOISE_CANCELING, AncMode.TRANSPARENCY),
            batteryParts = setOf(BatteryPart.LEFT, BatteryPart.RIGHT, BatteryPart.CASE),
        ),
    )

    fun find(identity: DeviceIdentity): Match? {
        val name = identity.bluetoothName?.lowercase() ?: return null
        val hit = KEYWORDS.any { name.contains(it) }
        if (!hit) return null
        return Match(route = Route(profile = DEFAULT_PROFILE), confidence = 80)
    }

    data class Match(val route: Route, val confidence: Int)
    data class Route(val profile: DeviceProfile)
}
