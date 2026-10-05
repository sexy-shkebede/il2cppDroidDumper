package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.components.StatTile
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.app.util.Sharing
import dev.il2cpp.dumper.core.extra.DumpDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffPickerScreen(vm: MainViewModel) {
    val dumps by vm.dumps.collectAsStateWithLifecycle()
    val withIndex = remember(dumps) { dumps.filter { it.hasIndex() } }
    var oldId by remember { mutableStateOf<String?>(null) }
    var newId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Compare dumps") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            if (oldId != null && newId != null && oldId != newId) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.DiffResult(oldId!!, newId!!)) },
                    text = { Text("Compare") },
                    icon = { Icon(Icons.Rounded.IosShare, null) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (withIndex.size < 2) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("You need at least two dumps with an index.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Old version", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(withIndex, key = { "o" + it.id }) { r ->
                PickRow(r.title, "metadata v${r.metadataVersion} · ${r.arch}", selected = oldId == r.id) { oldId = r.id }
            }
            item { Text("New version", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
            items(withIndex, key = { "n" + it.id }) { r ->
                PickRow(r.title, "metadata v${r.metadataVersion} · ${r.arch}", selected = newId == r.id) { newId = r.id }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffResultScreen(vm: MainViewModel, oldId: String, newId: String) {
    val context = LocalContext.current
    val result by produceState<DumpDiff.Result?>(initialValue = null, oldId, newId) {
        value = withContext(Dispatchers.Default) {
            val o = vm.store.get(oldId)?.loadIndex() ?: return@withContext null
            val n = vm.store.get(newId)?.loadIndex() ?: return@withContext null
            DumpDiff.compare(o, n)
        }
    }
    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Diff") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    result?.let { r -> IconButton(onClick = { Sharing.shareText(context, DumpDiff.report(r), "diff.txt") }) { Icon(Icons.Rounded.IosShare, "Share") } }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val r = result
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("+${r.addedTypes.size}", "types added", Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                    StatTile("-${r.removedTypes.size}", "types removed", Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${r.movedMethods}", "methods moved", Modifier.weight(1f))
                    StatTile("${r.fields.size}", "fields changed", Modifier.weight(1f))
                }
            }
            item { Text("Method changes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(r.methods.take(2000)) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(m.kind.name, container = when (m.kind) {
                                DumpDiff.ChangeKind.ADDED -> MaterialTheme.colorScheme.primaryContainer
                                DumpDiff.ChangeKind.REMOVED -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            }, content = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            Text(m.typeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.signature, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (m.kind == DumpDiff.ChangeKind.MOVED) {
                            Text("0x${java.lang.Long.toHexString(m.oldRva).uppercase()} → 0x${java.lang.Long.toHexString(m.newRva).uppercase()}",
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
