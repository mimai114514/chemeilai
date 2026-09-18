package io.github.mimai114514.chemeilai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mimai114514.chemeilai.ui.navigation.CheMeiLaiNavHost
import io.github.mimai114514.chemeilai.ui.theme.CheMeiLaiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheMeiLaiTheme {
                CheMeiLaiNavHost()
            }
        }
    }
}
