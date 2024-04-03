
import cafe.adriel.voyager.navigator.Navigator
import screens.home.HomeScreen


class AppNavigationActions(val navigator: Navigator) {

    fun navigateToMainScreen(userMessage: String = "") {
        val navigatesFromDrawer = userMessage == ""

        if (navigator.isEmpty)
            navigator.push(HomeScreen(userMessage))
        else navigator.popUntil { it == HomeScreen() }
    }
}