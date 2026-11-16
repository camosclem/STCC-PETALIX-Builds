package com.nuvio.tv.petalix

import androidx.compose.runtime.mutableStateOf

/** Focus only; selecting a category does not start a stream or change navigation. */
internal object PetalixLiveCategoryFocus {
    val category = mutableStateOf<String?>(null)
}
