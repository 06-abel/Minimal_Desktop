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
import java.util.Locale;
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

    private static boolean isIgnoredHolidayOrBirthday(String title, String calendarName) {
        if (title == null) return true;
        String lowerTitle = title.toLowerCase(Locale.getDefault());

        if (lowerTitle.contains("boldog születésnapot")
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
                || lowerTitle.contains("nagypéntek")) {
            return true;
        }

        if (calendarName != null) {
            String lowerCal = calendarName.toLowerCase(Locale.getDefault());
            if (lowerCal.contains("holiday")
                    || lowerCal.contains("ünnep")
                    || lowerCal.contains("birthday")
                    || lowerCal.contains("születésnap")
                    || lowerCal.contains("névnap")) {
                return true;
            }
        }

        return false;
    }

    public static List<CalendarEventModel> loadNext24HoursEvents(Context context) {
        List<CalendarEventModel> events = new ArrayList<>();
        if (!hasCalendarPermission(context)) {
            return events;
        }

        Set<String> completedIds = getCompletedEventIds(context);
        Set<Long> loadedEventIds = new HashSet<>();

        long now = System.currentTimeMillis();
        long next24h = now + (24 * 60 * 60 * 1000L);

        // 1. Primary Query: CalendarContract.Instances for next 24 hours
        try {
            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, now);
            ContentUris.appendId(builder, next24h);

            String[] projection = new String[]{
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME
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
                    String calName = cursor.getString(5);

                    // Skip past events, empty titles, or holiday/birthday spam
                    if (endTime < now && !isAllDay) {
                        continue;
                    }

                    if (title != null && !title.trim().isEmpty() && !isIgnoredHolidayOrBirthday(title, calName)) {
                        CalendarEventModel event = new CalendarEventModel(eventId, title, startTime, endTime, isAllDay);
                        if (completedIds.contains(String.valueOf(eventId))) {
                            event.setCompleted(true);
                        }
                        events.add(event);
                        loadedEventIds.add(eventId);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Hiba az Instances lekérdezésekor: " + e.getMessage(), e);
        }

        // 2. Secondary Query: CalendarContract.Events for synced external calendars (e.g. Neptun / Timetable)
        try {
            String selection = CalendarContract.Events.DELETED + " = 0 AND "
                    + "((" + CalendarContract.Events.DTSTART + " <= ? AND " + CalendarContract.Events.DTEND + " >= ?) OR "
                    + "(" + CalendarContract.Events.ALL_DAY + " = 1 AND " + CalendarContract.Events.DTSTART + " <= ?))";
            String[] selectionArgs = new String[]{
                    String.valueOf(next24h),
                    String.valueOf(now),
                    String.valueOf(next24h)
            };

            String[] projection = new String[]{
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME
            };

            Cursor cursor = context.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    CalendarContract.Events.DTSTART + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(0);
                    if (loadedEventIds.contains(eventId)) {
                        continue;
                    }
                    String title = cursor.getString(1);
                    long startTime = cursor.getLong(2);
                    long endTime = cursor.getLong(3);
                    boolean isAllDay = cursor.getInt(4) == 1;
                    String calName = cursor.getString(5);

                    if (endTime < now && !isAllDay) {
                        continue;
                    }

                    if (title != null && !title.trim().isEmpty() && !isIgnoredHolidayOrBirthday(title, calName)) {
                        CalendarEventModel event = new CalendarEventModel(eventId, title, startTime, endTime, isAllDay);
                        if (completedIds.contains(String.valueOf(eventId))) {
                            event.setCompleted(true);
                        }
                        events.add(event);
                        loadedEventIds.add(eventId);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Hiba az Events lekérdezésekor: " + e.getMessage(), e);
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
