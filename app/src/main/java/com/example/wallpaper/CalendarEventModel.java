package com.example.wallpaper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CalendarEventModel {
    private final long id;
    private final String title;
    private final long startTime;
    private final long endTime;
    private final boolean isAllDay;
    private boolean isCompleted;

    public CalendarEventModel(long id, String title, long startTime, long endTime, boolean isAllDay) {
        this.id = id;
        this.title = title != null && !title.isEmpty() ? title : "Cím nélküli esemény";
        this.startTime = startTime;
        this.endTime = endTime;
        this.isAllDay = isAllDay;
        this.isCompleted = false;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean isAllDay() {
        return isAllDay;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public String getFormattedTime() {
        if (isAllDay) {
            return "Egész napos";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(startTime)) + " - " + sdf.format(new Date(endTime));
    }
}
