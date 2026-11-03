package com.example.ytxfeaturelab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ytxfeaturelab.gesture.GestureSettings
import com.example.ytxfeaturelab.theme.ThemeSettings
import com.example.ytxfeaturelab.theme.YtxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ThemeSettings.init(this)
        GestureSettings.init(this)

        setContent {
            YtxTheme {
                App()
            }
        }
    }
}
