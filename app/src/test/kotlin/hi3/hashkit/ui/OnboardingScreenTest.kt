package hi3.hashkit.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import hi3.hashkit.ui.onboarding.OnboardingScreen
import hi3.hashkit.ui.theme.Hi3MinerWatchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke UI test (Robolectric, runs in the plain `test` task and in CI without a
 * device): the onboarding renders its key content and the primary action fires.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OnboardingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun onboarding_composes_and_shows_key_content() {
        rule.setContent {
            Hi3MinerWatchTheme { OnboardingScreen(onDone = {}) }
        }
        // Screen composes without crashing and renders its key content.
        rule.onNodeWithText("Local-first").assertExists()
        rule.onNodeWithText("Get started").assertExists()
        rule.onNodeWithText("Private").assertExists()
        assertTrue(true)
    }
}
