package com.example.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherManager {

    private static final String TAG = "WeatherManager";
    private static final String PREFS_NAME = "weather_prefs";
    private static final String KEY_WEATHER_TEXT = "cached_weather";
    private static final String KEY_LAST_FETCH = "last_fetch_time";
    private static final long FETCH_INTERVAL_MS = 30 * 60 * 1000; // 30 mins

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface WeatherCallback {
        void onWeatherFetched(String weatherText);
    }

    public static void fetchWeather(Context context, WeatherCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_WEATHER_TEXT, null);
        long lastFetch = prefs.getLong(KEY_LAST_FETCH, 0);

        if (cached != null && callback != null) {
            callback.onWeatherFetched(cached);
        }

        long currentTime = System.currentTimeMillis();
        if (cached != null && (currentTime - lastFetch < FETCH_INTERVAL_MS)) {
            return; // Cache is still fresh
        }

        executor.execute(() -> {
            try {
                // Open-Meteo API for Hungary (Budapest coordinates: 47.4979, 19.0402)
                String apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=47.4979&longitude=19.0402&current_weather=true";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject currentWeather = json.getJSONObject("current_weather");

                    double temp = currentWeather.getDouble("temperature");
                    int weatherCode = currentWeather.getInt("weathercode");

                    String weatherInfo = formatWeather(temp, weatherCode);

                    prefs.edit()
                            .putString(KEY_WEATHER_TEXT, weatherInfo)
                            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                            .apply();

                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onWeatherFetched(weatherInfo);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Hiba az időjárási adatok lekérésekor: " + e.getMessage(), e);
            }
        });
    }

    private static String formatWeather(double temp, int code) {
        int roundedTemp = (int) Math.round(temp);
        String tempStr = (roundedTemp > 0 ? "+" : "") + roundedTemp + "°C";

        String status;
        String emoji;

        switch (code) {
            case 0:
                emoji = "☀️";
                status = "Tiszta idő";
                break;
            case 1:
            case 2:
                emoji = "⛅";
                status = "Gyengén felhős";
                break;
            case 3:
                emoji = "☁️";
                status = "Borult";
                break;
            case 45:
            case 48:
                emoji = "🌫";
                status = "Ködös";
                break;
            case 51:
            case 53:
            case 55:
            case 61:
            case 63:
            case 65:
            case 80:
            case 81:
            case 82:
                emoji = "🌧";
                status = "Eső";
                break;
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86:
                emoji = "❄️";
                status = "Havazás";
                break;
            case 95:
            case 96:
            case 99:
                emoji = "🌩";
                status = "Zivatar";
                break;
            default:
                emoji = "🌡";
                status = "Időjárás";
                break;
        }

        return emoji + " " + tempStr + " • " + status;
    }
}
