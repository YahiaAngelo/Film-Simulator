import androidx.compose.runtime.Composable

import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.util.DebugLogger
import org.koin.compose.KoinContext

/**
 * Entry point for the shared module and the App
 */
@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .logger(DebugLogger())
            .build()
    }
    KoinContext {
        AppTheme {
            Navigator(screen = AppNavController()) { navigator ->
                SlideTransition(navigator = navigator)
            }
        }
    }

}