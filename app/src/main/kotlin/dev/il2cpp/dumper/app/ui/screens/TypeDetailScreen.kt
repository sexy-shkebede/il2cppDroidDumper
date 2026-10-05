package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.app.util.Sharing
import dev.il2cpp.dumper.core.extra.CodeGen
import dev.il2cpp.dumper.core.extra.DumpIndex
import dev.il2cpp.dumper.core.extra.MethodEntry
import dev.il2cpp.dumper.core.extra.TypeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeDetailScreen(vm: MainViewModel, id: String, typeDefIndex: Int) {
    val record = remember(id) { vm.store.get(id) }
    val indexState = produceState<DumpIndex?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) { runCatching { record?.loadIndex() }.getOrNull() }
    }
    val index = indexState.value
    val type = index?.types?.firstOrNull { it.typeDefIndex == typeDefIndex }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var sheetMethod by remember { mutableStateOf<MethodEntry?>(null) }
    var typeSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(type?.name ?: "Type", maxLines = 1, fontFamily = FontFamily.Monospace) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { typeSheet = true }) { Icon(Icons.Rounded.Code, "Generate") }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (index == null || type == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Text(type.fullName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                if (type.extends.isNotEmpty()) Text(": ${type.extends}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(type.kind.name.lowercase())
                    Tag("${type.fields.size} fields")
                    Tag("${type.methods.size} methods")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (type.fields.isNotEmpty()) {
                item { SectionHeader("Fields") }
                items(type.fields.size) { i ->
                    val f = type.fields[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${f.modifiers}${f.typeName} ${f.name}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            if (f.defaultValue != null) Text("= ${f.defaultValue}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (!f.isConst && f.offset >= 0) Tag("0x" + Integer.toHexString(f.offset).uppercase(), container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (type.methods.isNotEmpty()) {
                item { SectionHeader("Methods") }
                items(type.methods.size) { i ->
                    val m = type.methods[i]
                    Row(Modifier.fillMaxWidth().clickable { sheetMethod = m }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.signature(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (m.hasBody) Tag("0x" + java.lang.Long.toHexString(m.rva).uppercase(), container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        }
    }

    val m = sheetMethod
    if (m != null && index != null && type != null) {
        ModalBottomSheet(onDismissRequest = { sheetMethod = null }, sheetState = rememberModalBottomSheetState()) {
            CodeGenSheet(
                title = m.name + "()",
                subtitle = if (m.hasBody) "RVA 0x" + java.lang.Long.toHexString(m.rva).uppercase() else "no body",
                snippets = listOf(
                    "Frida (raw)" to { CodeGen.fridaHook(index, type, m) },
                    "frida-il2cpp-bridge" to { CodeGen.fridaBridgeHook(index, type, m) },
                    "C++ hook (Dobby/ShadowHook)" to { CodeGen.cppHook(index, type, m) },
                ),
                onCopy = { clipboard.setText(AnnotatedString(it)) },
                onShare = { text, name -> Sharing.shareText(context, text, name) },
            )
        }
    }
    if (typeSheet && index != null && type != null) {
        ModalBottomSheet(onDismissRequest = { typeSheet = false }, sheetState = rememberModalBottomSheetState()) {
            CodeGenSheet(
                title = type.name,
                subtitle = "whole type",
                snippets = listOf(
                    "C# (dump.cs style)" to { CodeGen.typeAsCSharp(type) },
                    "C++ field layout / offsets" to { CodeGen.fieldLayout(index, type) },
                    "C++ offsets header (all methods)" to { CodeGen.offsetsHeader(index, listOf(type)) },
                ),
                onCopy = { clipboard.setText(AnnotatedString(it)) },
                onShare = { text, name -> Sharing.shareText(context, text, name) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun CodeGenSheet(
    title: String,
    subtitle: String,
    snippets: List<Pair<String, () -> String>>,
    onCopy: (String) -> Unit,
    onShare: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf(0) }
    val code = remember(selected) { runCatching { snippets[selected].second() }.getOrElse { "// error: ${it.message}" } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snippets.size) { i ->
                androidx.compose.material3.FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(snippets[i].first) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Text(
                code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = { onCopy(code) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy")
            }
            androidx.compose.material3.OutlinedButton(onClick = { onShare(code, snippets[selected].first.replace(Regex("[^A-Za-z0-9]"), "_") + ".txt") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
            }
        }
    }
}

