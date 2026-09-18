package io.github.mimai114514.chemeilai.ui.realtime

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mimai114514.chemeilai.data.model.CityLine
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.ui.common.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineRealtimeScreen(
    cityId: String?,
    lineName: String,
    direction: Int?,
    onBack: () -> Unit,
    onStationClick: (direction: Int, stationId: String, stationName: String) -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: LineRealtimeViewModel = viewModel(
        factory = LineRealtimeViewModel.factory(container, cityId, lineName, direction),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var swapTurns by remember { mutableIntStateOf(0) }
    val arrowRotation by animateFloatAsState(
        targetValue = swapTurns * 180f,
        label = "directionArrow",
    )
    val otherDirection = state.directions.firstOrNull { it.direction != state.selectedDirection }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.lineName.ifBlank { lineName.ifBlank { "线路" } },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "收藏线路",
                            tint = if (state.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DirectionPillBar(
                startName = state.selected?.startName,
                endName = state.selected?.endName,
                arrowRotation = arrowRotation,
                swapEnabled = otherDirection != null,
                onSwap = {
                    otherDirection?.let {
                        viewModel.selectDirection(it.direction)
                        swapTurns += 1
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                val realtime = state.realtime
                when {
                    state.loading && realtime == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }

                    realtime == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text("暂无数据") }

                    else -> LineRealtimeContent(
                        cityLine = state.selected,
                        realtime = realtime,
                        direction = state.selectedDirection,
                        onStationClick = onStationClick,
                    )
                }
            }
        }
    }
}

/** 顶部胶囊方向条：左起始站、右终点站，中间圆形箭头按钮，点击旋转 180° 并换向。 */
@Composable
private fun DirectionPillBar(
    startName: String?,
    endName: String?,
    arrowRotation: Float,
    swapEnabled: Boolean,
    onSwap: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = startName?.takeIf { it.isNotBlank() } ?: "起点",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (swapEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = swapEnabled, onClick = onSwap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "切换方向",
                    tint = if (swapEnabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation),
                )
            }
            Text(
                text = endName?.takeIf { it.isNotBlank() } ?: "终点",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LineRealtimeContent(
    cityLine: CityLine?,
    realtime: Realtime,
    direction: Int,
    onStationClick: (Int, String, String) -> Unit,
) {
    val busOrders = realtime.buses.mapNotNull { it.order }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LineMetaCard(cityLine = cityLine, tip = realtime.tip)
        }

        item {
            RealtimeSectionTitle("实时车辆")
        }

        if (realtime.buses.isEmpty()) {
            item {
                Text(
                    text = "暂无车辆信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(realtime.buses) { bus ->
                RealtimeBusCard(bus = bus, targetOrder = null, showDistance = false)
            }
        }

        if (realtime.stations.isNotEmpty()) {
            item {
                RealtimeSectionTitle("站序 · 车辆位置")
            }
            items(realtime.stations, key = { it.order }) { station ->
                RealtimeStationRow(
                    name = station.name,
                    order = station.order,
                    isTarget = false,
                    hasBus = station.order in busOrders,
                    onClick = {
                        station.sId?.let { stationId ->
                            onStationClick(direction, stationId, station.name)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LineMetaCard(cityLine: CityLine?, tip: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val meta = buildList {
                cityLine?.firstTime?.let { add("首班 $it") }
                cityLine?.lastTime?.let { add("末班 $it") }
                cityLine?.price?.let { add(it) }
            }
            Text(
                text = meta.ifEmpty { listOf("暂无首末班信息") }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tip?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
