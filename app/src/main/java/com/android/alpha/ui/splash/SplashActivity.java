package com.android.alpha.ui.splash;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.ui.main.MainActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.location.*;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;

    private FrameLayout flagContainer;
    private ImageView imgFlag;
    private TextView tvGreeting;
    private LottieAnimationView lottieAnimationView;

    private final Handler handler = new Handler();
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String lastCountry = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        UserSession.init(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        playSplashSequence();
    }

    private void initViews() {
        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        flagContainer = findViewById(R.id.flagContainer);
        imgFlag = findViewById(R.id.imgFlag);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvGreeting.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat));
    }

    private void playSplashSequence() {
        lottieAnimationView.setAnimation(R.raw.splash_animation);
        lottieAnimationView.playAnimation();
        handler.postDelayed(this::checkLocationPermission, 3000);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);

        } else startRealtimeLocationUpdates();
    }

    @SuppressLint("MissingPermission")
    private void startRealtimeLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) handleLocation(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    private void handleLocation(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                updateCountry(addresses.get(0).getCountryCode());
            } else getNetworkLocationFallback();

        } catch (Exception e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage(), e);
            getNetworkLocationFallback();
        }
    }

    private void updateCountry(String countryCode) {
        if (countryCode == null) return;
        countryCode = countryCode.toLowerCase(Locale.ROOT);

        if (!countryCode.equals(lastCountry)) {
            lastCountry = countryCode;
            showFlagSequence(countryCode);
        }
    }

    private void getNetworkLocationFallback() {
        new Thread(() -> {
            try {
                String countryCode = getCountryCodeFromIP();
                runOnUiThread(() -> updateCountry(countryCode.isEmpty() ? Locale.getDefault().getCountry() : countryCode));
            } catch (Exception e) {
                Log.e(TAG, "Network lokasi gagal: " + e.getMessage());
                runOnUiThread(this::useLocaleFallback);
            }
        }).start();
    }

    private String getCountryCodeFromIP() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://ipapi.co/json/").openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);

        return new JSONObject(readResponse(connection))
                .optString("country_code", "")
                .toLowerCase(Locale.ROOT);
    }

    private void useLocaleFallback() {
        updateCountry(Locale.getDefault().getCountry().toLowerCase());
    }

    private void showFlagSequence(String countryCode) {
        String flagUrl = "https://flagcdn.com/w320/" + countryCode + ".png";

        fadeOut(lottieAnimationView, () -> {
            flagContainer.setVisibility(View.VISIBLE);

            int size = (int) (tvGreeting.getLineHeight() * 1.2f);
            RequestOptions options = new RequestOptions()
                    .override(size, size)
                    .transform(new RoundedCorners(size / 4));

            Glide.with(this)
                    .load(flagUrl)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade(800))
                    .into(imgFlag);

            fadeIn(imgFlag, () ->
                    new Thread(() -> {
                        String greeting = translateGreeting(getLanguageFromCountryISO639_1(countryCode), countryCode);
                        runOnUiThread(() -> animateGreetingChange(greeting));
                    }).start());
        });
    }

    private String getLanguageFromCountryISO639_1(String countryCode) {
        Locale locale = new Locale("", countryCode);
        String lang = locale.getLanguage();
        if (!lang.isEmpty()) return lang.toLowerCase(Locale.ROOT);

        switch (countryCode) {
            case "ru": return "ru";
            case "jp": return "ja";
            case "kr": return "ko";
            case "cn": return "zh";
            default: return Locale.getDefault().getLanguage();
        }
    }

    private void animateGreetingChange(String newText) {
        tvGreeting.animate().cancel();
        tvGreeting.setAlpha(0f);
        tvGreeting.setVisibility(View.VISIBLE);

        tvGreeting.setText(newText);
        tvGreeting.animate()
                .alpha(1f)
                .setDuration(600)
                .withEndAction(() -> handler.postDelayed(this::goNext, 2000))
                .start();
    }

    private String translateGreeting(String targetLang, String countryCode) {
        try {
            JSONObject jsonInput = new JSONObject();
            jsonInput.put("q", "Hello!");
            jsonInput.put("source", "en");
            jsonInput.put("target", targetLang);
            jsonInput.put("format", "text");

            HttpURLConnection connection = openPostConnection();
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(jsonInput.toString());
            }

            String response = readResponse(connection);
            if (connection.getResponseCode() != 200 || response.startsWith("<!DOCTYPE")) {
                return getManualGreeting(countryCode);
            }

            return new JSONObject(response).optString("translatedText", getManualGreeting(countryCode));

        } catch (Exception e) {
            Log.e(TAG, "Terjemahan gagal: " + e.getMessage(), e);
            return getManualGreeting(countryCode);
        }
    }

    private HttpURLConnection openPostConnection() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://libretranslate.com/translate").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    // Manual Greeting
    private String getManualGreeting(String countryCode) {
        switch (countryCode.toLowerCase()) {

            // Asia & Oceania
            case "id": return "Halo!";         // Indonesia
            case "my": return "Hai!";          // Malaysia
            case "sg": return "Hello!";        // Singapore
            case "ph": return "Kamusta!";      // Philippines
            case "th": return "สวัสดี";         // Thailand
            case "vn": return "Xin chào!";     // Vietnam
            case "la": return "ສະບາຍດີ";       // Laos
            case "kh": return "សួស្តី";         // Cambodia
            case "mm": return "မင်္ဂလာပါ";      // Myanmar
            case "jp": return "こんにちは";       // Japan
            case "kr": return "안녕하세요";       // Korea
            case "cn": return "你好";            // China
            case "tw": return "你好";            // Taiwan
            case "hk": return "你好";            // Hongkong
            case "in": return "नमस्ते";          // India (Hindi)
            case "pk": return "السلام عليكم";     // Pakistan (Urdu)
            case "bd": return "হ্যালো";           // Bangladesh
            case "sa": return "السلام عليكم";     // Saudi Arabia
            case "ae": return "مرحبا";           // UAE
            case "il": return "שלום";            // Israel (Hebrew)
            case "ir": return "سلام";            // Persia
            case "tr": return "Merhaba!";       // Turkey

            // Eropa
            case "uk": return "Hello!";        // UK
            case "ie": return "Dia dhuit!";    // Ireland
            case "fr": return "Bonjour!";      // France
            case "de": return "Hallo!";        // Germany
            case "es": return "¡Hola!";        // Spain
            case "pt": return "Olá!";          // Portugal
            case "it": return "Ciao!";         // Italy
            case "nl": return "Hallo!";        // Netherlands
            case "be": return "Hallo!";        // Belgium
            case "ch": return "Grüezi!";       // Switzerland
            case "gr": return "Γειά σου";       // Greece
            case "pl": return "Cześć!";        // Poland
            case "se": return "Hej!";          // Sweden
            case "no": return "Hallo!";        // Norway
            case "dk": return "Hej!";          // Denmark
            case "fi": return "Hei!";          // Finland
            case "ru": return "Привет!";        // Russia
            case "ua": return "Привіт!";        // Ukraine
            case "cz": return "Ahoj!";         // Czech
            case "sk": return "Ahoj!";         // Slovakia
            case "hu": return "Szia!";         // Hungary
            case "ro": return "Salut!";        // Romania
            case "bg": return "Здравей";        // Bulgaria

            // Amerika
            case "us": return "Hello!";        // USA
            case "ca": return "Hello!";        // Canada (English)
            case "mx": return "¡Hola!";        // Mexico
            case "br": return "Olá!";          // Brazil (Portuguese)
            case "ar": return "¡Hola!";        // Argentina
            case "cl": return "¡Hola!";        // Chile
            case "co": return "¡Hola!";        // Colombia
            case "ve": return "¡Hola!";        // Venezuela
            case "pe": return "¡Hola!";        // Peru

            // Africa
            case "za": return "Hello!";        // South Africa
            case "eg": return "مرحبا";          // Egypt
            case "ma": return "مرحبا";          // Morocco
            case "dz": return "مرحبا";          // Algeria
            case "ng": return "Hello!";        // Nigeria
            case "ke": return "Jambo!";        // Kenya
            case "et": return "Selam!";        // Ethiopia

            // Australia
            case "au": return "Hello!";        // Australia
            case "nz": return "Hello!";        // New Zealand

            default: return "Hello!"; // fallback universal
        }
    }

    // Animation Helpers
    private void fadeIn(View view, Runnable endAction) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(800).withEndAction(endAction).start();
    }

    private void fadeOut(View view, Runnable endAction) {
        view.animate().alpha(0f).setDuration(600)
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    if (endAction != null) endAction.run();
                }).start();
    }

    private void goNext() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        startActivity(new Intent(this,
                UserSession.getInstance().isLoggedIn() ? MainActivity.class : LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // Permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startRealtimeLocationUpdates();

        } else getNetworkLocationFallback();
    }
}
