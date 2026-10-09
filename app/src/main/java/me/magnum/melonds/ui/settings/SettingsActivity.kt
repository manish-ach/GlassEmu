package me.magnum.melonds.ui.settings

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.ui.settings.fragments.CustomFirmwarePreferencesFragment
import me.magnum.melonds.ui.settings.fragments.MainPreferencesFragment
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    companion object {
        const val KEY_GROUP = "settings_group"
        const val KEY_ENTRY_POINT = "entry_point"
        const val CUSTOM_FIRMWARE_ENTRY_POINT = "custom_firmware_entry_point"
    }

    private var currentTitle by mutableStateOf("")
    private var hasParent by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.PersonalSettingsTheme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        currentTitle = getString(R.string.settings)
        supportFragmentManager.addOnBackStackChangedListener { updateTitle() }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, fragment: androidx.fragment.app.Fragment) { updateTitle() }
        }, false)

        setContent {
            MelonTheme {
                val background = colorResource(R.color.glass_background)
                val accent = colorResource(R.color.glass_accent)
                BackHandler { navigateBack() }
                Surface(color = background, contentColor = colorResource(R.color.glass_text)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 24.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, getString(R.string.library_back), tint = accent) }
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(currentTitle, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { context ->
                                FragmentContainerView(context).apply {
                                    id = R.id.personal_settings_container
                                    post {
                                        if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentById(id) == null) {
                                            val group = intent.getStringExtra(KEY_GROUP)
                                            val entry = if (group != null && group.startsWith("me.magnum.melonds.ui.settings.fragments.")) {
                                                supportFragmentManager.fragmentFactory.instantiate(classLoader, group)
                                            } else if (intent.getStringExtra(KEY_ENTRY_POINT) == CUSTOM_FIRMWARE_ENTRY_POINT) {
                                                CustomFirmwarePreferencesFragment()
                                            } else MainPreferencesFragment()
                                            supportFragmentManager.commit { replace(id, entry) }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun navigateBack() {
        if (!supportFragmentManager.popBackStackImmediate()) finish()
    }

    private fun updateTitle() {
        val fragment = supportFragmentManager.findFragmentById(R.id.personal_settings_container)
        currentTitle = (fragment as? PreferenceFragmentTitleProvider)?.getTitle() ?: getString(R.string.settings)
        hasParent = supportFragmentManager.backStackEntryCount > 0 || intent.hasExtra(KEY_GROUP)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val name = pref.fragment ?: return false
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, name).apply { arguments = pref.extras }
        supportFragmentManager.commit {
            replace(R.id.personal_settings_container, fragment)
            addToBackStack(null)
        }
        return true
    }
}
