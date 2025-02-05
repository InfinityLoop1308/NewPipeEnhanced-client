package org.schabi.newpipe.fragments.list.sponsorblock;

import static org.schabi.newpipe.fragments.detail.VideoDetailFragment.ACTION_MARK_SEEKBAR;
import static org.schabi.newpipe.util.TimeUtils.millisecondsToString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import icepick.Icepick;
import icepick.State;
import io.reactivex.rxjava3.core.Single;
import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding;
import org.schabi.newpipe.extractor.sponsorblock.*;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.sponsorblock.SponsorBlockDataManager;
import org.schabi.newpipe.util.SponsorBlockHelper;
import org.schabi.newpipe.util.SponsorBlockMode;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.UnsupportedEncodingException;

public class SponsorBlockFragment
        extends BaseFragment
        implements CompoundButton.OnCheckedChangeListener,
        SponsorBlockSegmentListAdapterListener {
    @State
    StreamInfo streamInfo = null;
    FragmentSponsorBlockBinding binding;
    @State
    Integer markedStartTime = null;
    @State
    Integer markedEndTime = null;
    private SponsorBlockSegmentListAdapter segmentListAdapter;
    private int currentProgress = -1;
    private @Nullable SponsorBlockFragmentListener sponsorBlockFragmentListener;
    private SponsorBlockDataManager sponsorBlockDataManager;
    private Disposable workerAddToWhitelisted;
    private Disposable workerRemoveFromWhitelisted;
    private SponsorBlockMode currentSponsorBlockMode = null;
    private boolean currentIsWhitelisted;

    public SponsorBlockFragment() {
    }

    public SponsorBlockFragment(@NonNull final StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sponsorBlockDataManager = new SponsorBlockDataManager(getContext());
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (segmentListAdapter == null) {
            segmentListAdapter = new SponsorBlockSegmentListAdapter(context, this);
        }

        if (streamInfo == null || streamInfo.isFetchSponsorBlockFinished()) {
            return;
        }

        Disposable disposable = Single.fromCallable(() -> {
                    SponsorBlockApiSettings sponsorBlockApiSettings = streamInfo.getService().getSponsorBlockApiSettings();
                    if (sponsorBlockApiSettings != null) {
                        try {
                            return SponsorBlockExtractorHelper.getSegments(streamInfo, sponsorBlockApiSettings);
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return new SponsorBlockSegment[0];
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        segments -> {
                            streamInfo.setSponsorBlockSegments(segments);
                            streamInfo.setFetchSponsorBlockFinished(true);
                            context.sendBroadcast(new Intent(ACTION_MARK_SEEKBAR));
                            segmentListAdapter.setItems(segments);
                            segmentListAdapter.notifyDataSetChanged();
                        },
                        throwable -> {
                            Toast.makeText(context, "Failed to load segments", Toast.LENGTH_SHORT).show();
                            Log.e("SponsorBlock", "Error loading segments", throwable);
                        }
                );
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (workerAddToWhitelisted != null) {
            workerAddToWhitelisted.dispose();
        }
        if (workerRemoveFromWhitelisted != null) {
            workerRemoveFromWhitelisted.dispose();
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        if (sponsorBlockDataManager != null) {
            sponsorBlockDataManager = new SponsorBlockDataManager(getContext());
        }

        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false);

        binding.sponsorBlockControlsMarkSegmentStart.setOnClickListener(v ->
                doMarkPendingSegment(true));
        binding.sponsorBlockControlsMarkSegmentEnd.setOnClickListener(v ->
                doMarkPendingSegment(false));
        binding.sponsorBlockControlsSegmentStart.setOnClickListener(v ->
                doPendingSegmentSeek(true));
        binding.sponsorBlockControlsSegmentEnd.setOnClickListener(v ->
                doPendingSegmentSeek(false));
        binding.sponsorBlockControlsClearSegment.setOnClickListener(v ->
                doClearPendingSegment());
        binding.sponsorBlockControlsSubmitSegment.setOnClickListener(v ->
                doSubmitPendingSegment());

        binding.segmentList.setAdapter(segmentListAdapter);

        binding.skippingIsEnabledSwitch.setChecked(
                currentSponsorBlockMode == SponsorBlockMode.ENABLED);

        binding.channelIsWhitelistedSwitch.setChecked(currentIsWhitelisted);

        if (currentIsWhitelisted) {
            binding.skippingIsEnabledSwitch.setChecked(false);
            binding.skippingIsEnabledSwitch.setEnabled(!currentIsWhitelisted);
        }

        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this);

        return binding.getRoot();
    }

    @Override
    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
        if (buttonView.getId() == R.id.skipping_is_enabled_switch) {
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener.onSkippingEnabledChanged(isChecked);
            }
        } else if (buttonView.getId() == R.id.channel_is_whitelisted_switch) {
            final Context context = requireContext();

            final String toastText;

            if (isChecked) {
                toastText = context.getString(
                        R.string.sponsor_block_uploader_added_to_whitelist_toast);

                workerAddToWhitelisted =
                        sponsorBlockDataManager.addToWhitelist(streamInfo.getUploaderName())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(result -> {
                                    Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
                                }, error -> {
                                    // TODO
                                });
            } else {
                toastText = context.getString(
                        R.string.sponsor_block_uploader_removed_from_whitelist_toast);

                workerRemoveFromWhitelisted =
                        sponsorBlockDataManager.removeFromWhitelist(streamInfo.getUploaderName())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> {
                                    Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
                                }, error -> {
                                    // TODO
                                });
            }

            binding.skippingIsEnabledSwitch.setChecked(false);
            binding.skippingIsEnabledSwitch.setEnabled(!isChecked);
        }
    }

    public void setListener(final SponsorBlockFragmentListener listener) {
        sponsorBlockFragmentListener = listener;
    }

    public void setSponsorBlockMode(@NonNull final SponsorBlockMode mode) {
        currentSponsorBlockMode = mode;

        if (binding == null) {
            return;
        }

        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(null);
        binding.skippingIsEnabledSwitch.setChecked(mode == SponsorBlockMode.ENABLED);
        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
    }

    public void setIsWhitelisted(final boolean value) {
        currentIsWhitelisted = value;

        if (binding == null) {
            return;
        }

        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(null);
        binding.channelIsWhitelistedSwitch.setChecked(value);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this);

        if (value) {
            binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(null);
            binding.skippingIsEnabledSwitch.setChecked(false);
            binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);

            binding.skippingIsEnabledSwitch.setEnabled(!currentIsWhitelisted);
        }
    }

    public void setCurrentProgress(final int progress) {
        currentProgress = progress;
    }

    @SuppressLint("SetTextI18n")
    public void clearPendingSegment() {
        markedStartTime = null;
        markedEndTime = null;

        binding.sponsorBlockControlsSegmentStart.setText("00:00:00");
        binding.sponsorBlockControlsSegmentEnd.setText("00:00:00");

        if (sponsorBlockFragmentListener != null) {
            sponsorBlockFragmentListener.onRequestClearPendingSegment();
        }
    }

    public void refreshSponsorBlockSegments() {
        if (segmentListAdapter == null) {
            return;
        }

        segmentListAdapter.setItems(streamInfo.getSponsorBlockSegments());
        segmentListAdapter.notifyDataSetChanged();
    }

    private void doMarkPendingSegment(final boolean isStart) {
        if (currentProgress < 0) {
            return;
        }

        if (isStart) {
            if (markedEndTime != null && currentProgress > markedEndTime) {
                Toast.makeText(getContext(),
                        getString(R.string.sponsor_block_invalid_start_toast),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            markedStartTime = currentProgress;
        } else {
            if (markedStartTime != null && currentProgress < markedStartTime) {
                Toast.makeText(getContext(),
                        getString(R.string.sponsor_block_invalid_end_toast),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            markedEndTime = currentProgress;
        }

        if (markedStartTime != null) {
            binding.sponsorBlockControlsSegmentStart.setText(
                    millisecondsToString(markedStartTime));
        }

        if (markedEndTime != null) {
            binding.sponsorBlockControlsSegmentEnd.setText(
                    millisecondsToString(markedEndTime));
        }

        if (markedStartTime != null && markedEndTime != null) {
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener.onRequestNewPendingSegment(
                        markedStartTime, markedEndTime);
            }
        }

        final String message = isStart
                ? getString(R.string.sponsor_block_marked_start_toast)
                : getString(R.string.sponsor_block_marked_end_toast);
        Toast.makeText(getContext(),
                message,
                Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetTextI18n")
    private void doClearPendingSegment() {
        new AlertDialog
                .Builder(requireContext())
                .setMessage(R.string.sponsor_block_clear_marked_segment_prompt)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    clearPendingSegment();
                    dialog.dismiss();
                })
                .show();
    }

    private void doPendingSegmentSeek(final boolean isStart) {
        if (isStart && markedStartTime != null) {
            onSkipToTimestampRequested((long) markedStartTime);
        } else if (markedEndTime != null) {
            onSkipToTimestampRequested((long) markedEndTime);
        }
    }

    private void doSubmitPendingSegment() {
        final Context context = requireContext();

        if (markedStartTime == null || markedEndTime == null) {
            Toast.makeText(context,
                    getString(R.string.sponsor_block_missing_times_toast),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.sponsor_block_select_a_category);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
        builder.setItems(new String[]{
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.SPONSOR),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.INTRO),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.OUTRO),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.INTERACTION),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.HIGHLIGHT),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.SELF_PROMO),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.NON_MUSIC),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.PREVIEW),
                SponsorBlockHelper.convertCategoryToFriendlyName(
                        context, SponsorBlockCategory.FILLER)
        }, (dialog, which) -> {
            final SponsorBlockCategory category = SponsorBlockCategory.values()[which];
            final SponsorBlockAction action = category == SponsorBlockCategory.HIGHLIGHT
                    ? SponsorBlockAction.POI
                    : SponsorBlockAction.SKIP;
            final SponsorBlockSegment newSegment =
                    new SponsorBlockSegment(
                            "", markedStartTime, markedEndTime, category, action, -1);
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener.onRequestSubmitPendingSegment(newSegment);
            }
            dialog.dismiss();
        });
        builder.show();
    }

    @Override
    public void onSkipToTimestampRequested(final long positionMillis) {
        if (sponsorBlockFragmentListener != null) {
            sponsorBlockFragmentListener.onSeekToRequested(positionMillis);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            Icepick.restoreInstanceState(this, savedInstanceState);
        }
        refreshSponsorBlockSegments();
        updatePendingSegmentUI();
    }

    private void updatePendingSegmentUI() {
        if (markedStartTime != null) {
            binding.sponsorBlockControlsSegmentStart.setText(millisecondsToString(markedStartTime));
        }
        if (markedEndTime != null) {
            binding.sponsorBlockControlsSegmentEnd.setText(millisecondsToString(markedEndTime));
        }
    }
}
