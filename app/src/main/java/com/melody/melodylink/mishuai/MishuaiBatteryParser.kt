package com.melody.melodylink.mishuai

import com.melody.melodylink.domain.BatteryPart
import com.melody.melodylink.domain.BatteryValue

object MishuaiBatteryParser {

    /**
     * 从电量响应的载荷中解析
     * 期望载荷格式：[左耳, 右耳, 盒子]
     */
    fun parse(payload: ByteArray): Map<BatteryPart, BatteryValue> {
        if (payload.size < 3) return emptyMap()
        val parts = listOf(BatteryPart.LEFT, BatteryPart.RIGHT, BatteryPart.CASE)
        val result = mutableMapOf<BatteryPart, BatteryValue>()
        parts.forEachIndexed { index, part ->
            val raw = payload[index].toInt() and 0xFF
            if (raw == 0xFF) return@forEachIndexed  // 无效
            val charging = (raw and 0x80) != 0
            val percent = raw and 0x7F
            result[part] = BatteryValue(percent, charging)
        }
        return result
    }
}
