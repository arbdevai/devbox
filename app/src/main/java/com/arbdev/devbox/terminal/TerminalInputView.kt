package com.arbdev.devbox.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.arbdev.devbox.util.AppLogger

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
        // Konfigurasi persis seperti Termux TerminalView:
        // Matikan predictive text, dictionary spellcheck, dan auto-correction agar keyboard menjadi pure stream
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    AppLogger.i("TerminalInput", "commitText diterima dari keyboard: '$text'")
                    onCommitText?.invoke(text.toString())
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    AppLogger.i("TerminalInput", "deleteSurroundingText (Backspace): beforeLength=$beforeLength")
                    repeat(beforeLength) { onBackspace?.invoke() }
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                    AppLogger.i("TerminalInput", "sendKeyEvent: keyCode=${event.keyCode}")
                    if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                        onBackspace?.invoke()
                        return true
                    }
                    if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                        onCommitText?.invoke("\r")
                        return true
                    }
                    if (onSendKey?.invoke(event.keyCode) == true) {
                        return true
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onBackspace?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            onCommitText?.invoke("\r")
            return true
        }
        if (onSendKey?.invoke(keyCode) == true) {
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
