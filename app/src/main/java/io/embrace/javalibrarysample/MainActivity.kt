package io.embrace.javalibrarysample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.embrace.android.embracesdk.Embrace
import io.embrace.javalibrarysample.ui.theme.JavaLibrarySampleTheme
import io.embrace.lib.Networking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JavaLibrarySampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
private val emb = EmbraceNetworkingInversionOfControl(Embrace.getInstance())

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
        Button(onClick = {
            CoroutineScope(Dispatchers.IO).async {
                Networking().myAPI(
                    "https://httpbin.org/get",
                    emb.embraceAbrastraction
                )
            }
        }) {
            Text("Click me")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JavaLibrarySampleTheme {
        Greeting("Android")
    }
}