package io.github.mimai114514.chemeilai.ui.realtime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mimai114514.chemeilai.data.model.BusEta

/** 顶部胶囊方向条：左起始站、右终点站，中间圆形箭头按钮，点击旋转 180° 并换向。 */
@Composable
fun DirectionPillBar(
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
fun RealtimeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** targetOrder 为 null 时（无站点上下文的线路页）不显示相对本站的措辞。 */
@Composable
fun RealtimeBusCard(
    bus: BusEta,
    targetOrder: Int?,
    targetLabel: String = "本站",
    showDistance: Boolean = true,
) {
    val passed = if (targetOrder != null && bus.stationsAway != null) {
        bus.stationsAway < 0
    } else {
        targetOrder != null && bus.order != null && bus.order < targetOrder
    }
    val accent = if (passed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (passed) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsBus,
                contentDescription = null,
                tint = accent,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = busLabel(bus, targetOrder, targetLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val details = buildList {
                    if (!passed && showDistance) bus.distanceMeters?.let { add("距$targetLabel $it 米") }
                    if (!passed) bus.stationName?.takeIf { it.isNotBlank() }?.let { add("停靠 $it") }
                    if (!passed) bus.timeStr?.takeIf { it.isNotBlank() }?.let { add("预计 $it") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!passed) {
                bus.etaMinutes?.let { minutes ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = minutes.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "分钟",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun busLabel(bus: BusEta, targetOrder: Int?, targetLabel: String): String {
    // 通卡数据源直接给「还剩几站」，比拿两个站序相减可靠
    bus.stationsAway?.let { away ->
        return when {
            targetOrder == null && away <= 0 -> "已到站"
            away <= 0 -> "已到$targetLabel"
            away == 1 -> "即将到站"
            else -> "还有 $away 站"
        }
    }
    val order = bus.order ?: return "车辆"
    if (targetOrder == null) return "行驶至第 $order 站"
    val diff = order - targetOrder
    return when {
        diff == 0 -> "已到$targetLabel"
        diff == 1 -> "即将到站"
        diff > 1 -> "还有 $diff 站"
        else -> "已过$targetLabel"
    }
}

@Composable
fun RealtimeStationRow(
    name: String,
    order: Int,
    isTarget: Boolean,
    hasBus: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val background = when {
        isTarget -> MaterialTheme.colorScheme.primaryContainer
        hasBus -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(background, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = order.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            hasBus -> Icon(
                imageVector = Icons.Filled.DirectionsBus,
                contentDescription = "有车",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )

            isTarget -> Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = "本站",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
