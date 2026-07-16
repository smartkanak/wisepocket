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
        // `dark`, not the default `auto`. Auto reads the *system's* light/dark setting to decide whether
        // the clock and battery icons are drawn dark or light — which is right for an app that follows the
        // system, and wrong for this one: the canvas is a deep blue whatever the phone thinks. Left on
        // auto, a light-mode phone drew a black clock onto the blue, which the build log has no way to
        // notice. Transparent scrims, because the app's own colour should reach the edges.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
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
