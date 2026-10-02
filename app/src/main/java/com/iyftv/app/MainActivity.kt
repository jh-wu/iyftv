package com.iyftv.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import com.iyftv.app.ui.category.CategoryViewModel
import com.iyftv.app.ui.common.IyfTheme
import com.iyftv.app.ui.detail.DetailScreen
import com.iyftv.app.ui.detail.DetailViewModel
import com.iyftv.app.ui.home.HistoryScreen
import com.iyftv.app.ui.home.HistoryViewModel
import com.iyftv.app.ui.home.HomeScreen
import com.iyftv.app.ui.home.HomeViewModel
import com.iyftv.app.ui.player.PlayerActivity
import com.iyftv.app.ui.search.SearchScreen
import com.iyftv.app.ui.search.SearchViewModel
import com.iyftv.app.ui.update.UpdateDialog
import com.iyftv.app.ui.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IyfTvApp
        val play = { videoKey: String, episodeKey: String? ->
            startActivity(PlayerActivity.intent(this, videoKey, episodeKey))
        }

        setContent {
            IyfTheme {
                Surface(Modifier.fillMaxSize(), shape = RectangleShape) {
                    val updates = viewModel { UpdateViewModel(app.updates, app) }
                    UpdateDialog(updates)
                    val nav = rememberNavController()
                    val openVideo = { key: String -> nav.navigate("detail/${Uri.encode(key)}") }

                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm = viewModel { HomeViewModel(app.source, app.history, app.getSharedPreferences("home", MODE_PRIVATE)) },
                                categoryViewModel = { c -> viewModel(key = "category-${c.id}") { CategoryViewModel(app.source, c) } },
                                onOpenVideo = openVideo,
                                // Open the title's page under the player, so leaving playback lands there.
                                onResume = { openVideo(it.videoKey); play(it.videoKey, it.episodeKey) },
                                onSearch = { nav.navigate("search") },
                                onCheckUpdate = updates::checkNow,
                            )
                        }
                        composable("search") {
                            SearchScreen(vm = viewModel { SearchViewModel(app.source, app.searchHistory) }, onOpenVideo = openVideo)
                        }
                        composable("history") {
                            HistoryScreen(vm = viewModel { HistoryViewModel(app.history) }, onOpen = { openVideo(it.videoKey) })
                        }
                        composable("detail/{key}") { entry ->
                            val key = entry.arguments?.getString("key").orEmpty()
                            DetailScreen(
                                vm = viewModel { DetailViewModel(app.source, app.history, key) },
                                onPlay = { episodeKey -> play(key, episodeKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}
