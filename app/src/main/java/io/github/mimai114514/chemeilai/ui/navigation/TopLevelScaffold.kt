package io.github.mimai114514.chemeilai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    FAVORITES("favorites", "收藏", Icons.Filled.Star),
    NEARBY("nearby", "附近", Icons.Filled.NearMe),
    SETTINGS("settings", "设置", Icons.Filled.Settings),
}

@Composable
fun CheMeiLaiBottomBar(
    currentRoute: String?,
    onSelect: (TopDestination) -> Unit,
) {
    NavigationBar {
        TopDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(destination.icon, contentDescription = destination.label)
                },
                label = { Text(destination.label) },
            )
        }
    }
}
