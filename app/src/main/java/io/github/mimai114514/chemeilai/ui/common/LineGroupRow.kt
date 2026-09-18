package io.github.mimai114514.chemeilai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            .heightIn(min = 76.dp)
            .clickable { onClick(direction) }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
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

/** 开往（终点站）· 下一站 xx */
private fun directionSummary(direction: LineDirection): String = buildString {
    append("开往 ")
    append(direction.endName?.takeIf { it.isNotBlank() } ?: "终点站")
    direction.nextStationName.takeIf { it.isNotBlank() }?.let {
        append(" · 下一站 ")
        append(it)
    }
}

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

            else -> Text(
                text = "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LineBadge(text: String, isFavorite: Boolean = false) {
    val container = if (isFavorite) FavoriteBadgeContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isFavorite) FavoriteBadgeContent else MaterialTheme.colorScheme.onPrimaryContainer
    // 宽度按 3~6 个字符计算，短编号也占满最小宽度，超长省略
    val charCount = text.length.coerceIn(3, 6)
    val badgeWidth = (charCount * 12).dp + 12.dp
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(badgeWidth),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
