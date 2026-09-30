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
import com.iyftv.app.data.model.Category
import com.iyftv.app.ui.category.CategoryScreen
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
                                vm = viewModel { HomeViewModel(app.source, app.history) },
                                onOpenVideo = openVideo,
                                onResume = { play(it.videoKey, it.episodeKey) },
                                onOpenCategory = { c -> nav.navigate("category/${Uri.encode(c.id)}/${Uri.encode(c.name)}") },
                                onSearch = { nav.navigate("search") },
                                onHistory = { nav.navigate("history") },
                                onCheckUpdate = updates::checkNow,
                                version = "build ${BuildConfig.VERSION_CODE}",
                            )
                        }
                        composable("category/{id}/{name}") { entry ->
                            val id = entry.arguments?.getString("id").orEmpty()
                            val name = entry.arguments?.getString("name").orEmpty()
                            CategoryScreen(
                                title = name,
                                vm = viewModel { CategoryViewModel(app.source, Category(id, name)) },
                                onOpenVideo = openVideo,
                            )
                        }
                        composable("search") {
                            SearchScreen(vm = viewModel { SearchViewModel(app.source) }, onOpenVideo = openVideo)
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
