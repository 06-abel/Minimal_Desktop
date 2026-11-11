package com.example.wallpaper;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AppAdapter.OnAppClickListener {

    private EditText etSearch;
    private TextView tvHeaderLabel;
    private View bannerSetDefault;
    private Day24ClockRingView clockRingView;
    private TextView tvDayPercent;
    private AppAdapter appAdapter;

    private final List<AppModel> allApps = new ArrayList<>();
    private final List<AppModel> filteredApps = new ArrayList<>();

    private final Handler dayPercentHandler = new Handler(Looper.getMainLooper());
    private final Runnable dayPercentRunnable = new Runnable() {
        @Override
        public void run() {
            if (clockRingView != null && tvDayPercent != null) {
                float percent = clockRingView.getDayProgressPercentage();
                tvDayPercent.setText(getString(R.string.day_percent_text, percent));
            }
            dayPercentHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupBackHandler();
        loadInstalledApps();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        tvHeaderLabel = findViewById(R.id.tvHeaderLabel);
        bannerSetDefault = findViewById(R.id.bannerSetDefault);
        clockRingView = findViewById(R.id.clockRingView);
        tvDayPercent = findViewById(R.id.tvDayPercent);
        RecyclerView rvApps = findViewById(R.id.rvApps);

        appAdapter = new AppAdapter(this);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(appAdapter);

        findViewById(R.id.btnSettings).setOnClickListener(v -> openLauncherSettings());
        bannerSetDefault.setOnClickListener(v -> requestDefaultLauncher());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (etSearch.getText().length() > 0) {
                    etSearch.setText("");
                    etSearch.clearFocus();
                }
                // Do not exit activity as this is the Home launcher
            }
        });
    }

    private void loadInstalledApps() {
        allApps.clear();

        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(mainIntent, 0);
        String ownPackage = getPackageName();

        for (ResolveInfo ri : resolveInfoList) {
            String packageName = ri.activityInfo.packageName;
            if (packageName.equals(ownPackage)) {
                continue; // Skip launcher itself
            }
            String label = ri.loadLabel(pm).toString();
            String className = ri.activityInfo.name;

            allApps.add(new AppModel(label, packageName, className, null));
        }

        allApps.sort(Comparator.comparing(a -> a.getLabel().toLowerCase(Locale.getDefault())));

        filterApps(etSearch.getText().toString());
    }

    private void filterApps(String query) {
        filteredApps.clear();
        String trimmedQuery = query.toLowerCase(Locale.getDefault()).trim();

        if (trimmedQuery.isEmpty()) {
            filteredApps.addAll(allApps);
            tvHeaderLabel.setText(getString(R.string.all_apps_count, allApps.size()));
        } else {
            for (AppModel app : allApps) {
                if (app.getLabel().toLowerCase(Locale.getDefault()).contains(trimmedQuery)) {
                    filteredApps.add(app);
                }
            }
            if (filteredApps.isEmpty()) {
                tvHeaderLabel.setText(R.string.no_apps_found);
            } else {
                tvHeaderLabel.setText(getString(R.string.search_results_count, filteredApps.size()));
            }
        }

        appAdapter.setApps(filteredApps);
    }

    @Override
    public void onAppClick(AppModel app) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(app.getPackageName());
        if (launchIntent != null) {
            try {
                startActivity(launchIntent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.cant_launch_app, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.cant_launch_app, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isMyLauncherDefault() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            return getPackageName().equals(resolveInfo.activityInfo.packageName);
        }
        return false;
    }

    private void requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                    startActivity(intent);
                    return;
                }
            }
        }
        openLauncherSettings();
    }

    private void openLauncherSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, R.string.cant_open_settings, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        etSearch.setText("");
        etSearch.clearFocus();

        if (isMyLauncherDefault()) {
            bannerSetDefault.setVisibility(View.GONE);
        } else {
            bannerSetDefault.setVisibility(View.VISIBLE);
        }

        dayPercentHandler.removeCallbacks(dayPercentRunnable);
        dayPercentHandler.post(dayPercentRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        dayPercentHandler.removeCallbacks(dayPercentRunnable);
    }
}
