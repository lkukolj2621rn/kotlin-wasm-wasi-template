import kotlin.time.Clock
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

fun main() {
    try {
        while (true) {
            val line = readChar() ?: break
            println("Wasm received: $line")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun fdRead(fd: Int, ptr: UInt, arraySize: Int, errnoPtr: UInt): Int


@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
fun readChar(): Char? {
    withScopedMemoryAllocator {
        allocator ->
        val ptr = allocator.allocate(8 + 2 + 1)
        val errnoPtr = ptr + 8
        val charPtr = errnoPtr + 2
        ptr.storeInt(charPtr.address.toInt()) // buf
        (ptr + 4).storeInt(1) // buf_len
        val count = fdRead(0, ptr.address, 1, errnoPtr.address)
        val errno = errnoPtr.loadShort()
        if (errno != 0.toShort())
            throw RuntimeException("Read errno: $errno")
        if (count == 0)
            return null
        if (count != 1)
            throw RuntimeException("Unexpected read count: $count")
        val code = charPtr.loadByte().toUByte()
        if (code >= 128.toUByte())
            throw RuntimeException("Got non-ASCII char: $code")
        return Char(code.toUShort())
    }
}

@WasmImport("wasi_snapshot_preview1", "clock_time_get")
private external fun wasiRawClockTimeGet(clockId: Int, precision: Long, resultPtr: Int): Int

private const val REALTIME = 0
private const val MONOTONIC = 1

@OptIn(UnsafeWasmMemoryApi::class)
fun wasiGetTime(clockId: Int): Long = withScopedMemoryAllocator { allocator ->
    val rp0 = allocator.allocate(8)
    val ret = wasiRawClockTimeGet(
        clockId = clockId,
        precision = 1,
        resultPtr = rp0.address.toInt()
    )
    check(ret == 0) {
        "Invalid WASI return code $ret"
    }
    (Pointer(rp0.address.toInt().toUInt())).loadLong()
}

fun wasiRealTime(): Long = wasiGetTime(REALTIME)

fun wasiMonotonicTime(): Long = wasiGetTime(MONOTONIC)

// We need it to run WasmEdge with the _initialize function
@WasmExport
fun dummy() {}