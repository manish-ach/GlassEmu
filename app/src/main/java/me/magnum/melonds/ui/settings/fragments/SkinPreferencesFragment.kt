package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.impl.skins.DeltaSkinRepository
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider

class SkinPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {
    private val repository by lazy { DeltaSkinRepository(requireContext().applicationContext) }
    private var importing = false
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            importing = true
            findPreference<Preference>("import_delta_skin")?.apply { isEnabled = false; setSummary(R.string.skin_importing) }
            val result = withContext(Dispatchers.IO) { runCatching { repository.import(uri) } }
            importing = false
            if (isAdded) {
                refreshChoices()
                findPreference<Preference>("import_delta_skin")?.apply {
                    isEnabled = true
                    summary = result.fold({ getString(R.string.import_delta_skin_summary) }, { getString(R.string.skin_import_error, it.message ?: "Invalid skin file") })
                }
                result.onSuccess { Toast.makeText(requireContext(), getString(R.string.skin_imported, it), Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(requireContext(), getString(R.string.skin_import_error, it.message ?: "Invalid skin file"), Toast.LENGTH_LONG).show() }
            }
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_skins, rootKey)
        refreshChoices()
        findPreference<Preference>("import_delta_skin")?.setOnPreferenceClickListener {
            if (!importing) importer.launch(arrayOf("*/*"))
            true
        }
    }
    private fun refreshChoices() {
        val choices = repository.choices()
        findPreference<ListPreference>("delta_skin")?.apply {
            entries = choices.map { it.name }.toTypedArray()
            entryValues = choices.map { it.id }.toTypedArray()
            value = repository.selected().takeIf { id -> choices.any { it.id == id } } ?: "builtin"
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
    }
    override fun getTitle(): String = getString(R.string.skins)
}
