package me.magnum.melonds.ui.emulator

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R

@Composable
internal fun GlassLoadingScreen() {
    Surface(Modifier.fillMaxSize(), color = colorResource(R.color.glass_background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            val artworkSize = minOf(192.dp, maxHeight * 0.5f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.glass_loading_crystal), null, Modifier.size(artworkSize))
                Text(stringResource(R.string.info_loading), Modifier.padding(top = 20.dp), color = colorResource(R.color.glass_secondary))
            }
        }
    }
}
