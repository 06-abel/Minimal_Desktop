package com.example.wallpaper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.List;

public class MediaControlManager {

    public interface MediaUpdateListener {
        void onMediaUpdated(boolean isPlaying, String title, String artist, String appName, boolean needsPermission);
    }

    public static boolean isNotificationListenerGranted(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(context.getPackageName());
    }

    public static void openNotificationListenerSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static MediaController getActiveMediaController(Context context) {
        if (!isNotificationListenerGranted(context)) {
            return null;
        }

        try {
            MediaSessionManager mm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(context, MediaNotificationListener.class);
            List<MediaController> controllers = mm.getActiveSessions(cn);

            if (controllers != null && !controllers.isEmpty()) {
                // Look for Spotify first
                for (MediaController mc : controllers) {
                    if ("com.spotify.music".equals(mc.getPackageName())) {
                        return mc;
                    }
                }
                // Otherwise return the first active controller (YouTube Music, Podcasts, etc.)
                return controllers.get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void updateMediaState(Context context, MediaUpdateListener listener) {
        if (listener == null) return;

        if (!isNotificationListenerGranted(context)) {
            listener.onMediaUpdated(false, "Zenevezérlő engedélyezése", "Kattints a hozzáférés megadásához", "Minimal Launcher", true);
            return;
        }

        MediaController mc = getActiveMediaController(context);
        if (mc != null) {
            MediaMetadata metadata = mc.getMetadata();
            PlaybackState pbState = mc.getPlaybackState();

            String title = metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
            String artist = metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;

            boolean isPlaying = pbState != null && pbState.getState() == PlaybackState.STATE_PLAYING;

            String appName = "com.spotify.music".equals(mc.getPackageName()) ? "Spotify" : "Zene";

            if (title == null || title.isEmpty()) {
                title = "Nincs aktív zene";
                artist = "Nyisd meg a Spotify-t";
            }

            listener.onMediaUpdated(isPlaying, title, artist, appName, false);
        } else {
            listener.onMediaUpdated(false, "Spotify megnyitása", "Nincs lejátszás alatt lévő zene", "Spotify", false);
        }
    }

    public static void togglePlayPause(Context context) {
        MediaController mc = getActiveMediaController(context);
        if (mc != null) {
            PlaybackState pbState = mc.getPlaybackState();
            if (pbState != null && pbState.getState() == PlaybackState.STATE_PLAYING) {
                mc.getTransportControls().pause();
            } else {
                mc.getTransportControls().play();
            }
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    public static void skipToNext(Context context) {
        MediaController mc = getActiveMediaController(context);
        if (mc != null) {
            mc.getTransportControls().skipToNext();
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    public static void skipToPrevious(Context context) {
        MediaController mc = getActiveMediaController(context);
        if (mc != null) {
            mc.getTransportControls().skipToPrevious();
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    private static void sendMediaKeyEvent(Context context, int keyCode) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            }
        } catch (Exception ignored) {}
    }
}
