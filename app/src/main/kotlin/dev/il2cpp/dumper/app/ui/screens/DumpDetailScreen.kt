package dev.il2cpp.dumper.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import dev.il2cpp.dumper.app.data.DumpRecord
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.components.FlowTags
import dev.il2cpp.dumper.app.ui.components.StatTile
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.app.ui.components.formatCount
import dev.il2cpp.dumper.app.ui.components.formatSize
import dev.il2cpp.dumper.app.util.Sharing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpDetailScreen(vm: MainViewModel, id: String) {
    val record = remember(id) { vm.store.get(id) }
    val context = LocalContext.current
    if (record == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Dump not found") }
        return
    }
    val scroll = androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.MediumTopAppBar(
                title = { Text(record.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { Sharing.shareDumpZip(context, record.dir, "${record.title}.zip") }) {
                        Icon(Icons.Rounded.IosShare, "Share all")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { InfoCard(record) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(formatCount(record.typeCount), "types", Modifier.weight(1f))
                    StatTile(formatCount(record.methodCount), "methods", Modifier.weight(1f))
                    StatTile(formatCount(record.stringCount), "strings", Modifier.weight(1f))
                }
            }
            item {
                if (record.hasIndex()) {
                    FilledTonalButton(onClick = { vm.navigate(Screen.ClassBrowser(id)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Browse classes, methods & strings")
                    }
                }
            }
            item { Text("Output files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(record.outputFiles(), key = { it.name }) { file ->
                FileRow(file, onOpen = { vm.navigate(Screen.Viewer(id, file.name)) }, onShare = { Sharing.shareFile(context, file, Sharing.mimeFor(file.name)) })
            }
        }
    }
}

@Composable
private fun InfoCard(record: DumpRecord) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            if (record.packageName.isNotEmpty()) {
                Text(record.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
            }
            FlowTags(buildList {
                add { Tag("metadata v${record.metadataVersion}") }
                add { Tag("il2cpp v${record.il2cppVersion}") }
                if (record.unityVersion.isNotEmpty()) add { Tag("Unity ${record.unityVersion}") }
                add { Tag(record.format) }
                add { Tag(record.arch) }
                if (record.isDumped) add { Tag("memory dump") }
                if (record.maybeProtected) add { Tag("protected", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer) }
            })
            Spacer(Modifier.height(10.dp))
            InfoRow("CodeRegistration", "0x" + record.meta.optString("codeRegistration"))
            InfoRow("MetadataRegistration", "0x" + record.meta.optString("metadataRegistration"))
            InfoRow("Output size", formatSize(record.sizeBytes()))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileRow(file: File, onOpen: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Sharing.mimeFor(file.name))) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
    Card(onClick = onOpen, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { saveLauncher.launch(file.name) }) { Icon(Icons.Rounded.Save, "Save") }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.IosShare, "Share") }
        }
    }
}
