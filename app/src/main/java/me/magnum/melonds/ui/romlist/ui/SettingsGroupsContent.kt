package me.magnum.melonds.ui.romlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R
import org.xmlpull.v1.XmlPullParser

private data class SettingsGroup(val title: String, val summary: String, val icon: Int, val fragment: String)

@Composable
internal fun SettingsGroupsContent(onGroupSelected: (String) -> Unit) {
    val context = LocalContext.current
    // Use the original settings index so every group remains available.
    val groups = remember(context) {
        val result = mutableListOf<SettingsGroup>()
        context.resources.getXml(R.xml.pref_main).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Preference") {
                    val android = "http://schemas.android.com/apk/res/android"
                    val app = "http://schemas.android.com/apk/res-auto"
                    result += SettingsGroup(
                        context.getString(parser.getAttributeResourceValue(android, "title", 0)),
                        context.getString(parser.getAttributeResourceValue(android, "summary", 0)),
                        parser.getAttributeResourceValue(app, "icon", 0),
                        parser.getAttributeValue(android, "fragment"),
                    )
                }
                parser.next()
            }
        }
        result
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "settings_header") {
            Text(stringResource(R.string.settings), Modifier.padding(top = 24.dp, bottom = 12.dp), color = colorResource(R.color.glass_text), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        }
        items(groups, key = { it.fragment }) { group ->
            Surface(color = colorResource(R.color.glass_surface), shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onGroupSelected(group.fragment) }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painterResource(group.icon), null, Modifier.size(24.dp), tint = colorResource(R.color.glass_accent))
                    Column(Modifier.weight(1f)) {
                        Text(group.title, color = colorResource(R.color.glass_text), fontSize = 16.sp, lineHeight = 22.sp)
                        Text(group.summary, Modifier.padding(top = 5.dp), color = colorResource(R.color.glass_secondary), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colorResource(R.color.glass_secondary))
                }
            }
        }
    }
}
