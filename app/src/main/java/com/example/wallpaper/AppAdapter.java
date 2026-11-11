package com.example.wallpaper;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppModel app);
    }

    private List<AppModel> apps = new ArrayList<>();
    private final OnAppClickListener listener;

    public AppAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppModel> apps) {
        this.apps = apps != null ? apps : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = apps.get(position);
        holder.tvAppLabel.setText(app.getLabel());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppClick(app);
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppLabel = itemView.findViewById(R.id.tvAppLabel);
        }
    }
}
