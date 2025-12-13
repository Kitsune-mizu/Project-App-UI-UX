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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    // === CONSTANTS ===
    private static final String TAG = "SplashActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;

    // === UI COMPONENTS ===
    private FrameLayout flagContainer;
    private ImageView imgFlag;
    private TextView tvGreeting;
    private LottieAnimationView lottieAnimationView;

    // === LOCATION FIELDS ===
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String lastCountry = "";

    // === HANDLERS ===
    private final Handler handler = new Handler();

    // === LIFECYCLE METHODS ===
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        UserSession.init(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        playSplashSequence();
    }

    // === VIEW INITIALIZATION ===
    private void initViews() {
        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        flagContainer = findViewById(R.id.flagContainer);
        imgFlag = findViewById(R.id.imgFlag);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvGreeting.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat));
    }

    // === SPLASH SEQUENCE & NAVIGATION ===
    private void playSplashSequence() {
        lottieAnimationView.setAnimation(R.raw.splash_animation);
        lottieAnimationView.playAnimation();
        handler.postDelayed(this::checkLocationPermission, 3000);
    }

    private void goNext() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        startActivity(new Intent(this,
                UserSession.getInstance().isLoggedIn() ? MainActivity.class : LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // === LOCATION & PERMISSION HANDLING ===
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);

        } else startRealtimeLocationUpdates();
    }

    @SuppressLint("MissingPermission")
    private void startRealtimeLocationUpdates() {
        if (fusedLocationClient == null) return;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) {
                    Log.w(TAG, "Location is null, waiting for next update...");
                    return;
                }

                Log.d(TAG, "Location received: " + location.getLatitude() + ", " + location.getLongitude());
                handleLocation(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

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

    // === GEOLOCATION PROCESSING ===
    private void handleLocation(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(SplashActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        updateCountry(addresses.get(0).getCountryCode());
                    } else {
                        Log.w(TAG, "Address list empty, using fallback.");
                        getNetworkLocationFallback();
                    }
                });

            } catch (Exception e) {
                // Catches IOException (e.g., timeout) and other exceptions
                Log.e(TAG, "Geocoder error: " + e.getMessage());
                runOnUiThread(this::getNetworkLocationFallback);
            }
        }).start();
    }

    private void updateCountry(String countryCode) {
        if (countryCode == null) return;
        countryCode = countryCode.toLowerCase(Locale.ROOT);

        if (!countryCode.equals(lastCountry)) {
            lastCountry = countryCode;
            showFlagSequence(countryCode);
        }
    }

    // === FALLBACK MECHANISMS (NETWORK/LOCALE) ===
    private void getNetworkLocationFallback() {
        new Thread(() -> {
            try {
                String countryCode = getCountryCodeFromIP();
                runOnUiThread(() -> updateCountry(countryCode.isEmpty() ? Locale.getDefault().getCountry() : countryCode));
            } catch (Exception e) {
                Log.e(TAG, "Network location failed: " + e.getMessage());
                runOnUiThread(this::useLocaleFallback);
            }
        }).start();
    }

    private void useLocaleFallback() {
        updateCountry(Locale.getDefault().getCountry().toLowerCase());
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

    // === NETWORK UTILITIES ===
    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    // === UI ANIMATION AND CONTENT DISPLAY ===
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
                        String greeting = getManualGreeting(countryCode);
                        runOnUiThread(() -> animateGreetingChange(greeting));
                    }).start());
        });
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

    // === GREETING LOGIC ===
    private String getManualGreeting(String countryCode) {
        switch (countryCode.toLowerCase()) {

            // Asia & Oceania
            case "id": return "Halo!";
            case "my": return "Hai!";
            case "sg": return "Hello!";
            case "ph": return "Kamusta!";
            case "th": return "สวัสดี";
            case "vn": return "Xin chào!";
            case "la": return "ສະບາຍດີ";
            case "kh": return "សួស្តី";
            case "mm": return "မင်္ဂလာပါ";
            case "jp": return "こんにちは";
            case "kr": return "안녕하세요";
            case "cn": return "你好";
            case "tw": return "你好";
            case "hk": return "你好";
            case "in": return "नमस्ते";
            case "pk": return "السلام عليكم";
            case "bd": return "হ্যালো";
            case "sa": return "السلام عليكم";
            case "ae": return "مرحبا";
            case "il": return "שלום";
            case "ir": return "سلام";
            case "tr": return "Merhaba!";

            // Europe
            case "uk": return "Hello!";
            case "ie": return "Dia dhuit!";
            case "fr": return "Bonjour!";
            case "de": return "Hallo!";
            case "es": return "¡Hola!";
            case "pt": return "Olá!";
            case "it": return "Ciao!";
            case "nl": return "Hallo!";
            case "be": return "Hallo!";
            case "ch": return "Grüezi!";
            case "gr": return "Γειά σου";
            case "pl": return "Cześć!";
            case "se": return "Hej!";
            case "no": return "Hallo!";
            case "dk": return "Hej!";
            case "fi": return "Hei!";
            case "ru": return "Привет!";
            case "ua": return "Привіт!";
            case "cz": return "Ahoj!";
            case "sk": return "Ahoj!";
            case "hu": return "Szia!";
            case "ro": return "Salut!";
            case "bg": return "Здравей";

            // Americas
            case "us": return "Hello!";
            case "ca": return "Hello!";
            case "mx": return "¡Hola!";
            case "br": return "Olá!";
            case "ar": return "¡Hola!";
            case "cl": return "¡Hola!";
            case "co": return "¡Hola!";
            case "ve": return "¡Hola!";
            case "pe": return "¡Hola!";

            // Africa
            case "za": return "Hello!";
            case "eg": return "مرحبا";
            case "ma": return "مرحبا";
            case "dz": return "مرحبا";
            case "ng": return "Hello!";
            case "ke": return "Jambo!";
            case "et": return "Selam!";

            // Australia
            case "au": return "Hello!";
            case "nz": return "Hello!";

            default: return "Hello!"; // Universal fallback
        }
    }

    // === ANIMATION HELPERS ===
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
}