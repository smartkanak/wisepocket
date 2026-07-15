package date.oxi.wisepocket.llm

import android.content.Context

/**
 * Holds the application [Context] so common code can resolve storage paths without threading a
 * Context through every call. Call [initWisePocketAndroid] once from the Android entry point.
 */
object AndroidAppContext {
    @Volatile
    var context: Context? = null
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }
}

/** Call once from the Android app before using the LLM/model features (e.g. in MainActivity.onCreate). */
fun initWisePocketAndroid(context: Context) = AndroidAppContext.init(context)

actual fun modelsDir(): String {
    val ctx = AndroidAppContext.context
        ?: error("initWisePocketAndroid(context) must be called before using models")
    // Internal app storage: /data/user/0/<pkg>/files/models. The model must live here (not in the
    // external Android/data dir) because llama.cpp's native code opens the file via a raw POSIX path;
    // on FUSE-backed external storage a file owned by another app's uid fails to open. Files created
    // by this app in internal storage are owned by the app uid, so native open() always succeeds.
    // Side-load during dev by streaming through run-as (writes as the app uid):
    //   adb shell "run-as <pkg> sh -c 'cat > files/models/<name>.gguf'" < local.gguf
    return ctx.filesDir.resolve("models").apply { mkdirs() }.absolutePath
}
