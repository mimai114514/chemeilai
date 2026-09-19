package io.github.mimai114514.chemeilai.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.StationLineGroup

/** 收藏线路固定使用金黄色徽章（不随主题/动态取色变化）。 */
private val FavoriteBadgeContainer = Color(0xFFFFC107)
private val FavoriteBadgeContent = Color(0xFF3F2E00)

/**
 * 全局换向：不换向时取 ETA 最近的方向，换向后取另一方向（只有一个方向时保持原样）。
 */
fun StationLineGroup.resolveDisplayDirection(reversed: Boolean): LineDirection? {
    val normal = defaultDirection()
    if (!reversed) return normal
    return directions.firstOrNull { it.direction != normal?.direction } ?: normal
}

@Composable
fun StationLineRow(
    group: StationLineGroup,
    reversed: Boolean,
    onClick: (LineDirection) -> Unit,
) {
    val direction = group.resolveDisplayDirection(reversed) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(direction) }
            .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LineBadge(text = group.displayName, isFavorite = group.isFavorite)
            Spacer(Modifier.height(6.dp))
            Text(
                text = directionSummary(direction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        VehicleInfo(
            etaText = direction.etaText,
            status = direction.desc?.takeIf { it.isNotBlank() },
        )
    }
}

/** 开往（终点站）· 下一站 xx；两个站名各最多显示 6 个字。 */
private fun directionSummary(direction: LineDirection): String {
    direction.summaryOverride?.takeIf { it.isNotBlank() }?.let { return it }
    return buildString {
        append("开往 ")
        append(shortStationName(direction.endName) ?: "终点站")
        direction.nextStationName.takeIf { it.isNotBlank() }?.let {
            append(" · 下一站 ")
            append(shortStationName(it))
        }
    }
}

private fun shortStationName(name: String?): String? {
    val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (trimmed.length > MAX_STATION_CHARS) {
        trimmed.take(MAX_STATION_CHARS) + "…"
    } else {
        trimmed
    }
}

private const val MAX_STATION_CHARS = 6

/** 车辆信息区：有 ETA 时显示分钟数（必要时前置状态），没有 ETA 时显示状态文案。 */
@Composable
private fun VehicleInfo(etaText: String?, status: String?) {
    val minutes = parseEtaMinutes(etaText)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (minutes != null && status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 92.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        when {
            minutes != null -> EtaBadge(etaText)
            status != null -> Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 116.dp),
            )

            etaText != null -> Text(
                text = etaText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 116.dp),
            )

            else -> Text(
                text = "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StationLineList(
    stateKey: Any,
    lines: List<StationLineGroup>,
    reversed: Boolean,
    onLineClick: (LineDirection) -> Unit,
    collapsedCount: Int = 3,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val visible = if (expanded) lines else lines.take(collapsedCount)
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        visible.forEach { group ->
            StationLineRow(
                group = group,
                reversed = reversed,
                onClick = onLineClick,
            )
        }
        if (lines.size > collapsedCount) {
            val hiddenCount = lines.size - collapsedCount
            Surface(
                onClick = { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriangleMark(up = expanded)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "$hiddenCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** 自绘实心三角，避免 Material 图标字形的内边距造成左右不对称。 */
@Composable
private fun TriangleMark(up: Boolean) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(width = 10.dp, height = 6.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun LineBadge(text: String, isFavorite: Boolean = false) {
    val container = if (isFavorite) FavoriteBadgeContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isFavorite) FavoriteBadgeContent else MaterialTheme.colorScheme.onPrimaryContainer
    // 宽度自适应文字：短编号占满最小宽度，长名最多到最大宽度后才省略
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(min = MIN_BADGE_WIDTH, max = MAX_BADGE_WIDTH),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val MIN_BADGE_WIDTH = 52.dp
private val MAX_BADGE_WIDTH = 104.dp

@Composable
fun EtaBadge(etaText: String?) {
    val minutes = parseEtaMinutes(etaText)
    Column(horizontalAlignment = Alignment.End) {
        if (minutes != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = minutes.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = parseEtaUnit(etaText).ifBlank { "分钟" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        } else {
            Text(
                text = etaText ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
