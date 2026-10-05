package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.il2cpp.dumper.app.data.InstalledApp
import dev.il2cpp.dumper.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: MainViewModel) {
    val state by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) { if (state.apps.isEmpty() && !state.loading) vm.loadApps() }
    LaunchedEffect(snackbar) { snackbar?.let { snackbarHostState.showSnackbar(it); snackbar = null } }

    val filtered = remember(state.apps, state.query) {
        if (state.query.isBlank()) state.apps
        else state.apps.filter { it.label.contains(state.query, true) || it.packageName.contains(state.query, true) }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { Text("Installed apps") })
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            androidx.compose.material3.OutlinedTextField(
                value = state.query,
                onValueChange = vm::setAppsQuery,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.il2cppOnly, onClick = { vm.loadApps(il2cppOnly = !state.il2cppOnly) },
                    label = { Text("IL2CPP only") }, leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp)) })
                FilterChip(selected = state.includeSystem, onClick = { vm.loadApps(includeSystem = !state.includeSystem) },
                    label = { Text("System apps") })
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(if (state.il2cppOnly) "Scanning apps for IL2CPP…" else "Loading apps…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(app, onClick = {
                            vm.startAppDump(app) { msg -> snackbar = msg }
                        })
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(app.packageName)
            val bmp = android.graphics.Bitmap.createBitmap(
                d.intrinsicWidth.coerceIn(1, 144), d.intrinsicHeight.coerceIn(1, 144), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height); d.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    ListItem(
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${app.packageName}${if (app.versionName.isNotEmpty()) "  ·  ${app.versionName}" else ""}",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, null, Modifier.size(44.dp))
                else Icon(Icons.Rounded.Search, null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
