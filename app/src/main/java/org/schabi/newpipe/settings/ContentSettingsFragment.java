package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.util.PicassoHelper;

import java.io.IOException;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();


        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = defaultPreferences.getString(getString(R.string.app_language_key), "en");

        findPreference(getString(R.string.download_thumbnail_key)).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    PicassoHelper.setShouldLoadImages((Boolean) newValue);
                    try {
                        PicassoHelper.clearCache(preference.getContext());
                        Toast.makeText(preference.getContext(),
                                R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                                .show();
                    } catch (final IOException e) {
                        Log.e(TAG, "Unable to clear Picasso cache", e);
                    }
                    return true;
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage =
                defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }
}
