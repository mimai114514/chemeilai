package io.github.mimai114514.chemeilai.ui.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.ui.common.StationLineList
import io.github.mimai114514.chemeilai.ui.common.formatDistance
import io.github.mimai114514.chemeilai.ui.common.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onStationClick: (NearbyStop) -> Unit,
    onLineClick: (NearbyStop, LineDirection) -> Unit,
    onPickCity: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: NearbyViewModel = viewModel(factory = NearbyViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("附近", fontWeight = FontWeight.SemiBold) },
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
                    if (state.loading || state.refreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新定位")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "搜索线路或站点")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.manualCity -> ManualCityPanel(
                    cityName = state.cityName.orEmpty(),
                    onSearch = onSearchClick,
                    onUseAuto = viewModel::useAutoLocation,
                )

                state.permissionRequired -> PermissionRequest(
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onPickCity = onPickCity,
                )

                state.loading -> LoadingState()

                state.servicesDisabled || state.error != null -> ErrorState(
                    message = state.error ?: "定位不可用",
                    onRetry = viewModel::refresh,
                    onPickCity = onPickCity,
                )

                state.stops.isEmpty() -> EmptyState(onPickCity = onPickCity)

                else -> StopList(
                    stops = state.stops,
                    reversed = state.reversed,
                    onStationClick = onStationClick,
                    onLineClick = onLineClick,
                )
            }
        }
    }
}

@Composable
private fun ManualCityPanel(
    cityName: String,
    onSearch: () -> Unit,
    onUseAuto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.LocationCity,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "当前城市：$cityName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "手动选择城市时不依赖定位，可直接搜索线路和站点。「附近站点」需要定位。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onSearch, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("去搜索线路 / 站点")
        }
        TextButton(onClick = onUseAuto, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("改用当前定位")
        }
    }
}

@Composable
private fun StopList(
    stops: List<NearbyStop>,
    reversed: Boolean,
    onStationClick: (NearbyStop) -> Unit,
    onLineClick: (NearbyStop, LineDirection) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(stops, key = { it.sId }) { stop ->
            StopCard(
                stop = stop,
                reversed = reversed,
                onStationClick = onStationClick,
                onLineClick = onLineClick,
            )
        }
    }
}

@Composable
private fun StopCard(
    stop: NearbyStop,
    reversed: Boolean,
    onStationClick: (NearbyStop) -> Unit,
    onLineClick: (NearbyStop, LineDirection) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStationClick(stop) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                formatDistance(stop.distanceMeters)?.let { distance ->
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (stop.lines.isNotEmpty()) {
                StationLineList(
                    stateKey = stop.sId,
                    lines = stop.lines,
                    reversed = reversed,
                    onLineClick = { direction -> onLineClick(stop, direction) },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = "正在定位…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onPickCity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("重试")
        }
        TextButton(onClick = onPickCity) {
            Text("手动选择城市")
        }
    }
}

@Composable
private fun EmptyState(onPickCity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "附近没有找到站点",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onPickCity) {
            Text("手动选择城市")
        }
    }
}

@Composable
private fun PermissionRequest(onGrant: () -> Unit, onPickCity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "需要定位权限",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "“车没来”需要位置权限来查找附近的公交站点。也可以手动选择城市，改用搜索。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 20.dp)) {
            Text("授予权限")
        }
        TextButton(onClick = onPickCity) {
            Text("手动选择城市")
        }
    }
}
