package com.example.picsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.picsearch.ui.theme.PicSearchTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picsearch.ui.screen.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PicSearchTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}