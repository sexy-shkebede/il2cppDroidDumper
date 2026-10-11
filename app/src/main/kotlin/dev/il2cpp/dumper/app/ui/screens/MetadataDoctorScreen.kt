package dev.il2cpp.dumper.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.components.StatTile
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.core.extra.MetadataDoctor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataDoctorScreen(vm: MainViewModel, metaUri: String?) {
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf(metaUri) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            uri = picked.toString()
        }
    }

    // Load bytes + diagnose off the main thread. Tri-state so a read failure is distinct from loading.
    val state by produceState<DoctorState>(initialValue = DoctorState.Loading, uri) {
        val u = uri
        value = DoctorState.Loading
        if (u == null) { value = DoctorState.Idle; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(u.toUri()).use { input ->
                    if (input == null) return@use DoctorState.Failed("Couldn't open that file. It may have been moved, or its app needs to download it first.")
                    val bytes = input.readBytes()
                    if (bytes.size > MAX_BYTES) DoctorState.Failed("File is ${bytes.size / 1024 / 1024} MB — too large to analyze here. A real global-metadata.dat is well under 64 MB.")
                    else DoctorState.Loaded(MetadataDoctor.diagnose(bytes))
                }
            } catch (e: Exception) {
                DoctorState.Failed(e.message ?: "Couldn't read that file.")
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val rec = (state as? DoctorState.Loaded)?.diagnosis?.recovery
        if (dest != null && rec != null) {
            runCatching { context.contentResolver.openOutputStream(dest)?.use { it.write(rec.bytes) } }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Metadata Doctor") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is DoctorState.Idle -> {
                    item { Intro() }
                    item {
                        Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Pick global-metadata.dat")
                        }
                    }
                }
                is DoctorState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Analyzing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is DoctorState.Failed -> {
                    item { FailedCard(s.message) }
                    item {
                        Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pick another file")
                        }
                    }
                }
                is DoctorState.Loaded -> {
                    val d = s.diagnosis
                    item { VerdictHero(d) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("%.2f".format(d.entropy), "entropy /8", Modifier.weight(1f))
                            StatTile("%.0f%%".format(d.zeroRatio * 100), "zero bytes", Modifier.weight(1f))
                        }
                    }
                    item { SignalsCard(d) }
                    if (d.verdict == MetadataDoctor.Verdict.VALID) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile(d.types.toString(), "types", Modifier.weight(1f))
                                StatTile(d.methods.toString(), "methods", Modifier.weight(1f))
                            }
                        }
                    }
                    item { AdviceCard(d.advice) }
                    // Recovery was already computed during diagnosis — present it directly, no re-read/re-search.
                    d.recovery?.let { rec ->
                        item { RecoveredCard(rec) { saveLauncher.launch("global-metadata.dat") } }
                    }
                    item {
                        androidx.compose.material3.OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Analyze another file")
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_BYTES = 64L * 1024 * 1024

private sealed interface DoctorState {
    data object Idle : DoctorState
    data object Loading : DoctorState
    data class Failed(val message: String) : DoctorState
    data class Loaded(val diagnosis: MetadataDoctor.Diagnosis) : DoctorState
}

@Composable
private fun FailedCard(message: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Dangerous, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Couldn't read the file", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun Intro() {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Rounded.HealthAndSafety, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text("Why won't it dump?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "When every dumper says “not a valid metadata file”, it almost always means the game encrypted or obfuscated global-metadata.dat. Pick the file and I'll tell you exactly which case you're in and what to do — and auto-recover the simple ones.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun VerdictHero(d: MetadataDoctor.Diagnosis) {
    val icon: ImageVector
    val container: Color
    val content: Color
    when (d.verdict) {
        MetadataDoctor.Verdict.VALID -> {
            icon = Icons.Rounded.CheckCircle; container = MaterialTheme.colorScheme.primaryContainer; content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        MetadataDoctor.Verdict.FULLY_ENCRYPTED, MetadataDoctor.Verdict.NOT_METADATA -> {
            icon = Icons.Rounded.Dangerous; container = MaterialTheme.colorScheme.errorContainer; content = MaterialTheme.colorScheme.onErrorContainer
        }
        else -> {
            icon = Icons.Rounded.Warning; container = MaterialTheme.colorScheme.tertiaryContainer; content = MaterialTheme.colorScheme.onTertiaryContainer
        }
    }
    val bg by animateColorAsState(container, label = "bg")
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = content, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(d.headline, style = MaterialTheme.typography.titleLarge, color = content)
                Text(verdictLabel(d.verdict), style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

private fun verdictLabel(v: MetadataDoctor.Verdict): String = when (v) {
    MetadataDoctor.Verdict.VALID -> "Ready to dump"
    MetadataDoctor.Verdict.UNSUPPORTED_VERSION -> "Genuine file, new version"
    MetadataDoctor.Verdict.TRUNCATED -> "Incomplete file"
    MetadataDoctor.Verdict.HEADER_SCRAMBLED -> "Header fields obfuscated"
    MetadataDoctor.Verdict.HEADER_OBFUSCATED -> "Header obfuscated"
    MetadataDoctor.Verdict.FULLY_ENCRYPTED -> "Encrypted — dump from memory"
    MetadataDoctor.Verdict.NOT_METADATA -> "Not metadata"
}

@Composable
private fun SignalsCard(d: MetadataDoctor.Diagnosis) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Signals", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SignalRow("Magic AF1BB1FA", if (d.magicPresent) "present" else "missing", d.magicPresent)
            if (d.magicPresent) SignalRow("Version field", "${d.versionField}", d.versionSupported)
            SignalRow("File size", "${d.fileSize / 1024} KB", d.fileSize > 0x110)
            Spacer(Modifier.height(8.dp))
            Text("Entropy ${"%.2f".format(d.entropy)} / 8.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (d.entropy / 8.0).toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            if (d.markersFound.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Plaintext names visible:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.markersFound.forEach { Tag(it, container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdviceCard(advice: List<String>) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What to do", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            advice.forEachIndexed { i, line ->
                Row {
                    Text("${i + 1}. ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RecoveredCard(rec: MetadataDoctor.Recovery, onSave: () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(10.dp))
                Text("Recovered valid metadata (v${rec.version})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text("Method: ${rec.method}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save clean global-metadata.dat")
            }
        }
    }
}
