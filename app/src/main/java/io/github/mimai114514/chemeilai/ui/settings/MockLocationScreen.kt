package io.github.mimai114514.chemeilai.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mimai114514.chemeilai.data.model.MockSwitchRule
import io.github.mimai114514.chemeilai.ui.common.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockLocationScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: MockLocationViewModel = viewModel(factory = MockLocationViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddRule by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模拟定位") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(title = "定位来源") {
                SettingsSwitchRow(
                    icon = Icons.Outlined.MyLocation,
                    title = "使用模拟定位",
                    subtitle = if (state.effectiveMock) "当前生效：模拟定位" else "当前生效：真实定位",
                    checked = state.useMock,
                    onCheckedChange = viewModel::setUseMock,
                )
            }

            SettingsGroup(title = "模拟坐标") {
                SettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = "当前模拟坐标",
                    subtitle = state.mockLocation?.let { mock ->
                        val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(mock.savedAt))
                        "${"%.6f".format(mock.lat)}, ${"%.6f".format(mock.lng)} · $time"
                    } ?: "尚未保存模拟坐标",
                    onClick = {},
                    trailing = {},
                )
                SettingsRow(
                    icon = Icons.Outlined.MyLocation,
                    title = "保存当前定位",
                    subtitle = "读取真实定位并保存为模拟坐标",
                    onClick = viewModel::saveCurrentLocation,
                    trailing = {},
                )
                SettingsRow(
                    icon = Icons.Outlined.ContentCopy,
                    title = "导出到剪贴板",
                    subtitle = state.mockLocation?.toText() ?: "暂无模拟坐标",
                    onClick = {
                        state.mockLocation?.let {
                            clipboard.setText(AnnotatedString(it.toText()))
                            viewModel.notifyMessage("已复制到剪贴板")
                        }
                    },
                    trailing = {},
                )
                SettingsRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = "从剪贴板导入",
                    subtitle = "支持「纬度,经度」或「备注,纬度,经度」",
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        viewModel.importFromText(text)
                    },
                    trailing = {},
                )
                SettingsRow(
                    icon = Icons.Outlined.Delete,
                    title = "清除模拟坐标",
                    subtitle = null,
                    onClick = viewModel::clearMockLocation,
                    trailing = {},
                )
            }

            SettingsGroup(title = "定时切换") {
                if (state.rules.isEmpty()) {
                    Text(
                        text = "还没有规则。到点后自动在模拟/真实定位之间切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    state.rules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rule.timeText()} · ${if (rule.useMock) "切换到模拟" else "切换到真实"}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = rule.daysText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { viewModel.toggleRule(rule.id, it) },
                            )
                            IconButton(onClick = { viewModel.deleteRule(rule.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "删除规则",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                SettingsRow(
                    icon = Icons.Outlined.Add,
                    title = "添加规则",
                    subtitle = "每天某时间，或星期几的某时间",
                    onClick = { showAddRule = true },
                    trailing = {},
                )
            }

            Text(
                text = "提示：模拟定位只影响本应用的取数坐标。自动切换在每次请求定位时判定，" +
                    "定时规则按「今天已触发的最晚一条」生效。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (showAddRule) {
        AddRuleDialog(
            onDismiss = { showAddRule = false },
            onConfirm = { hour, minute, days, useMock ->
                viewModel.addRule(hour, minute, days, useMock)
                showAddRule = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, days: List<Int>, useMock: Boolean) -> Unit,
) {
    val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var useMock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加定时切换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = timeState)
                Text(
                    text = "重复（不选＝每天）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MockSwitchRule.WEEKDAY_LABELS.forEachIndexed { index, label ->
                        val isoDay = index + 1
                        FilterChip(
                            selected = isoDay in days,
                            onClick = {
                                days = if (isoDay in days) days - isoDay else days + isoDay
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "切换到模拟定位",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = useMock, onCheckedChange = { useMock = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timeState.hour, timeState.minute, days.toList(), useMock) },
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
