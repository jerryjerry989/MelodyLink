package com.melody.melodylink.mishuai

import com.melody.melodylink.domain.AncMode

/**
 * 咪帅耳机 SPP 协议
 *
 * 发送帧: 00 2C 01 00 01 [模式字节]
 * 查询帧: 00 27 01 00 01 [类型字节]
 * 响应帧: 00 27 02 00 [载荷长度] [类型] [子类型] [数据...]
 */
object MishuaiProtocol {

    // ---- 命令字 ----
    private const val CMD_SET_ANC: Byte = 0x2C
    private const val CMD_QUERY: Byte = 0x27
    private const val CMD_RESPONSE: Byte = 0x27

    // ---- 查询类型 ----
    const val QUERY_BATTERY: Byte = 0x01
    const val QUERY_DEVICE_NAME: Byte = 0x03
    const val QUERY_ANC_STATE: Byte = 0x07

    // ---- 模式字节 ----
    private const val MODE_WIND_NR: Byte = 0x00
    private const val MODE_DEEP_ANC: Byte = 0x01
    private const val MODE_TRANSPARENCY: Byte = 0x02
    private const val MODE_NC_OFF: Byte = 0x03

    /** 构建降噪设置帧：00 2C 01 00 01 [模式字节] */
    fun buildAncFrame(mode: AncMode): ByteArray = byteArrayOf(
        0x00, CMD_SET_ANC, 0x01, 0x00, 0x01, ancModeToByte(mode)
    )

    /** 构建查询帧：00 27 01 00 01 [类型字节] */
    fun buildQueryFrame(queryType: Byte): ByteArray = byteArrayOf(
        0x00, CMD_QUERY, 0x01, 0x00, 0x01, queryType
    )

    fun ancModeToByte(mode: AncMode): Byte = when (mode) {
        AncMode.NOISE_CANCELING -> MODE_DEEP_ANC
        AncMode.TRANSPARENCY -> MODE_TRANSPARENCY
        AncMode.AMBIENT_SOUND -> MODE_TRANSPARENCY
        AncMode.OFF -> MODE_NC_OFF
    }

    fun byteToAncMode(byte: Byte): AncMode = when (byte) {
        MODE_WIND_NR -> AncMode.NOISE_CANCELING
        MODE_DEEP_ANC -> AncMode.NOISE_CANCELING
        MODE_TRANSPARENCY -> AncMode.TRANSPARENCY
        MODE_NC_OFF -> AncMode.OFF
        else -> AncMode.OFF
    }

    /** 尝试解析一帧，成功返回 ParsedFrame，不是完整帧返回 null */
    fun tryParse(bytes: ByteArray): ParsedFrame? {
        if (bytes.size < 7) return null
        if (bytes[0] != 0x00.toByte()) return null
        if (bytes[1] != CMD_RESPONSE) return null
        if (bytes[2] != 0x02.toByte()) return null

        val payloadLen = bytes[4].toInt() and 0xFF
        val totalLen = 7 + payloadLen
        if (bytes.size < totalLen) return null

        val type = bytes[5]
        val subtype = bytes[6]
        val payload = if (payloadLen > 0) bytes.copyOfRange(7, totalLen) else ByteArray(0)
        return ParsedFrame(type, subtype, payload, totalLen)
    }

    data class ParsedFrame(
        val type: Byte,
        val subtype: Byte,
        val payload: ByteArray,
        val consumed: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedFrame) return false
            return type == other.type && subtype == other.subtype &&
                    payload.contentEquals(other.payload) && consumed == other.consumed
        }
        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + subtype.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + consumed
            return result
        }
    }
}
