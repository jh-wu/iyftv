package com.iyftv.app.ui.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iyftv.app.data.update.ApkInstaller
import com.iyftv.app.data.update.Release
import com.iyftv.app.data.update.UpdateChecker
import com.iyftv.app.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Hidden : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val build: Int) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    /** Downloaded; [attempted] once the installer has been opened, [needsPermission] when Android asked for it first. */
    data class Ready(val release: Release, val file: File, val attempted: Boolean = false, val needsPermission: Boolean = false) : UpdateState
    data class Failed(val release: Release?, val message: String) : UpdateState
}

class UpdateViewModel(private val checker: UpdateChecker, private val app: Application) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state

    val currentBuild get() = checker.currentBuild
    private var job: Job? = null

    init {
        ApkInstaller.cleanUp(app)
        // Quiet check at start-up: only a newer build is worth interrupting for.
        job = viewModelScope.launch {
            runCatching { checker.newer() }.getOrNull()?.let { _state.value = UpdateState.Available(it) }
        }
    }

    /** Check from the menu button: always says what it found. */
    fun checkNow() {
        job?.cancel()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            _state.value = try {
                val latest = checker.latest()
                if (latest.build > checker.currentBuild) UpdateState.Available(latest) else UpdateState.UpToDate(checker.currentBuild)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(null, e.userMessage())
            }
        }
    }

    fun download(release: Release) {
        job?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)
        job = viewModelScope.launch {
            _state.value = try {
                val file = checker.download(release, ApkInstaller.updateFile(app, release)) { p ->
                    _state.value = UpdateState.Downloading(release, p)
                }
                if (ApkInstaller.isValid(app, file)) UpdateState.Ready(release, file)
                else UpdateState.Failed(release, "下载的文件不是有效的安装包")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(release, e.userMessage())
            }
        }
    }

    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        _state.value = when (ApkInstaller.install(context, ready.file)) {
            ApkInstaller.Result.Started -> ready.copy(attempted = true, needsPermission = false)
            ApkInstaller.Result.NeedsPermission -> ready.copy(attempted = true, needsPermission = true)
            ApkInstaller.Result.Failed -> UpdateState.Failed(ready.release, "无法打开系统安装程序")
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Hidden
    }
}
