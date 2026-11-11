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

    public interface OnAppLongClickListener {
        boolean onAppLongClick(AppModel app);
    }

    private List<AppModel> apps = new ArrayList<>();
    private final OnAppClickListener clickListener;
    private final OnAppLongClickListener longClickListener;

    public AppAdapter(OnAppClickListener clickListener, OnAppLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
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
        holder.tvFavoriteStar.setVisibility(app.isFavorite() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAppClick(app);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                return longClickListener.onAppLongClick(app);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppLabel;
        TextView tvFavoriteStar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppLabel = itemView.findViewById(R.id.tvAppLabel);
            tvFavoriteStar = itemView.findViewById(R.id.tvFavoriteStar);
        }
    }
}
