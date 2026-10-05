package dev.il2cpp.dumper.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.il2cpp.dumper.app.ui.MainViewModel
import dev.il2cpp.dumper.app.ui.Screen
import dev.il2cpp.dumper.app.ui.components.Tag
import dev.il2cpp.dumper.core.extra.DumpIndex
import dev.il2cpp.dumper.core.extra.SearchEngine
import dev.il2cpp.dumper.core.extra.TypeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassBrowserScreen(vm: MainViewModel, id: String) {
    val record = remember(id) { vm.store.get(id) }
    val indexState = produceState<DumpIndex?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) { runCatching { record?.loadIndex() }.getOrNull() }
    }
    val index = indexState.value
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SearchEngine.Scope.ALL) }

    val engine = remember(index) { index?.let { SearchEngine(it) } }
    val results = remember(query, scope, engine) {
        if (engine == null) emptyList()
        else if (query.isBlank()) engine.namespaces().flatMap { it.second }.map { SearchEngine.Hit.TypeHit(it) }.take(1000)
        else engine.search(query, scope, 1000)
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(record?.title ?: "Classes", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search types, methods, 0xRVA, /regex/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchEngine.Scope.entries.forEach { s ->
                    FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            if (index == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
            } else {
                Text("  ${results.size} result${if (results.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(results.size) { i ->
                        when (val hit = results[i]) {
                            is SearchEngine.Hit.TypeHit -> ResultRow(Icons.Rounded.DataObject, hit.type.name, hit.type.namespace.ifEmpty { hit.type.kind.name.lowercase() },
                                kindTag(hit.type.kind)) { vm.navigate(Screen.TypeDetail(id, hit.type.typeDefIndex)) }
                            is SearchEngine.Hit.MethodHit -> ResultRow(Icons.Rounded.Functions, hit.method.name + "()", hit.type.fullName,
                                if (hit.method.hasBody) "0x" + java.lang.Long.toHexString(hit.method.rva).uppercase() else null) { vm.navigate(Screen.TypeDetail(id, hit.type.typeDefIndex)) }
                            is SearchEngine.Hit.FieldHit -> ResultRow(Icons.Rounded.DataObject, hit.field.name, "${hit.type.fullName}  ·  ${hit.field.typeName}",
                                if (!hit.field.isConst) "0x" + Integer.toHexString(hit.field.offset).uppercase() else null) { vm.navigate(Screen.TypeDetail(id, hit.type.typeDefIndex)) }
                            is SearchEngine.Hit.StringHit -> ResultRow(Icons.Rounded.TextFields, '"' + hit.literal.value.take(80) + '"', "string literal",
                                "0x" + java.lang.Long.toHexString(hit.literal.address).uppercase()) {}
                        }
                    }
                }
            }
        }
    }
}

private fun kindTag(kind: TypeKind) = kind.name.lowercase()

@Composable
private fun ResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Tag(trailing, container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
