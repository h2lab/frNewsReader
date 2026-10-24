package org.h2lab.frnewsreader.data

import androidx.annotation.StringRes
import org.h2lab.frnewsreader.R

enum class NewsCategory(@param:StringRes val labelRes: Int) {
    FRANCE(R.string.tab_france),
    INTERNATIONAL(R.string.tab_international),
    TECHNOLOGIES(R.string.tab_technologies),
}
