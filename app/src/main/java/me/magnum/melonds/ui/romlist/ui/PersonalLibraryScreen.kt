package me.magnum.melonds.ui.romlist.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.romlist.RomIcon
import java.io.File
import java.security.MessageDigest

private val LibraryBackground: Color @Composable get() = colorResource(R.color.glass_background)
private val LibrarySurface: Color @Composable get() = colorResource(R.color.glass_surface)
private val LibraryAccent: Color @Composable get() = colorResource(R.color.glass_accent)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RomListScreen(
    roms: List<Rom>?,
    isRefreshing: Boolean,
    hasSearchDirectories: Boolean,
    onRomSelected: (Rom) -> Unit,
    onRefresh: () -> Unit,
    onDirectorySelected: (Uri) -> Unit,
    onRenameRom: (Rom, String) -> Unit,
    onSettingsGroupSelected: (String) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    BackHandler(pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }
    var actionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var artworkTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var artworkRevision by remember { mutableIntStateOf(0) }
    var isSavingArtwork by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = artworkTarget
        artworkTarget = null
        if (uri != null && target != null) {
            scope.launch {
                isSavingArtwork = true
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        require(bounds.outWidth > 0 && bounds.outHeight > 0)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 1
                            while (bounds.outWidth / inSampleSize > 1024 || bounds.outHeight / inSampleSize > 1024) inSampleSize *= 2
                        }
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                            ?: error("Cannot decode artwork")
                        try {
                            val destination = artworkFile(context.filesDir, target)
                            destination.parentFile?.mkdirs()
                            val temporary = File(destination.path + ".tmp")
                            try {
                                temporary.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                require(temporary.renameTo(destination))
                            } finally { temporary.delete() }
                        } finally { bitmap.recycle() }
                    }.isSuccess
                }
                isSavingArtwork = false
                if (saved) artworkRevision++
                else Toast.makeText(context, R.string.library_artwork_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onDirectorySelected)
    }

    Surface(color = LibraryBackground, contentColor = colorResource(R.color.glass_text)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                if (pager.currentPage == 0) Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (pager.currentPage == 0) R.string.library_title else R.string.settings), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (pager.currentPage == 0) IconButton(onClick = { directoryPicker.launch(null) }) {
                        Icon(Icons.Default.Add, stringResource(R.string.library_add_folder), tint = LibraryAccent)
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
                    if (page == 1) SettingsGroupsContent(onSettingsGroupSelected) else when {
                        isRefreshing || roms == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LibraryAccent)
                        }
                        !hasSearchDirectories || roms.isEmpty() -> Column(
                            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(stringResource(R.string.library_empty), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.library_empty_hint), Modifier.padding(top = 12.dp, bottom = 24.dp), color = colorResource(R.color.glass_secondary))
                            Button(onClick = { directoryPicker.launch(null) }, colors = ButtonDefaults.buttonColors(backgroundColor = LibraryAccent)) {
                                Text(stringResource(R.string.library_add_folder))
                            }
                            if (hasSearchDirectories) TextButton(onClick = onRefresh) {
                                Text(stringResource(R.string.action_refresh_rom_list), color = LibraryAccent)
                            }
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 112.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            items(roms, key = { it.uri.toString() }) { rom ->
                                val key = rom.uri.toString()
                                var artwork by remember(key) { mutableStateOf<Bitmap?>(null) }
                                var icon by remember(key) { mutableStateOf<RomIcon?>(null) }
                                LaunchedEffect(key, artworkRevision) {
                                    artwork = withContext(Dispatchers.IO) {
                                        BitmapFactory.decodeFile(artworkFile(context.filesDir, key).path)
                                    }
                                    if (artwork == null) icon = retrieveRomIcon(rom)
                                }
                                Column(Modifier.combinedClickable(
                                    onClick = { onRomSelected(rom) },
                                    onLongClickLabel = stringResource(R.string.library_edit_game),
                                    onLongClick = {
                                        if (!isSavingArtwork) {
                                            actionTarget = key
                                        }
                                    },
                                )) {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(LibrarySurface),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val cover = artwork
                                        val fallback = icon?.bitmap
                                        if (cover != null) Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else if (fallback != null) Image(fallback.asImageBitmap(), null, Modifier.size(64.dp))
                                    }
                                    Text(
                                        rom.config.customName ?: rom.name,
                                        Modifier.padding(top = 12.dp),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).width(240.dp),
                color = colorResource(R.color.glass_nav),
                shape = RoundedCornerShape(36.dp),
                elevation = 18.dp,
            ) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryNavItem(Modifier.weight(1f), stringResource(R.string.library_roms), pager.currentPage == 0, { scope.launch { pager.animateScrollToPage(0) } }) {
                        Icon(Icons.Default.GridView, null)
                    }
                    LibraryNavItem(Modifier.weight(1f), stringResource(R.string.settings), pager.currentPage == 1, { scope.launch { pager.animateScrollToPage(1) } }) {
                        Icon(Icons.Default.Settings, null)
                    }
                }
            }
        }
    }
    val selectedRom = roms?.firstOrNull { it.uri.toString() == actionTarget }
    if (selectedRom != null) AlertDialog(
        onDismissRequest = { actionTarget = null },
        backgroundColor = LibrarySurface,
        contentColor = colorResource(R.color.glass_text),
        shape = RoundedCornerShape(24.dp),
        title = { Text(selectedRom.config.customName ?: selectedRom.name) },
        buttons = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                TextButton(onClick = {
                    artworkTarget = selectedRom.uri.toString()
                    actionTarget = null
                    artworkPicker.launch("image/*")
                }) { Text(stringResource(R.string.library_set_artwork), color = LibraryAccent) }
                TextButton(onClick = {
                    renameTarget = selectedRom.uri.toString()
                    newName = selectedRom.config.customName ?: selectedRom.name
                    actionTarget = null
                }) { Text(stringResource(R.string.library_rename), color = LibraryAccent) }
            }
        },
    )
    val renamingRom = roms?.firstOrNull { it.uri.toString() == renameTarget }
    if (renamingRom != null) AlertDialog(
        onDismissRequest = { renameTarget = null },
        backgroundColor = LibrarySurface,
        contentColor = colorResource(R.color.glass_text),
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.library_rename)) },
        text = {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, singleLine = true,
                label = { Text(stringResource(R.string.library_game_name)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = colorResource(R.color.glass_text), cursorColor = LibraryAccent,
                    focusedBorderColor = LibraryAccent, unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = LibraryAccent, unfocusedLabelColor = colorResource(R.color.glass_secondary),
                ),
            )
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                onRenameRom(renamingRom, newName.trim())
                renameTarget = null
            }) { Text(stringResource(R.string.library_save), color = LibraryAccent) }
        },
        dismissButton = {
            TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.library_cancel), color = colorResource(R.color.glass_secondary)) }
        },
    )

}

@Composable
private fun LibraryNavItem(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(52.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = if (selected) colorResource(R.color.glass_selected) else Color.Transparent,
            contentColor = if (selected) LibraryAccent else colorResource(R.color.glass_secondary),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun artworkFile(filesDir: File, romUri: String): File {
    val id = MessageDigest.getInstance("SHA-256").digest(romUri.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(filesDir, "rom-artwork/$id.png")
}

@Preview(name = "A36 library", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PreviewPersonalLibrary() {
    RomListScreen(
        roms = emptyList(),
        isRefreshing = false,
        hasSearchDirectories = false,
        onRomSelected = {},
        onRefresh = {},
        onDirectorySelected = {},
        onRenameRom = { _, _ -> },
        onSettingsGroupSelected = {},
        retrieveRomIcon = { RomIcon(null, me.magnum.melonds.domain.model.RomIconFiltering.NONE) },
    )
}
