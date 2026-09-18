package io.github.mimai114514.chemeilai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mimai114514.chemeilai.ui.common.rememberAppContainer
import io.github.mimai114514.chemeilai.ui.navigation.CheMeiLaiNavHost
import io.github.mimai114514.chemeilai.ui.theme.CheMeiLaiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheMeiLaiTheme {
                val container = rememberAppContainer()
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    startDestination = container.repository.startDestination()
                }
                startDestination?.let { destination ->
                    CheMeiLaiNavHost(startDestination = destination)
                }
            }
        }
    }
}
