package com.linedraw.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linedraw.app.data.ProductOption

@Composable
fun ProductFilterDialog(options: List<ProductOption>, selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = options.filter { option ->
        search.isBlank() || (listOf(option.label) + option.names).any { it.contains(search.trim(), true) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("選擇商品") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("可多選；相同型號的不同款式會一起顯示。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(search, { search = it }, singleLine = true,
                    label = { Text("搜尋型號或商品名稱") },
                    modifier = Modifier.fillMaxWidth().testTag("productSearch"))
                TextButton(onClick = { onSelectionChange(emptySet()) }, modifier = Modifier.testTag("productReset")) {
                    Text(if (selected.isEmpty()) "全部商品（目前）" else "重設為全部商品")
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("productOptions")) {
                    if (visible.isEmpty()) item { Text("找不到符合的商品", Modifier.padding(vertical = 16.dp)) }
                    items(visible, key = { it.key }) { option ->
                        val checked = option.key in selected
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .toggleable(checked, role = Role.Checkbox) {
                                onSelectionChange(if (checked) selected - option.key else selected + option.key)
                            }.testTag("product:${option.key}").padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(option.label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                if (option.names.any { it != option.label }) Text(option.names.joinToString("／"),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text("符合目前條件 ${option.count} 筆", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("productDone")) { Text("完成") }
        })
}
