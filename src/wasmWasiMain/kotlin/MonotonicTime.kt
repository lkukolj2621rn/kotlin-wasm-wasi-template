import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

fun main() {
    try {
        while (true) {
            val line = readLine() ?: break
            println("Wasm received: $line")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@ExperimentalWasmInterop
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun fdRead(fd: Int, ptr: UInt, arraySize: Int, countPtr: UInt): Int


@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
fun readChar(): Char? {
    withScopedMemoryAllocator {
        allocator ->
        val ptr = allocator.allocate(8 + 4 + 1)
        val countPtr = ptr + 8
        val charPtr = countPtr + 4
        ptr.storeInt(charPtr.address.toInt()) // buf
        (ptr + 4).storeInt(1) // buf_len
        val errno = fdRead(0, ptr.address, 1, countPtr.address)
        val count = countPtr.loadInt()
        if (errno != 0)
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

fun readLine(): String? {
    val list = mutableListOf<Char>()
    while (true) {
        val char = readChar() ?: break
        if (char == '\n')
            break
        list.add(char)
    }
    if (list.isEmpty())
        return null
    return list.toCharArray().concatToString()
}

// We need it to run WasmEdge with the _initialize function
@Suppress("unused")
@OptIn(ExperimentalWasmInterop::class)
@WasmExport
fun dummy() {}