package com.dorobistro.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dorobistro.inventory.ui.DoroApp
import com.dorobistro.inventory.ui.theme.DoroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoroTheme {
                DoroApp()
            }
        }
    }
}
