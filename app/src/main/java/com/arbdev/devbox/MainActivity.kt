package com.arbdev.devbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arbdev.devbox.ui.DevBoxApp
import com.arbdev.devbox.ui.theme.DevBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevBoxTheme {
                DevBoxApp()
            }
        }
    }
}
