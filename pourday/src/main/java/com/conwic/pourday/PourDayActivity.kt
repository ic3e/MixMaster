package com.conwic.pourday

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.conwic.pourday.game.GameScreen

/**
 * Pour Day on its own: the game, full screen, and nothing else. Its way out on the title and end
 * screens, and in the pause menu, closes the app. The copy passed round as a file says on its title
 * screen when there's a new version; the one from Google Play leaves that to Play ([StoreUpdates]).
 */
class PourDayActivity : ComponentActivity() {
    private var game: GameScreen? = null
    private val updates = storeUpdates(this)

    // the answer to a permission question goes to whoever asked it
    private var permissionAnswer: ((Boolean) -> Unit)? = null
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val then = permissionAnswer
        permissionAnswer = null
        then?.invoke(result.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = GameScreen(
            context = this,
            appName = "Pour Day",
            onQuit = { finish() },
            askPermissions = { permissions, then ->
                permissionAnswer = then
                askPermissions.launch(permissions)
            },
            updates = updates,
        )
        game = screen
        updates?.look()
        // From Android 15 an app is drawn edge to edge, under the camera's cut-out too, and the
        // game's buttons at the sides would sit under it on a phone held sideways. It keeps clear
        // of the cut-out, as it did before, with the dusk showing there instead.
        val frame = FrameLayout(this)
        frame.addView(screen.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val cutOut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(cutOut.left, cutOut.top, cutOut.right, cutOut.bottom)
            insets
        }
        setContentView(frame)
        fullScreen()
        // Mid-shift the back gesture pauses the game; on its title and end screens it leaves.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = screen.back { finish() }
            },
        )
    }

    override fun onPause() {
        game?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        game?.resume()
        // perhaps back from the phone's "install unknown apps" screen, with the update waiting
        updates?.resumed()
        // Android does not always keep the bars hidden across a trip to another app.
        fullScreen()
    }

    override fun onDestroy() {
        game?.close()
        game = null
        super.onDestroy()
    }

    /** The status and navigation bars hidden; a swipe from the edge brings them back for a moment. */
    private fun fullScreen() {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    }
}
