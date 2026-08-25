package com.pdfphotoframe.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfphotoframe.app.ui.setup.SetupScreen
import com.pdfphotoframe.app.ui.slideshow.SlideshowScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is meant to sit as a picture frame, so keep the display awake.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as PdfPhotoFrameApplication

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "setup") {
                        composable("setup") {
                            SetupScreen(
                                settingsRepository = app.settingsRepository,
                                pdfPageRepository = app.pdfPageRepository,
                                onStartSlideshow = { navController.navigate("slideshow") }
                            )
                        }
                        composable("slideshow") {
                            SlideshowScreen(
                                settingsRepository = app.settingsRepository,
                                pdfPageRepository = app.pdfPageRepository,
                                onExit = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
