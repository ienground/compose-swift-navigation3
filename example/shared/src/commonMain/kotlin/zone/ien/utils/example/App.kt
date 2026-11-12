package zone.ien.utils.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kotlin.fibonacci.generateFibi
import org.jetbrains.compose.resources.painterResource
import zone.ien.utils.example.resources.Res
import zone.ien.utils.example.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    MaterialTheme {
        Scaffold { innerPadding ->
            var showContent by rememberSaveable { mutableStateOf(false) }
            val greeting = remember { Greeting().greet() }
            val fibonacci = remember { generateFibi().take(10).joinToString() }

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = greeting, style = MaterialTheme.typography.titleMedium)
                // Uses the `:library` module
                Text(text = "Fibonacci: $fibonacci")

                TextButton(onClick = { showContent = !showContent }) {
                    Text(text = if (showContent) "Hide logo" else "Show logo")
                }
                AnimatedVisibility(showContent) {
                    Image(
                        painter = painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
