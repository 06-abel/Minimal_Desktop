package com.example.wallpaper;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarManager {

    private static final String TAG = "CalendarManager";
    private static final String PREFS_NAME = "calendar_prefs";
    private static final String KEY_COMPLETED_EVENTS = "completed_event_ids";

    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static Set<String> getCompletedEventIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_COMPLETED_EVENTS, new HashSet<>());
    }

    public static void toggleEventCompleted(Context context, long eventId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> completedSet = new HashSet<>(getCompletedEventIds(context));
        String idStr = String.valueOf(eventId);

        if (completedSet.contains(idStr)) {
            completedSet.remove(idStr);
        } else {
            completedSet.add(idStr);
        }

        prefs.edit().putStringSet(KEY_COMPLETED_EVENTS, completedSet).apply();
    }

    public static List<CalendarEventModel> loadNext24HoursEvents(Context context) {
        List<CalendarEventModel> events = new ArrayList<>();
        if (!hasCalendarPermission(context)) {
            return events;
        }

        try {
            long now = System.currentTimeMillis();
            long next24h = now + (24 * 60 * 60 * 1000L);

            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, now);
            ContentUris.appendId(builder, next24h);

            String[] projection = new String[]{
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY
            };

            Cursor cursor = context.getContentResolver().query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
            );

            Set<String> completedIds = getCompletedEventIds(context);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(0);
                    String title = cursor.getString(1);
                    long startTime = cursor.getLong(2);
                    long endTime = cursor.getLong(3);
                    boolean isAllDay = cursor.getInt(4) == 1;

                    CalendarEventModel event = new CalendarEventModel(eventId, title, startTime, endTime, isAllDay);
                    if (completedIds.contains(String.valueOf(eventId))) {
                        event.setCompleted(true);
                    }
                    events.add(event);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Hiba a naptári események lekérdezésekor: " + e.getMessage(), e);
        }

        // Sort: Incomplete events first (chronological), then completed events
        events.sort((e1, e2) -> {
            if (e1.isCompleted() != e2.isCompleted()) {
                return e1.isCompleted() ? 1 : -1;
            }
            return Long.compare(e1.getStartTime(), e2.getStartTime());
        });

        return events;
    }
}
