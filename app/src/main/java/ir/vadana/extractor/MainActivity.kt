package ir.vadana.extractor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ir.vadana.extractor.ui.MainScreen
import ir.vadana.extractor.ui.MainViewModel
import ir.vadana.extractor.ui.VadanaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VadanaTheme {
                MainScreen(viewModel)
            }
        }
    }
}
