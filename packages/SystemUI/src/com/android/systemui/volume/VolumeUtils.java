/*
 * Copyright (C) 2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class VolumeUtils implements TunerService.Tunable {
    private static final String TAG = "VolumeUtils";

    public static final String VOLUME_SOUND_HAPTICS =
            "system:" + "volume_sound_haptics";

    private static final int SOUND_HAPTICS_DELAY = 50;
    private static final int SOUND_HAPTICS_DURATION = 2000;

    private Ringtone mRingtone;
    private MediaPlayer mMediaPlayer = null;
    private AudioManager mAudioManager;
    private Context mContext;
    private Handler mHandler;
    private final TunerService mTunerService;

    private boolean mSoundHapticsEnabled;

    public VolumeUtils(Context context, AudioManager audioManager) {
        mAudioManager = audioManager;
        mContext = context;
        mHandler = new Handler();
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(mp -> stopPlayback());
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, VOLUME_SOUND_HAPTICS);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case VOLUME_SOUND_HAPTICS:
                mSoundHapticsEnabled = TunerService.parseIntegerSwitch(newValue, false);
                break;
            default:
                break;
        }
    }

    public void playSoundForStreamType(int streamType) {
        if (!mSoundHapticsEnabled) return;
        Uri soundUri = null;
        switch (streamType) {
            case AudioManager.STREAM_RING:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                break;
            case AudioManager.STREAM_NOTIFICATION:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                break;
            case AudioManager.STREAM_ALARM:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                break;
        }

        playSound(soundUri, streamType);
    }

    private void playSound(Uri soundUri, int streamType) {
        stopPlayback();
        if (soundUri == null) {
            soundUri = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.volume_control_sound);
        }
        try {
            if (streamType == AudioManager.STREAM_RING) {
                mRingtone = RingtoneManager.getRingtone(mContext, soundUri);
                mRingtone.play();
                mHandler.postDelayed(() -> stopPlayback(), SOUND_HAPTICS_DURATION);
            } else {
                mMediaPlayer.setDataSource(mContext, soundUri);
                mMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(getAudioUsageForStreamType(streamType))
                        .build());
                mMediaPlayer.setOnPreparedListener(mp -> {
                    mMediaPlayer.start();
                    mHandler.postDelayed(() -> stopPlayback(), SOUND_HAPTICS_DURATION);
                });
                mMediaPlayer.prepareAsync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not play sound: " + e.getMessage());
        }
    }

    private int getAudioUsageForStreamType(int streamType) {
        switch (streamType) {
            case AudioManager.STREAM_RING:
                return AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
            case AudioManager.STREAM_NOTIFICATION:
                return AudioAttributes.USAGE_NOTIFICATION;
            case AudioManager.STREAM_ALARM:
                return AudioAttributes.USAGE_ALARM;
            default:
                return AudioAttributes.USAGE_UNKNOWN;
        }
    }

    private void startRingtone() {
        if (mRingtone != null) {
            mRingtone.play();
        }
    }

    private void startMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(0);
            mMediaPlayer.start();
        }
    }

    private void stopPlayback() {
        if (mRingtone != null && mRingtone.isPlaying()) {
            mRingtone.stop();
        }
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.reset();
        }
    }

    public void onDestroy() {
        mTunerService.removeTunable(this);
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mRingtone != null) {
            mRingtone.stop();
            mRingtone = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }
}
