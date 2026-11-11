package com.shii.dev.cambix

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shii.dev.cambix.ui.camera.CameraScreen
import com.shii.dev.cambix.ui.camera.CameraViewModel
import com.shii.dev.cambix.ui.components.GlassNavBar
import com.shii.dev.cambix.ui.components.Tab
import com.shii.dev.cambix.ui.gallery.GalleryScreen
import com.shii.dev.cambix.ui.settings.SettingsScreen
import com.shii.dev.cambix.ui.discover.DiscoverScreen
import com.shii.dev.cambix.ui.studio.StudioScreen
import com.shii.dev.cambix.ui.studio.StudioViewModel
import com.shii.dev.cambix.ui.theme.CambixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { CambixTheme { CambixApp() } }
    }
}

@Composable
private fun CambixApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.CAMERA) }
    var studioUri by remember { mutableStateOf<Uri?>(null) }
    val cameraVm: CameraViewModel = viewModel()
    val studioVm: StudioViewModel = viewModel()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.CAMERA -> CameraScreen(cameraVm, onOpenGallery = { tab = Tab.GALLERY })
                Tab.DISCOVER -> DiscoverScreen()
                Tab.STUDIO -> StudioScreen(studioVm, studioUri, onPendingConsumed = { studioUri = null })
                Tab.GALLERY -> GalleryScreen(onEdit = { uri ->
                    studioUri = uri
                    tab = Tab.STUDIO
                })
                Tab.SETTINGS -> SettingsScreen()
            }
        }
        GlassNavBar(selected = tab, onSelect = { tab = it })
    }
}
