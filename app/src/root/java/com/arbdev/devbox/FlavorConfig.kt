package com.arbdev.devbox

import android.content.Context

object FlavorConfig {
    const val IS_ROOT_FLAVOR = true
    const val FLAVOR_NAME = "root"

    fun getDefaultRootfs(context: Context): String {
        return "/data/local/devbox-chroot"
    }
}
