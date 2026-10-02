package com.iyftv.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.iyftv.app.data.update.GitHubReleases

@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Open the installer as soon as the download is done.
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Ready && !s.attempted) vm.install(context)
    }

    val s = state
    if (s is UpdateState.Hidden) return
    val current = "当前版本：build ${vm.currentBuild}"

    when (s) {
        UpdateState.Hidden -> Unit
        UpdateState.About -> UpdateBox("设置", "当前版本：build ${vm.currentBuild}（${vm.versionName}）\n更新来源：GitHub ${GitHubReleases.REPO}", onBack = vm::dismiss) {
            Choice("检查更新", primary = true, onClick = vm::checkNow)
            Choice("关闭", onClick = vm::dismiss)
        }
        UpdateState.Checking -> UpdateBox("正在检查更新…", current, onBack = vm::dismiss) {
            Choice("取消", primary = true, onClick = vm::dismiss)
        }
        is UpdateState.UpToDate -> UpdateBox("已是最新版本", current, onBack = vm::dismiss) {
            Choice("好", primary = true, onClick = vm::dismiss)
        }
        is UpdateState.Available -> UpdateBox("发现新版本 build ${s.release.build}", current, s.release.notes, onBack = vm::dismiss) {
            Choice("更新", primary = true, onClick = { vm.download(s.release) })
            Choice("以后再说", onClick = vm::dismiss)
        }
        is UpdateState.Downloading -> UpdateBox("正在下载 build ${s.release.build}… ${(s.progress * 100).toInt()}%", current, onBack = vm::dismiss) {
            Box(Modifier.width(360.dp).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(s.progress).height(6.dp).background(MaterialTheme.colorScheme.primary))
            }
            Choice("取消", primary = true, onClick = vm::dismiss)
        }
        is UpdateState.Ready -> UpdateBox(
            "build ${s.release.build} 已下载",
            if (s.needsPermission) "请在设置里允许 iyfTV 安装未知应用，然后返回按“安装”。"
            else "如果没有出现安装界面，请按“安装”。",
            onBack = vm::dismiss,
        ) {
            Choice("安装", primary = true, onClick = { vm.install(context) })
            Choice("以后再说", onClick = vm::dismiss)
        }
        is UpdateState.Failed -> UpdateBox("更新失败", s.message, onBack = vm::dismiss) {
            val release = s.release
            Choice("重试", primary = true, onClick = { if (release != null) vm.download(release) else vm.checkNow() })
            Choice("关闭", onClick = vm::dismiss)
        }
    }
}

private class Choices {
    val primary = FocusRequester()
}

@Composable
private fun UpdateBox(title: String, message: String, notes: String? = null, onBack: () -> Unit, buttons: @Composable Choices.() -> Unit) {
    val choices = remember { Choices() }
    Dialog(onDismissRequest = onBack) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.width(520.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                if (!notes.isNullOrBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { choices.buttons() }
            }
        }
    }
    LaunchedEffect(title) { runCatching { choices.primary.requestFocus() } }
}

@Composable
private fun Choices.Choice(label: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) Button(onClick = onClick, modifier = Modifier.focusRequester(this.primary)) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}
