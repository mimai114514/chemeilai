package io.github.mimai114514.chemeilai.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.CheMeiLaiApp

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as CheMeiLaiApp).container }
}

fun parseEtaMinutes(text: String?): Int? = text?.takeWhile { it.isDigit() }?.toIntOrNull()

fun parseEtaUnit(text: String?): String = text?.dropWhile { it.isDigit() }?.trim().orEmpty()

fun formatDistance(meters: Int?): String? = meters?.let { "$it 米" }
