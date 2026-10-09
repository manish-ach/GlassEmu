package me.magnum.melonds.ui.emulator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R

@Composable
internal fun GlassPauseMenu(options: List<PauseMenuOption>, onResume: () -> Unit, onOption: (PauseMenuOption) -> Unit) {
    val ink = colorResource(R.color.glass_text)
    val accent = colorResource(R.color.glass_accent)
    Surface(color = colorResource(R.color.glass_surface), contentColor = ink, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pause), Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            MenuRow(stringResource(R.string.glass_resume), accent, onResume)
            Divider(Modifier.padding(horizontal = 24.dp), color = colorResource(R.color.glass_secondary).copy(alpha = 0.16f))
            options.forEach { option ->
                MenuRow(stringResource(option.textResource), ink) { onOption(option) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuRow(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.body1)
    }
}
