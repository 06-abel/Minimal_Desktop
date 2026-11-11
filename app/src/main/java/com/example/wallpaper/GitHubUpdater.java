package com.example.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitHubUpdater {

    private static final String TAG = "GitHubUpdater";
    private static final String PREFS_NAME = "wallpaper_prefs";
    private static final String KEY_LAST_INSTALLED_TAG = "last_installed_github_tag";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void checkForUpdates(Activity activity, String owner, String repo, boolean showToastIfLatest) {
        if (owner == null || repo == null || owner.isEmpty() || repo.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Wallpaper-App");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    if (showToastIfLatest) {
                        mainHandler.post(() -> Toast.makeText(activity, "Még nincs közzétett Release a GitHubon (" + owner + "/" + repo + ")", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject releaseJson = new JSONObject(sb.toString());
                    String tagName = releaseJson.optString("tag_name", "");
                    String changelog = releaseJson.optString("body", "Nincs leírás.");

                    String downloadUrl = null;
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    String currentVersion = getCurrentVersionName(activity);
                    String cleanLatestTag = tagName.replaceAll("[^0-9.]", "");
                    String cleanCurrentVersion = currentVersion.replaceAll("[^0-9.]", "");

                    SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String lastInstalledTag = prefs.getString(KEY_LAST_INSTALLED_TAG, "");

                    boolean isNewer = isVersionNewer(cleanLatestTag, cleanCurrentVersion);

                    // Prevent loop if user already installed this exact tag
                    if (tagName.equalsIgnoreCase(lastInstalledTag) && !showToastIfLatest) {
                        isNewer = false;
                    }

                    final boolean finalIsNewer = isNewer;
                    String finalDownloadUrl = downloadUrl;
                    mainHandler.post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;

                        if (finalIsNewer) {
                            if (finalDownloadUrl != null) {
                                showUpdateDialog(activity, tagName, changelog, finalDownloadUrl);
                            } else if (showToastIfLatest) {
                                Toast.makeText(activity, "Új verzió van a GitHubon (" + tagName + "), de nincs hozzá csatolva .apk fájl!", Toast.LENGTH_LONG).show();
                            }
                        } else if (showToastIfLatest) {
                            Toast.makeText(activity, "A legújabb verziót használod! (Telepítve: " + currentVersion + " | GitHub: " + tagName + ")", Toast.LENGTH_LONG).show();
                        }
                    });
                } else if (showToastIfLatest) {
                    mainHandler.post(() -> Toast.makeText(activity, "Hiba a GitHub elérésekor (HTTP " + responseCode + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Hiba a frissítések ellenőrzésekor: " + e.getMessage(), e);
                if (showToastIfLatest) {
                    mainHandler.post(() -> Toast.makeText(activity, "Nem sikerült kapcsolódni a GitHubhoz", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private static boolean isVersionNewer(String latest, String current) {
        if (latest.isEmpty()) return false;
        if (current.isEmpty()) return true;

        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? parseInteger(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? parseInteger(currentParts[i]) : 0;
            if (latestPart > currentPart) return true;
            if (latestPart < currentPart) return false;
        }
        return false;
    }

    private static int parseInteger(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void showUpdateDialog(Activity activity, String version, String changelog, String downloadUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("Új frissítés érhető el! (" + version + ")")
                .setMessage("Változások:\n\n" + changelog + "\n\nSzeretnéd most letölteni és telepíteni a frissítést?")
                .setPositiveButton("Frissítés", (dialog, which) -> downloadAndInstallApk(activity, version, downloadUrl))
                .setNegativeButton("Később", null)
                .show();
    }

    public static void downloadAndInstallApk(Activity activity, String tagName, String downloadUrl) {
        Toast.makeText(activity, "Frissítés letöltése folyamatban…", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

                File apkFile = new File(activity.getExternalCacheDir(), "update.apk");
                InputStream inputStream = conn.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                // Remember that we just installed this tag
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_LAST_INSTALLED_TAG, tagName)
                        .apply();

                mainHandler.post(() -> installApk(activity, apkFile));
            } catch (Exception e) {
                Log.e(TAG, "Hiba az APK letöltésekor: " + e.getMessage(), e);
                mainHandler.post(() -> Toast.makeText(activity, "Nem sikerült letölteni a frissítést", Toast.LENGTH_SHORT).show());
            }
        });
    }

    public static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Kérjük, engedélyezd az ismeretlen alkalmazások telepítését", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Hiba a telepítés indításakor: " + e.getMessage(), e);
            Toast.makeText(activity, "Nem sikerült elindítani a telepítőt", Toast.LENGTH_SHORT).show();
        }
    }
}
