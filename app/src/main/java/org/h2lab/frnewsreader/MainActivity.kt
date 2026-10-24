package org.h2lab.frnewsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.h2lab.frnewsreader.ui.NewsApp
import org.h2lab.frnewsreader.ui.theme.FrNewsReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrNewsReaderTheme {
                NewsApp()
            }
        }
    }
}
