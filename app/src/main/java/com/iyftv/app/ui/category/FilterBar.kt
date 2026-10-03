package com.iyftv.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.iyftv.app.data.model.FilterGroup

/** A row of buttons, one for the sort order and one per filter (地区, 语言, 年份); each opens a list to pick from. */
@Composable
fun FilterBar(vm: CategoryViewModel, modifier: Modifier = Modifier, onListClosed: () -> Unit = {}) {
    if (vm.filterGroups.isEmpty()) return
    val filter by vm.filter.collectAsState()
    var open by remember { mutableStateOf<FilterGroup?>(null) }
    var last by remember { mutableStateOf<FilterGroup?>(null) }
    val buttons = remember(vm.filterGroups) { vm.filterGroups.associate { it.id to FocusRequester() } }

    // When the list closes, focus goes back to the button that opened it. Otherwise it lands
    // on the first tab (继续观看), which would switch tabs.
    LaunchedEffect(open) {
        if (open == null) last?.let { g -> runCatching { buttons[g.id]?.requestFocus() } }
    }

    Row(modifier.padding(start = 48.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        vm.filterGroups.forEach { g ->
            val chosen = filter[g.id]
            val label = chosen ?: g.allLabel
            val m = Modifier.focusRequester(buttons.getValue(g.id))
            val onClick = { last = g; open = g }
            if (chosen != null) {
                Button(onClick = onClick, modifier = m) { Text("$label ▾") }
            } else {
                OutlinedButton(onClick = onClick, modifier = m) { Text("$label ▾") }
            }
        }
    }

    open?.let { g ->
        FilterDialog(
            g, filter[g.id],
            onPick = { onListClosed(); vm.setFilter(g.id, it); open = null },
            onDismiss = { onListClosed(); open = null },
        )
    }
}

@Composable
private fun FilterDialog(group: FilterGroup, chosen: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val choices = listOf<String?>(null) + group.options
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            LazyColumn(
                Modifier.width(320.dp).heightIn(max = 480.dp),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(choices) { value ->
                    val label = value ?: group.allLabel
                    val selected = value == chosen
                    val m = Modifier.fillMaxWidth().let { if (selected) it.focusRequester(focus) else it }
                    if (selected) {
                        Button(onClick = { onPick(value) }, modifier = m) { Text("✓ $label") }
                    } else {
                        OutlinedButton(onClick = { onPick(value) }, modifier = m) { Text(label) }
                    }
                }
            }
        }
    }
    LaunchedEffect(group.id) { runCatching { focus.requestFocus() } }
}
