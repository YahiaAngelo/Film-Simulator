import androidx.compose.runtime.Composable

import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.example.compose.AppTheme
import org.koin.compose.KoinContext

/**
 * Entry point for the shared module and the App
 */
@Composable
fun App() {
    KoinContext {
        AppTheme {
            Navigator(screen = AppNavController()) { navigator ->
                SlideTransition(navigator = navigator)
            }
        }
    }

}