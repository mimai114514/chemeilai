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

/** 只有「N分钟…」才算到站分钟数；发车时刻（如 13:50）不应被当成分钟。 */
private val ETA_MINUTES_REGEX = Regex("^(\\d+)分钟")

fun parseEtaMinutes(text: String?): Int? {
    val value = text?.trim().orEmpty()
    return ETA_MINUTES_REGEX.find(value)?.groupValues?.get(1)?.toIntOrNull()
}

fun formatDistance(meters: Int?): String? = meters?.let { "$it 米" }
