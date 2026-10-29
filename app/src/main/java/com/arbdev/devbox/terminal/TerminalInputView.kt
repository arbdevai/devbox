package com.arbdev.devbox.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Native Android View dengan BaseInputConnection murni (Arsitektur Input standar Termux).
 * Menggantikan Compose BasicTextField yang menyebabkan race condition dan delay pada IME virtual.
 * Menghubungkan FlorisBoard/Gboard langsung ke stream PTY secara instan (0ms latency).
 */
class TerminalInputView(context: Context) : View(context) {

    var onCommitText: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSendKey: ((Int) -> Boolean)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    onCommitText?.invoke(text.toString())
                }
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // Untuk keyboard seperti Gboard/Samsung yang menggunakan composing text:
                // Commit langsung setiap input teks agar stream PTY menerima karakter secara real-time
                if (!text.isNullOrEmpty()) {
                    onCommitText?.invoke(text.toString())
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { onBackspace?.invoke() }
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                    if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                        onBackspace?.invoke()
                        return true
                    }
                    if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                        if (event.isShiftPressed) {
                            onCommitText?.invoke("\u001B[13;2u")
                        } else if (event.isCtrlPressed) {
                            onCommitText?.invoke("\u001B[13;5u")
                        } else {
                            onCommitText?.invoke("\r")
                        }
                        return true
                    }
                    if (onSendKey?.invoke(event.keyCode) == true) {
                        return true
                    }
                    // Jika event adalah karakter teks yang bisa diketik (misal physical keyboard),
                    // kirim kodenya langsung tanpa memanggil super.sendKeyEvent agar tidak double
                    val unicode = event.unicodeChar
                    if (unicode > 0 && !Character.isISOControl(unicode)) {
                        onCommitText?.invoke(unicode.toChar().toString())
                        return true
                    }
                }
                // Jika ACTION_UP untuk DEL/ENTER, konsumsi agar tidak diteruskan ke onKeyUp
                if (event != null && (event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    return true
                }
                return false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Hanya tangani keydown fisik jika belum ditangani oleh sendKeyEvent
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onBackspace?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event?.isShiftPressed == true) {
                onCommitText?.invoke("\u001B[13;2u")
            } else if (event?.isCtrlPressed == true) {
                onCommitText?.invoke("\u001B[13;5u")
            } else {
                onCommitText?.invoke("\r")
            }
            return true
        }
        if (onSendKey?.invoke(keyCode) == true) {
            return true
        }
        val unicode = event?.unicodeChar ?: 0
        if (unicode > 0 && !Character.isISOControl(unicode)) {
            onCommitText?.invoke(unicode.toChar().toString())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun requestKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}
