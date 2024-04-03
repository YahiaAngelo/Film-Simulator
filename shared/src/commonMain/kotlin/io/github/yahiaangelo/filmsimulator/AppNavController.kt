import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import screens.home.HomeScreen

class AppNavController
: Screen {

    @Composable
    override fun Content() {

        val currentNavigator = LocalNavigator.currentOrThrow

        currentNavigator.push(HomeScreen())
    }

}