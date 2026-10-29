package com.arbdev.devbox

import android.content.Context
import java.io.File

object FlavorConfig {
    const val IS_ROOT_FLAVOR = false
    const val FLAVOR_NAME = "noroot"

    fun getDefaultRootfs(context: Context): String {
        return File(context.filesDir, "rootfs").absolutePath
    }
}
