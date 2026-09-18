package io.github.mimai114514.chemeilai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.StationLineGroup

/** 同一线路当前展示的方向：优先用用户选择的方向，否则用默认（ETA 最近）。 */
fun StationLineGroup.resolveDirection(selectedDirection: Int?): LineDirection? =
    selectedDirection?.let { target -> directions.firstOrNull { it.direction == target } }
        ?: defaultDirection()

@Composable
fun StationLineRow(
    group: StationLineGroup,
    selectedDirection: Int?,
    onSwapDirection: () -> Unit,
    onClick: (LineDirection) -> Unit,
) {
    val direction = group.resolveDirection(selectedDirection) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(direction) }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(group.displayName)
        if (group.isFavorite) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "已收藏",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(direction.startName, direction.endName).joinToString(" → "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "下一站 ${direction.nextStationName.ifBlank { "—" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            direction.desc?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        EtaBadge(direction.etaText)
        if (group.directions.size > 1) {
            IconButton(onClick = onSwapDirection) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = "切换方向",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun LineBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(max = 150.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
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
