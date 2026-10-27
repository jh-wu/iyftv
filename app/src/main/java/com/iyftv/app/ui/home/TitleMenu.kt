package com.iyftv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.data.history.WatchRecord

/** The long-press menu for a title in 继续观看. */
@Composable
fun TitleMenu(
    record: WatchRecord,
    player: PlayerChoice.Player?,
    posterNote: String?,
    onResetPlayer: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // The menu opens while OK is still held from the long press. Its release (and the held
    // key's repeats) must not press a menu button, so buttons work only after a fresh press.
    var armed by remember { mutableStateOf(false) }
    fun ifArmed(action: () -> Unit): () -> Unit = { if (armed) action() }
    val playerText = when (player) {
        PlayerChoice.Player.App -> "当前：应用播放器"
        PlayerChoice.Player.Web -> "当前：网页播放器"
        null -> "当前：未记录，下次播放会自动测试"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.width(480.dp).padding(24.dp).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) armed = true
                    false
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(record.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = ifArmed(onRemove), modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                    Text("从继续观看中删除")
                }
                OutlinedButton(onClick = ifArmed(onResetPlayer), enabled = player != null, modifier = Modifier.fillMaxWidth()) {
                    Text("重置播放器")
                }
                Text(playerText, style = MaterialTheme.typography.bodySmall)
                // Shown so a poster that won't load can be reported with its address and error.
                Text(
                    posterNote ?: "海报：${record.imageUrl ?: "没有地址"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
