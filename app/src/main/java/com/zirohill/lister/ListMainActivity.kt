package com.zirohill.lister

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import android.app.Application

val CustomColorPalette = lightColors(
    primary = Color(0xFF6C757D),
    primaryVariant = Color(0xFF495057),
    secondary = Color(0xFFADB5BD),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF212529),
    onBackground = Color(0xFF212529),
    onSurface = Color(0xFF212529)
)

sealed class DialogState {
    object None : DialogState()
    object AddTask : DialogState()
    data class EditTask(val todo: TodoItem) : DialogState()
    object SetReminder : DialogState()
}

@Composable
fun MainScreen(vm: TodoViewModel, categoryViewModel: CategoryViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        TodoScreen(vm = vm, categoryViewModel = categoryViewModel)
    }
}

class MainActivity : ComponentActivity() {
    private val vm by lazy {
        ViewModelProvider(this, TodoViewModelFactory(this)).get(TodoViewModel::class.java)
    }

    private val categoryVm by lazy {
        ViewModelProvider(this, CategoryViewModelFactory(this)).get(CategoryViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colors = CustomColorPalette) {
                val activity = LocalActivity.current
                val view = LocalView.current

                SideEffect {
                    activity?.window?.let { window ->
                        window.statusBarColor = CustomColorPalette.background.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    }
                }

                MainScreen(vm = vm, categoryViewModel = categoryVm)
            }
        }
    }
}

class ListerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }
}
