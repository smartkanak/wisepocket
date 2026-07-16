package date.oxi.wisepocket

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // `light`, not the default `auto`. Auto reads the *system's* light/dark setting to decide whether
        // the clock and battery icons are drawn dark or light — which is right for an app that follows the
        // system, and wrong for this one: the canvas is light off-white whatever the phone thinks. Left on
        // auto or dark, the phone drew a white clock onto the off-white, making it invisible.
        // Transparent scrims, because the app's own color should reach the edges.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
