import androidx.compose.runtime.Composable

import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.example.compose.AppTheme
import org.koin.compose.KoinContext

@Composable
fun App() {
    KoinContext {
        AppTheme {
            Navigator(AppNavController()) {
                SlideTransition(it)
            }
        }
    }

}