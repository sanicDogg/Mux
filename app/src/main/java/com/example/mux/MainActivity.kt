package com.example.mux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.mux.ui.main.MainAdaptiveHub
import com.example.mux.ui.theme.MuxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAdaptiveHub()
                }
            }
        }
    }
}

@Preview(name = "Phone", device = "spec:width=360dp,height=800dp", showBackground = true)
@Preview(name = "Foldable", device = "spec:width=673dp,height=841dp", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp", showBackground = true)
@Composable
fun MainAdaptiveHubPreview() {
    MuxTheme {
        MainAdaptiveHub()
    }
}
