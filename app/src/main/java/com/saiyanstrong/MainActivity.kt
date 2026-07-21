package com.saiyanstrong

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.navigation.NavGraph
import com.saiyanstrong.presentation.theme.SaiyanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val crash = CrashReporter.lastCrash(this)
        if (crash != null) {
            // Show the last crash instead of the normal UI, so it can't re-crash before it's seen.
            setContent {
                SaiyanTheme {
                    CrashReportScreen(
                        trace = crash,
                        onCopy = { copyToClipboard(activity, crash) },
                        onDismiss = { CrashReporter.clear(activity); activity.recreate() }
                    )
                }
            }
            return
        }
        setContent {
            SaiyanTheme {
                NavGraph()
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("crash", text))
}

@Composable
private fun CrashReportScreen(trace: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("The app crashed. Copy this and send it:", fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
            Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("COPY CRASH") }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("DISMISS & CONTINUE") }
            Text(
                trace, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
