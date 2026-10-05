package dev.il2cpp.dumper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Small coloured pill used for metadata version / arch / format tags. */
@Composable
fun Tag(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FlowTags(tags: List<@Composable () -> Unit>) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { tags.forEach { it() } }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        androidx.compose.foundation.layout.Column {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Human readable size. */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

fun relativeTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val diff = System.currentTimeMillis() - epochMs
    val m = diff / 60000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 1440 -> "${m / 60}h ago"
        m < 43200 -> "${m / 1440}d ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
    }
}
