package com.example.wallpaper;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AppAdapter.OnAppClickListener, AppAdapter.OnAppLongClickListener {

    private static final String PREFS_NAME = "wallpaper_prefs";
    private static final String PREF_GITHUB_OWNER = "github_owner";
    private static final String PREF_GITHUB_REPO = "github_repo";
    private static final String PREF_FAVORITES = "favorite_packages";

    private EditText etSearch;
    private TextView tvHeaderLabel;
    private View bannerSetDefault;
    private Day24ClockRingView clockRingView;
    private TextView tvDayPercent;
    private TextView tvWeather;

    private TextView tvMediaTitle;
    private TextView tvMediaArtist;
    private ImageView btnMediaPlayPause;

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

    private final Handler mediaHandler = new Handler(Looper.getMainLooper());
    private final Runnable mediaRunnable = new Runnable() {
        @Override
        public void run() {
            refreshMediaState();
            mediaHandler.postDelayed(this, 2000);
        }
    };

    private boolean mediaPermissionNeeded = false;

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

        checkGitHubUpdates(false);
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        tvHeaderLabel = findViewById(R.id.tvHeaderLabel);
        bannerSetDefault = findViewById(R.id.bannerSetDefault);
        clockRingView = findViewById(R.id.clockRingView);
        tvDayPercent = findViewById(R.id.tvDayPercent);
        tvWeather = findViewById(R.id.tvWeather);

        tvMediaTitle = findViewById(R.id.tvMediaTitle);
        tvMediaArtist = findViewById(R.id.tvMediaArtist);
        btnMediaPlayPause = findViewById(R.id.btnMediaPlayPause);
        View layoutTrackInfo = findViewById(R.id.layoutTrackInfo);

        ImageView btnMediaPrev = findViewById(R.id.btnMediaPrev);
        ImageView btnMediaNext = findViewById(R.id.btnMediaNext);

        RecyclerView rvApps = findViewById(R.id.rvApps);

        appAdapter = new AppAdapter(this, this);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(appAdapter);

        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettingsDialog());
        bannerSetDefault.setOnClickListener(v -> requestDefaultLauncher());

        if (tvWeather != null) {
            tvWeather.setOnClickListener(v -> {
                Toast.makeText(this, "Időjárás frissítése…", Toast.LENGTH_SHORT).show();
                WeatherManager.fetchWeather(this, weatherText -> tvWeather.setText(weatherText));
            });
        }

        if (layoutTrackInfo != null) {
            layoutTrackInfo.setOnClickListener(v -> {
                if (mediaPermissionNeeded) {
                    MediaControlManager.openNotificationListenerSettings(this);
                } else {
                    launchSpotifyOrMusicApp();
                }
            });
        }

        if (btnMediaPrev != null) {
            btnMediaPrev.setOnClickListener(v -> {
                MediaControlManager.skipToPrevious(this);
                mediaHandler.postDelayed(this::refreshMediaState, 300);
            });
        }

        if (btnMediaPlayPause != null) {
            btnMediaPlayPause.setOnClickListener(v -> {
                MediaControlManager.togglePlayPause(this);
                mediaHandler.postDelayed(this::refreshMediaState, 300);
            });
        }

        if (btnMediaNext != null) {
            btnMediaNext.setOnClickListener(v -> {
                MediaControlManager.skipToNext(this);
                mediaHandler.postDelayed(this::refreshMediaState, 300);
            });
        }

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

    private void launchSpotifyOrMusicApp() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage("com.spotify.music");
        if (intent != null) {
            try {
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Spotify nyitása…", Toast.LENGTH_SHORT).show();
    }

    private void refreshMediaState() {
        MediaControlManager.updateMediaState(this, (isPlaying, title, artist, appName, needsPermission) -> {
            mediaPermissionNeeded = needsPermission;
            if (tvMediaTitle != null) tvMediaTitle.setText(title);
            if (tvMediaArtist != null) tvMediaArtist.setText(artist);
            if (btnMediaPlayPause != null) {
                btnMediaPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white);
            }
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

    private Set<String> getFavoritePackages() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getStringSet(PREF_FAVORITES, new HashSet<>());
    }

    private void toggleFavorite(AppModel app) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(getFavoritePackages());

        if (favorites.contains(app.getPackageName())) {
            favorites.remove(app.getPackageName());
            app.setFavorite(false);
            Toast.makeText(this, app.getLabel() + " eltávolítva a kedvencekből", Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(app.getPackageName());
            app.setFavorite(true);
            Toast.makeText(this, app.getLabel() + " hozzáadva a kedvencekhez ⭐", Toast.LENGTH_SHORT).show();
        }

        prefs.edit().putStringSet(PREF_FAVORITES, favorites).apply();

        sortApps();
        filterApps(etSearch.getText().toString());
    }

    private void loadInstalledApps() {
        allApps.clear();
        Set<String> favorites = getFavoritePackages();

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

            AppModel app = new AppModel(label, packageName, className, null);
            if (favorites.contains(packageName)) {
                app.setFavorite(true);
            }
            allApps.add(app);
        }

        sortApps();
        filterApps(etSearch.getText().toString());
    }

    private void sortApps() {
        allApps.sort((a1, a2) -> {
            if (a1.isFavorite() != a2.isFavorite()) {
                return a1.isFavorite() ? -1 : 1; // Favorites first
            }
            return a1.getLabel().toLowerCase(Locale.getDefault())
                    .compareTo(a2.getLabel().toLowerCase(Locale.getDefault()));
        });
    }

    private void filterApps(String query) {
        filteredApps.clear();
        String trimmedQuery = query.toLowerCase(Locale.getDefault()).trim();

        if (trimmedQuery.isEmpty()) {
            filteredApps.addAll(allApps);
            long favCount = allApps.stream().filter(AppModel::isFavorite).count();
            if (favCount > 0) {
                tvHeaderLabel.setText(getString(R.string.favorites_header_text, favCount, allApps.size()));
            } else {
                tvHeaderLabel.setText(getString(R.string.all_apps_count, allApps.size()));
            }
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

    @Override
    public boolean onAppLongClick(AppModel app) {
        String favOption = app.isFavorite() ? getString(R.string.remove_favorite) : getString(R.string.add_favorite);
        String[] options = new String[]{
                favOption,
                getString(R.string.app_details),
                getString(R.string.uninstall_app)
        };

        new AlertDialog.Builder(this)
                .setTitle(app.getLabel())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        toggleFavorite(app);
                    } else if (which == 1) {
                        openAppDetails(app);
                    } else if (which == 2) {
                        uninstallApp(app);
                    }
                })
                .show();

        return true;
    }

    private void openAppDetails(AppModel app) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + app.getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nem sikerült megnyitni az alkalmazás részleteit", Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstallApp(AppModel app) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + app.getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nem sikerült elindítani az eltávolítást", Toast.LENGTH_SHORT).show();
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

    private void checkGitHubUpdates(boolean showToastIfLatest) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String owner = prefs.getString(PREF_GITHUB_OWNER, "06-abel");
        String repo = prefs.getString(PREF_GITHUB_REPO, "Minimal_Desktop");

        if (!owner.isEmpty() && !repo.isEmpty()) {
            GitHubUpdater.checkForUpdates(this, owner, repo, showToastIfLatest);
        } else if (showToastIfLatest) {
            Toast.makeText(this, "Állítsd be a GitHub felhasználónevedet a Beállításokban!", Toast.LENGTH_LONG).show();
        }
    }

    private void openSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentOwner = prefs.getString(PREF_GITHUB_OWNER, "06-abel");
        String currentRepo = prefs.getString(PREF_GITHUB_REPO, "Minimal_Desktop");

        String[] options = new String[]{
                "Alapértelmezett Kezdőképernyő beállítása",
                "Zenevezérlési hozzáférés (Értesítés engedély)",
                "Frissítés keresése a GitHubon",
                "GitHub Repó beállítása (" + currentOwner + "/" + currentRepo + ")"
        };

        new AlertDialog.Builder(this)
                .setTitle("Beállítások")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestDefaultLauncher();
                    } else if (which == 1) {
                        MediaControlManager.openNotificationListenerSettings(this);
                    } else if (which == 2) {
                        checkGitHubUpdates(true);
                    } else if (which == 3) {
                        showGithubRepoInputDialog();
                    }
                })
                .setNegativeButton("Bezárás", null)
                .show();
    }

    private void showGithubRepoInputDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentOwner = prefs.getString(PREF_GITHUB_OWNER, "06-abel");
        String currentRepo = prefs.getString(PREF_GITHUB_REPO, "Minimal_Desktop");

        EditText input = new EditText(this);
        input.setHint("Felhasználónév/Repó (pl. janos/Wallpaper)");
        if (!currentOwner.isEmpty()) {
            input.setText(currentOwner + "/" + currentRepo);
        }
        input.setPadding(32, 24, 32, 24);

        new AlertDialog.Builder(this)
                .setTitle("GitHub Repó megadása")
                .setMessage("Add meg a GitHub felhasználóneved és a tárhelyed nevét 'felhasználónév/projekt' formátumban:")
                .setView(input)
                .setPositiveButton("Mentés", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.contains("/")) {
                        String[] parts = value.split("/", 2);
                        prefs.edit()
                                .putString(PREF_GITHUB_OWNER, parts[0].trim())
                                .putString(PREF_GITHUB_REPO, parts[1].trim())
                                .apply();
                        Toast.makeText(this, "GitHub repó elmentve!", Toast.LENGTH_SHORT).show();
                        checkGitHubUpdates(true);
                    } else {
                        Toast.makeText(this, "Érvénytelen formátum! Használd a 'felhasználó/repó' alakot.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Mégse", null)
                .show();
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

        mediaHandler.removeCallbacks(mediaRunnable);
        mediaHandler.post(mediaRunnable);

        WeatherManager.fetchWeather(this, weatherText -> {
            if (tvWeather != null) {
                tvWeather.setText(weatherText);
            }
        });

        loadInstalledApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        dayPercentHandler.removeCallbacks(dayPercentRunnable);
        mediaHandler.removeCallbacks(mediaRunnable);
    }
}
