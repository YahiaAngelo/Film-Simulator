import di.appModule
import org.koin.core.context.startKoin

/**
 * Koin DI init function to be called from the iOS project
 */
fun doInitKoin() {
    startKoin {
        modules(appModule())
    }
}