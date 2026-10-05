package dev.il2cpp.dumper.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.data.DumpInput
import dev.il2cpp.dumper.app.data.DumpRecord
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.components.FlowTags
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.app.ui.components.formatCount
import dev.il2cpp.dumper.app.ui.components.relativeTime
import dev.il2cpp.dumper.app.ui.theme.VersionAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val dumps by vm.dumps.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startInputDump(DumpInput.Content(uri))
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("Il2Cpp Dumper") },
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroActions(
                    onPickApk = { pickApk.launch(arrayOf("*/*")) },
                    onApps = { vm.navigate(Screen.Apps) },
                    onDiff = { vm.navigate(Screen.DiffPicker) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent dumps", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (dumps.isNotEmpty()) Text("${dumps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (dumps.isEmpty()) {
                item { EmptyDumps() }
            } else {
                items(dumps, key = { it.id }) { record ->
                    DumpCard(record, onOpen = { vm.navigate(Screen.DumpDetail(record.id)) }, onDelete = { vm.deleteDump(record.id) })
                }
            }
        }
    }
}

@Composable
private fun HeroActions(onPickApk: () -> Unit, onApps: () -> Unit, onDiff: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            onClick = onApps,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Android, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dump an installed game", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Scans your apps for IL2CPP automatically", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniAction(Modifier.weight(1f), Icons.Rounded.FolderZip, "Pick APK / XAPK", "or libil2cpp bundle", onPickApk)
            MiniAction(Modifier.weight(1f), Icons.Rounded.CompareArrows, "Diff dumps", "compare versions", onDiff)
        }
    }
}

@Composable
private fun MiniAction(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyDumps() {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("No dumps yet", style = MaterialTheme.typography.titleMedium)
            Text("Pick a game above to create your first dump.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpCard(record: DumpRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (record.packageName.isNotEmpty()) {
                        Text(record.packageName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(10.dp))
            FlowTags(buildList {
                add { Tag("metadata v${record.metadataVersion}", container = VersionAccents.forVersion(record.metadataVersion).copy(alpha = 0.22f), content = MaterialTheme.colorScheme.onSurface) }
                add { Tag(record.arch) }
                if (record.isDumped) add { Tag("memory dump", icon = Icons.Rounded.Memory) }
                if (record.maybeProtected) add { Tag("protected", icon = Icons.Rounded.Shield, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer) }
                add { Tag("${formatCount(record.typeCount)} types") }
                add { Tag("${formatCount(record.methodCount)} methods") }
            })
            Spacer(Modifier.height(6.dp))
            Text(relativeTime(record.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
