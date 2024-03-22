
import cafe.adriel.voyager.navigator.Navigator
import screens.home.HomeScreen


class AppNavigationActions(val navigator: Navigator) {

    fun navigateToMainScreen(userMessage: Int = 0) {
        val navigatesFromDrawer = userMessage == 0

        if (navigator.isEmpty)
            navigator.push(HomeScreen(userMessage))
        else navigator.popUntil { it == HomeScreen() }
    }
}