package io.github.mimai114514.chemeilai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.StationLineGroup

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
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LineBadge(text = group.displayName, isFavorite = group.isFavorite)
            Spacer(Modifier.height(4.dp))
            Text(
                text = directionSummary(direction),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        EtaBadge(direction.etaText)
    }
}

/** 方向 + 下一站 +（若有）状态文案，合成一行小字；状态文案保持错误色。 */
@Composable
private fun directionSummary(direction: LineDirection): AnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        withStyle(SpanStyle(color = mutedColor)) {
            append(listOfNotNull(direction.startName, direction.endName).joinToString(" → "))
            direction.nextStationName.takeIf { it.isNotBlank() }?.let {
                append(" · 下一站 ")
                append(it)
            }
            direction.desc?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                withStyle(SpanStyle(color = errorColor)) { append(it) }
            }
        }
    }
}

@Composable
fun LineBadge(text: String, isFavorite: Boolean = false) {
    val container = if (isFavorite) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (isFavorite) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(min = 60.dp, max = 110.dp),
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
