package com.hydra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hydra.ui.HydraRoot
import com.hydra.ui.HydraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HydraTheme { HydraRoot() } }
    }
}
