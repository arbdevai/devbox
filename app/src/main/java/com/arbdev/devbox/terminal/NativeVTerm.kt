package com.arbdev.devbox.terminal

class NativeVTerm(
    var rows: Int,
    var cols: Int
) {
    private var nativePtr: Long = 0

    init {
        nativePtr = nativeCreate(rows, cols)
    }

    fun feed(bytes: ByteArray, offset: Int, length: Int) {
        if (nativePtr != 0L && length > 0) {
            nativeFeed(nativePtr, bytes, offset, length)
        }
    }

    fun resize(newRows: Int, newCols: Int) {
        if (nativePtr != 0L) {
            rows = newRows
            cols = newCols
            nativeResize(nativePtr, newRows, newCols)
        }
    }

    fun clearScrollback() {
        if (nativePtr != 0L) {
            nativeClearScrollback(nativePtr)
        }
    }

    fun getScrollbackSize(): Int {
        return if (nativePtr != 0L) nativeGetScrollbackSize(nativePtr) else 0
    }

    fun isAltScreen(): Boolean {
        return if (nativePtr != 0L) nativeIsAltScreen(nativePtr) else false
    }

    fun getCursor(outPos: IntArray) {
        if (nativePtr != 0L) {
            nativeGetCursor(nativePtr, outPos)
        }
    }

    fun getRowData(lineIdx: Int, outData: IntArray): Int {
        return if (nativePtr != 0L) nativeGetRowData(nativePtr, lineIdx, outData) else 0
    }

    fun getModes(outModes: IntArray) {
        if (nativePtr != 0L) {
            nativeGetModes(nativePtr, outModes)
        }
    }

    fun getTitle(): String? {
        return if (nativePtr != 0L) nativeGetTitle(nativePtr) else null
    }

    fun destroy() {
        if (nativePtr != 0L) {
            val ptr = nativePtr
            nativePtr = 0L
            nativeDestroy(ptr)
        }
    }

    protected fun finalize() {
        destroy()
    }

    companion object {
        init {
            System.loadLibrary("devbox-term")
        }

        @JvmStatic
        private external fun nativeCreate(rows: Int, cols: Int): Long

        @JvmStatic
        private external fun nativeFeed(ptr: Long, bytes: ByteArray, offset: Int, length: Int)

        @JvmStatic
        private external fun nativeResize(ptr: Long, rows: Int, cols: Int)

        @JvmStatic
        private external fun nativeClearScrollback(ptr: Long)

        @JvmStatic
        private external fun nativeGetScrollbackSize(ptr: Long): Int

        @JvmStatic
        private external fun nativeIsAltScreen(ptr: Long): Boolean

        @JvmStatic
        private external fun nativeGetCursor(ptr: Long, outPos: IntArray)

        @JvmStatic
        private external fun nativeGetRowData(ptr: Long, lineIdx: Int, outData: IntArray): Int

        @JvmStatic
        private external fun nativeGetModes(ptr: Long, outModes: IntArray)

        @JvmStatic
        private external fun nativeGetTitle(ptr: Long): String?

        @JvmStatic
        private external fun nativeDestroy(ptr: Long)
    }
}
