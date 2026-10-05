package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.data.LogLine
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.work.DumpController

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningScreen(vm: MainViewModel) {
    val state by vm.dumpState.collectAsStateWithLifecycle()

    val log = when (val s = state) {
        is DumpController.State.Running -> s.log
        is DumpController.State.Failed -> s.log
        else -> emptyList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex) }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Dumping") },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = { vm.pop() }) { Icon(Icons.Rounded.Close, "Close") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().padding(horizontal = 16.dp)) {
            when (val s = state) {
                is DumpController.State.Running -> Header(s.title, s.stage, s.fraction, running = true)
                is DumpController.State.Done -> {
                    Header(s.title, "Finished", 1f, running = false, done = true)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.refreshDumps(); vm.popTo(Screen.Home); vm.navigate(Screen.DumpDetail(s.recordId)); vm.clearDumpState() }, modifier = Modifier.weight(1f)) {
                            Text("Open dump")
                        }
                        OutlinedButton(onClick = { vm.refreshDumps(); vm.popTo(Screen.Home); vm.clearDumpState() }, modifier = Modifier.weight(1f)) {
                            Text("Done")
                        }
                    }
                }
                is DumpController.State.Failed -> {
                    Header(s.title, s.error, -1f, running = false, failed = true)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.popTo(Screen.Home); vm.clearDumpState() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }
                DumpController.State.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No dump running.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state is DumpController.State.Running) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { vm.cancelDump() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cancel")
                }
            }

            Spacer(Modifier.height(16.dp))
            if (log.isNotEmpty()) {
                Text("Log", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(log) { line -> LogRow(line) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(title: String, subtitle: String, fraction: Float, running: Boolean, done: Boolean = false, failed: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                failed -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                else -> androidx.compose.material3.LoadingIndicator(modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (running) {
            Spacer(Modifier.height(14.dp))
            if (fraction >= 0f) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), gapSize = 4.dp)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), gapSize = 4.dp)
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.level) {
        LogLine.Level.ERROR -> MaterialTheme.colorScheme.error
        LogLine.Level.WARN -> MaterialTheme.colorScheme.tertiary
        LogLine.Level.STEP -> MaterialTheme.colorScheme.primary
        LogLine.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(line.message, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp))
}
