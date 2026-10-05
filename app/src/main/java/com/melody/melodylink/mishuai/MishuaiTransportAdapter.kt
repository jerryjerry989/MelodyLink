package com.melody.melodylink.mishuai

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.melody.melodylink.domain.AncMode
import com.melody.melodylink.domain.BatteryPart
import com.melody.melodylink.domain.BatteryValue
import com.melody.melodylink.domain.EarbudsCapabilities
import com.melody.melodylink.domain.EarbudsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class MishuaiTransportAdapter(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnecting()
        fun onConnected(state: EarbudsState)
        fun onStateChanged(state: EarbudsState)
        fun onBatteryState(state: EarbudsState)
        fun onAncWriteResult(success: Boolean, state: EarbudsState?, reason: String)
        fun onDisconnected()
        fun onFailed(reason: String)
        fun onLog(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0)
    private var job: Job? = null
    private var client: MishuaiSppClient? = null
    private var currentState: EarbudsState? = null

    @Volatile
    private var isConnected = false

    private val capabilities = EarbudsCapabilities(
        ancModes = setOf(AncMode.OFF, AncMode.NOISE_CANCELING, AncMode.TRANSPARENCY),
        batteryParts = setOf(BatteryPart.LEFT, BatteryPart.RIGHT, BatteryPart.CASE),
    )

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val request = generation.incrementAndGet()
        job?.cancel()
        job = scope.launch {
            listener.onConnecting()
            val active = MishuaiSppClient { msg -> listener.onLog(msg) }
            client = active

            // 收帧
            launch {
                active.notifications.collect { chunk ->
                    onIncoming(chunk)
                }
            }

            val result = active.connect(device)
            if (request != generation.get()) return@launch

            result.onSuccess {
                isConnected = true
                listener.onLog("MiShuai SPP channel ready")
                // 连上后主动查一次电量和降噪状态
                refreshBattery()
                queryAncState()
            }.onFailure { err ->
                listener.onFailed("MiShuai SPP connection failed: ${err.message}")
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        scope.launch {
            release(false)
        }
    }

    fun refreshBattery() {
        val active = client ?: return
        if (!isConnected) return
        scope.launch {
            active.write(MishuaiProtocol.buildQueryFrame(MishuaiProtocol.QUERY_BATTERY))
        }
    }

    fun queryAncState() {
        val active = client ?: return
        if (!isConnected) return
        scope.launch {
            active.write(MishuaiProtocol.buildQueryFrame(MishuaiProtocol.QUERY_ANC_STATE))
        }
    }

    fun setAncMode(mode: AncMode) {
        val active = client ?: run {
            listener.onAncWriteResult(false, null, "MiShuai SPP not connected")
            return
        }
        if (!isConnected) {
            listener.onAncWriteResult(false, null, "MiShuai SPP not connected")
            return
        }
        scope.launch {
            val frame = MishuaiProtocol.buildAncFrame(mode)
            val result = active.write(frame)
            result.onSuccess {
                // 本地先更新
                val state = currentState?.copy(ancMode = mode) ?: EarbudsState(
                    capabilities = capabilities, ancMode = mode,
                )
                currentState = state
                listener.onAncWriteResult(true, state, "")
                listener.onStateChanged(state)
            }.onFailure { err ->
                listener.onAncWriteResult(false, currentState, err.message ?: "write failed")
            }
        }
    }

    private fun onIncoming(chunk: ByteArray) {
        var buffer = chunk
        while (true) {
            val frame = MishuaiProtocol.tryParse(buffer) ?: return
            handleFrame(frame)
            if (buffer.size <= frame.consumed) return
            buffer = buffer.copyOfRange(frame.consumed, buffer.size)
        }
    }

    private fun handleFrame(frame: MishuaiProtocol.ParsedFrame) {
        listener.onLog("MiShuai frame type=0x${frame.type.toInt().toString(16)} sub=0x${frame.subtype.toInt().toString(16)}")
        when (frame.type) {
            MishuaiProtocol.QUERY_BATTERY -> {
                val battery = MishuaiBatteryParser.parse(frame.payload)
                if (battery.isEmpty()) return
                val state = (currentState ?: EarbudsState(capabilities = capabilities))
                    .copy(battery = battery)
                currentState = state
                listener.onBatteryState(state)
            }
            MishuaiProtocol.QUERY_ANC_STATE -> {
                if (frame.payload.isEmpty()) return
                val mode = MishuaiProtocol.byteToAncMode(frame.payload[0])
                val state = (currentState ?: EarbudsState(capabilities = capabilities))
                    .copy(ancMode = mode)
                currentState = state
                listener.onStateChanged(state)
            }
            MishuaiProtocol.QUERY_DEVICE_NAME -> {
                listener.onLog("MiShuai device name: ${String(frame.payload)}")
            }
        }
    }

    private suspend fun release(notify: Boolean) {
        client?.close()
        client = null
        currentState = null
        isConnected = false
        if (notify) listener.onDisconnected()
    }

    fun releaseResources() {
        disconnect()
        scope.cancel()
    }
}
