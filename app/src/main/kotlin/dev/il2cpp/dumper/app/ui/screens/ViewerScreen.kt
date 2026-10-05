package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.theme.CodeTextStyle
import dev.il2cpp.dumper.app.util.Sharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: MainViewModel, id: String, fileName: String) {
    val record = remember(id) { vm.store.get(id) }
    val file = remember(id, fileName) { record?.let { File(it.dir, fileName) } }
    val context = LocalContext.current

    val lines by produceState(initialValue = emptyList<String>(), file) {
        value = withContext(Dispatchers.IO) {
            val f = file ?: return@withContext listOf("File not found")
            if (f.length() > 24 * 1024 * 1024) listOf("File too large to preview (${f.length() / 1024 / 1024} MB). Use Share to open it elsewhere.")
            else runCatching { f.readLines() }.getOrElse { listOf("Cannot read file: ${it.message}") }
        }
    }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchPos by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(query, lines) {
        matches = if (query.length < 2) emptyList()
        else withContext(Dispatchers.Default) { lines.indices.filter { lines[it].contains(query, true) } }
        matchPos = 0
        if (matches.isNotEmpty()) listState.scrollToItem(matches[0])
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(fileName, fontFamily = FontFamily.Monospace, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { file?.let { Sharing.shareFile(context, it, Sharing.mimeFor(it.name)) } }) { Icon(Icons.Rounded.IosShare, "Share") }
                },
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(tonalElevation = 3.dp) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                        placeholder = { Text("Find in file") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (matches.isNotEmpty()) {
                        Text("${matchPos + 1}/${matches.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
                        IconButton(onClick = {
                            matchPos = (matchPos - 1 + matches.size) % matches.size
                        }) { Icon(Icons.Rounded.ArrowUpward, "Prev") }
                        IconButton(onClick = {
                            matchPos = (matchPos + 1) % matches.size
                        }) { Icon(Icons.Rounded.ArrowDownward, "Next") }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LaunchedEffect(matchPos, matches) { if (matches.isNotEmpty()) listState.scrollToItem(matches[matchPos]) }
        val hScroll = rememberScrollState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(lines.size) { i ->
                val highlighted = matches.isNotEmpty() && i == matches.getOrElse(matchPos) { -1 }
                Text(
                    lines[i].ifEmpty { " " },
                    style = CodeTextStyle,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .horizontalScroll(hScroll),
                    softWrap = false,
                )
            }
        }
    }
}
