package com.example.wallpaper;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    public interface OnEventToggleListener {
        void onEventToggle(CalendarEventModel event);
    }

    private List<CalendarEventModel> events = new ArrayList<>();
    private final OnEventToggleListener listener;

    public CalendarAdapter(OnEventToggleListener listener) {
        this.listener = listener;
    }

    public void setEvents(List<CalendarEventModel> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CalendarEventModel event = events.get(position);

        holder.tvEventTitle.setText(event.getTitle());
        holder.tvEventTime.setText(event.getFormattedTime());
        holder.cbCompleted.setChecked(event.isCompleted());

        if (event.isCompleted()) {
            holder.tvEventTitle.setPaintFlags(holder.tvEventTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvEventTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
        } else {
            holder.tvEventTitle.setPaintFlags(holder.tvEventTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvEventTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEventToggle(event);
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbCompleted;
        TextView tvEventTitle;
        TextView tvEventTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbCompleted = itemView.findViewById(R.id.cbEventCompleted);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvEventTime = itemView.findViewById(R.id.tvEventTime);
        }
    }
}
