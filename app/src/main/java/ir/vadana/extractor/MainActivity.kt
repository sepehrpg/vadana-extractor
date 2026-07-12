package ir.vadana.extractor

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.vadana.extractor.ui.MainScreen
import ir.vadana.extractor.ui.MainViewModel
import ir.vadana.extractor.ui.VadanaTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val configuration = LocalConfiguration.current
            val layoutDirection = if (configuration.isPersianLocale()) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                VadanaTheme {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

private fun Configuration.isPersianLocale(): Boolean {
    val primaryLocale = locales.get(0) ?: Locale.getDefault()
    return primaryLocale.language.equals("fa", ignoreCase = true)
}
