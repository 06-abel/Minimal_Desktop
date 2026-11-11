package com.example.wallpaper;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarManager {

    private static final String TAG = "CalendarManager";
    private static final String PREFS_NAME = "calendar_prefs";
    private static final String KEY_COMPLETED_EVENTS = "completed_event_ids";

    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasWriteCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static Set<String> getCompletedEventIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_COMPLETED_EVENTS, new HashSet<>());
    }

    public static void toggleEventCompleted(Context context, CalendarEventModel event) {
        if (context == null || event == null) return;

        boolean newStatus = !event.isCompleted();
        event.setCompleted(newStatus);

        // 1. Update local SharedPreferences state
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> completedSet = new HashSet<>(getCompletedEventIds(context));
        String idStr = String.valueOf(event.getId());

        if (newStatus) {
            completedSet.add(idStr);
        } else {
            completedSet.remove(idStr);
        }
        prefs.edit().putStringSet(KEY_COMPLETED_EVENTS, completedSet).apply();

        // 2. Sync title change ("✔ " prefix) back to Google Calendar database
        if (hasWriteCalendarPermission(context)) {
            try {
                long eventId = event.getId();
                String rawTitle = event.getTitle();

                String newTitle;
                if (newStatus) {
                    if (!rawTitle.startsWith("✔ ")) {
                        newTitle = "✔ " + rawTitle;
                    } else {
                        newTitle = rawTitle;
                    }
                } else {
                    if (rawTitle.startsWith("✔ ")) {
                        newTitle = rawTitle.substring(2);
                    } else if (rawTitle.startsWith("[✔] ")) {
                        newTitle = rawTitle.substring(4);
                    } else {
                        newTitle = rawTitle;
                    }
                }

                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.TITLE, newTitle);

                Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                int rowsUpdated = context.getContentResolver().update(updateUri, values, null, null);
                Log.d(TAG, "Google Naptár frissítve, frissített sorok száma: " + rowsUpdated);
            } catch (Exception e) {
                Log.e(TAG, "Hiba a Google Naptár esemény frissítésekor: " + e.getMessage(), e);
            }
        }
    }

    private static boolean isIgnoredHolidayOrBirthday(String title) {
        if (title == null) return true;
        String lowerTitle = title.toLowerCase(Locale.getDefault()).trim();

        return lowerTitle.contains("boldog születésnapot")
                || lowerTitle.contains("születésnap")
                || lowerTitle.contains("névnap")
                || lowerTitle.contains("télapó")
                || lowerTitle.contains("mikulás")
                || lowerTitle.contains("mindenszentek")
                || lowerTitle.contains("szent istván")
                || lowerTitle.contains("munkaszüneti nap")
                || lowerTitle.contains("nemzeti ünnep")
                || lowerTitle.contains("újév")
                || lowerTitle.contains("karácsony")
                || lowerTitle.contains("húsvét")
                || lowerTitle.contains("pünkösd")
                || lowerTitle.contains("nagypéntek");
    }

    public static List<CalendarEventModel> loadNext24HoursEvents(Context context) {
        List<CalendarEventModel> events = new ArrayList<>();
        if (!hasCalendarPermission(context)) {
            return events;
        }

        Set<String> completedIds = getCompletedEventIds(context);
        Set<String> loadedKeys = new HashSet<>();

        // Window: From start of today (00:00) to end of tomorrow (23:59:59)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();
        long endOfTomorrow = startOfToday + (48 * 60 * 60 * 1000L);

        try {
            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, startOfToday);
            ContentUris.appendId(builder, endOfTomorrow);

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

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(0);
                    String title = cursor.getString(1);
                    long startTime = cursor.getLong(2);
                    long endTime = cursor.getLong(3);
                    boolean isAllDay = cursor.getInt(4) == 1;

                    if (startTime >= startOfToday && startTime <= endOfTomorrow) {
                        if (title != null && !title.trim().isEmpty() && !isIgnoredHolidayOrBirthday(title)) {
                            String uniqueKey = eventId + "_" + startTime;
                            if (loadedKeys.contains(uniqueKey)) {
                                continue;
                            }
                            loadedKeys.add(uniqueKey);

                            CalendarEventModel event = new CalendarEventModel(eventId, title, startTime, endTime, isAllDay);
                            if (completedIds.contains(String.valueOf(eventId)) || title.startsWith("✔ ") || title.startsWith("[✔] ")) {
                                event.setCompleted(true);
                            }
                            events.add(event);
                        }
                    }
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

        if (events.size() > 8) {
            return new ArrayList<>(events.subList(0, 8));
        }

        return events;
    }
}
