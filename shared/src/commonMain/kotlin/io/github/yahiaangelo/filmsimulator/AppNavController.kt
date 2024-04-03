import androidx.compose.material.DrawerState
import androidx.compose.material.DrawerValue
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.CoroutineScope
import screens.home.HomeScreen
import view.AppModalDrawer

class AppNavController
: Screen {

    @Composable
    override fun Content() {

        val currentNavigator = LocalNavigator.currentOrThrow

        currentNavigator.push(HomeScreen())
    }

}