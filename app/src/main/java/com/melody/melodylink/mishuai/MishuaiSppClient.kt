package com.melody.melodylink.mishuai

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 咪帅耳机 SPP（RFCOMM）客户端
 * SPP UUID: 158627bc-0547-8787-87ba-435ad8571238
 */
class MishuaiSppClient(
    private val onLog: (String) -> Unit = {},
) {
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private val writeLock = Any()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var generation = 0L

    val notifications = incoming.asSharedFlow()
    val isConnected: Boolean get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            close()
            val request = ++generation
            var lastError: Throwable? = null
            val uuid = UUID.fromString(SPP_UUID)
            val label = "MiShuai SPP"
            onLog("MiShuai SPP attempting $label")
            val candidate = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (error: Throwable) {
                lastError = error
                onLog("MiShuai SPP socket creation failed: ${error.javaClass.simpleName}")
                null
            } ?: error("MiShuai SPP socket creation failed")

            try {
                connectWithTimeout(candidate)
                if (request != generation) {
                    candidate.close()
                    error("MiShuai SPP connection cancelled")
                }
                socket = candidate
                onLog("MiShuai SPP connected")
                startReader(candidate, request)
                return@runCatching Unit
            } catch (error: Throwable) {
                lastError = error
                runCatching { candidate.close() }
                onLog("MiShuai SPP failed: ${error.message ?: error.javaClass.simpleName}")
            }
            throw IllegalStateException("MiShuai SPP connection failed", lastError)
        }
    }

    suspend fun write(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val active = socket?.takeIf { it.isConnected } ?: error("MiShuai SPP socket unavailable")
            synchronized(writeLock) {
                active.outputStream.write(data)
                active.outputStream.flush()
            }
        }
    }

    fun close() {
        generation++
        val active = socket
        socket = null
        runCatching { active?.close() }
    }

    private fun connectWithTimeout(candidate: BluetoothSocket) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { candidate.connect() }.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw (error.cause ?: error)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun startReader(active: BluetoothSocket, request: Long) {
        Executors.newSingleThreadExecutor().execute {
            val buffer = ByteArray(1024)
            try {
                val input = active.inputStream
                while (request == generation && active.isConnected) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) incoming.tryEmit(buffer.copyOf(count))
                }
            } catch (_: IOException) {
            } finally {
                if (socket === active) socket = null
                runCatching { active.close() }
                if (request == generation) onLog("MiShuai SPP disconnected")
            }
        }
    }

    companion object {
        const val SPP_UUID = "158627bc-0547-8787-87ba-435ad8571238"
        private const val CONNECT_TIMEOUT_MS = 20_000L
    }
}
