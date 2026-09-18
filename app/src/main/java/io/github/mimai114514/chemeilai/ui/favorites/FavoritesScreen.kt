package io.github.mimai114514.chemeilai.ui.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.ui.common.LineBadge
import io.github.mimai114514.chemeilai.ui.common.StationLineRow
import io.github.mimai114514.chemeilai.ui.common.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onStationClick: (Favorite) -> Unit,
    onStationLineClick: (String, LineDirection) -> Unit,
    onLineClick: (FavoriteLineStatus) -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Favorite?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏") },
                actions = {
                    IconButton(onClick = viewModel::toggleDirection) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "全局换向",
                            tint = if (state.reversed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.isEmpty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有收藏，去「附近」或搜索结果里点☆添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.stations.isNotEmpty()) {
                        item { SectionHeader("收藏站点") }
                        items(state.stations, key = { it.id }) { status ->
                            StationCard(
                                status = status,
                                reversed = state.reversed,
                                onClick = { onStationClick(status.favorite) },
                                onLineClick = onStationLineClick,
                                onLongClick = { deleteTarget = status.favorite },
                            )
                        }
                    }
                    if (state.lines.isNotEmpty()) {
                        item { SectionHeader("收藏线路") }
                        items(state.lines, key = { it.favorite.id }) { status ->
                            LineCard(
                                status = status,
                                onClick = { onLineClick(status) },
                                onLongClick = { deleteTarget = status.favorite },
                            )
                        }
                    }
                }
            }

            if (state.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    deleteTarget?.let { favorite ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除收藏？") },
            text = {
                Text(favorite.stationName ?: favorite.lineName ?: favorite.id)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(favorite)
                        deleteTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun StationDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationCard(
    status: FavoriteStationStatus,
    reversed: Boolean,
    onClick: () -> Unit,
    onLineClick: (String, LineDirection) -> Unit,
    onLongClick: () -> Unit,
) {
    val stationId = status.favorite.stationId.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationDot()
                Spacer(Modifier.width(12.dp))
                Text(
                    text = status.favorite.stationName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                status.distanceMeters?.let { distance ->
                    Text(
                        text = "$distance 米",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (status.lines.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    status.lines.forEach { group ->
                        StationLineRow(
                            group = group,
                            reversed = reversed,
                            onClick = { direction -> onLineClick(stationId, direction) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineCard(
    status: FavoriteLineStatus,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val favorite = status.favorite
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(favorite.lineName.orEmpty())
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.stationName ?: status.directionLabel ?: "点击查看线路",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildList {
                    status.directionLabel?.takeIf { status.stationName != null }?.let { add(it) }
                    status.distanceMeters?.let { add("最近 $it 米") }
                    if (status.stationName == null) add("无法定位，点击查看线路")
                }.filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = status.etaText ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}
