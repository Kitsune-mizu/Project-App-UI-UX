package com.android.alpha.ui.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.utils.LocaleHelper;
import com.google.android.gms.location.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapFragment extends Fragment {

    // === CONSTANTS ===
    private final String TAG = "MapFragment";
    private final GeoPoint DEFAULT_POINT = new GeoPoint(-6.2088, 106.8456);

    // === UI COMPONENTS ===
    private MapView mapView;
    private EditText etSearch;
    private TextView tvLocationName;
    private RecyclerView rvSuggestions;

    // === LOCATION & NETWORK UTILITIES ===
    private FusedLocationProviderClient fusedLocationClient;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // === STATE & ADAPTERS ===
    private GeoPoint selectedPoint;
    private Marker currentMarker;
    private LocationSuggestionAdapter suggestionAdapter;
    private final List<LocationSuggestion> suggestionList = new ArrayList<>();
    private OnLocationSelectedListener listener;

    // === INTERFACES ===
    public interface OnLocationSelectedListener {
        void onLocationSelected(String location);
    }

    public void setOnLocationSelectedListener(OnLocationSelectedListener listener) {
        this.listener = listener;
    }

    // === PERMISSION LAUNCHER ===
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) getMyLocation();
                else showToast(R.string.location_permission_denied);
            });

    // === FRAGMENT LIFECYCLE ===
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        initViews(rootView);
        setupToolbar();
        setupMapView();
        setupRecyclerView();
        setupListeners(rootView);

        getMyLocation();
        return rootView;
    }

    // === INITIALIZATION & SETUP ===
    private void initViews(View rootView) {
        mapView = rootView.findViewById(R.id.osm_map);
        etSearch = rootView.findViewById(R.id.etSearch);
        tvLocationName = rootView.findViewById(R.id.tvLocationName);
        rvSuggestions = rootView.findViewById(R.id.rvSuggestions);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
    }

    private void setupToolbar() {
        if (getActivity() != null) {
            ((MainActivity) getActivity()).applyCurrentLanguage();
            getActivity().setTitle(R.string.map_title);
        }
    }

    private void setupMapView() {
        LocaleHelper.setLocale(requireContext(), getLanguage());

        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        Configuration.getInstance().setOsmdroidBasePath(requireContext().getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(requireContext().getCacheDir());

        mapView.setMultiTouchControls(true);

        mapView.setTileSource(new XYTileSource(
                "Mapnik",
                0,
                19,
                256,
                ".png",
                new String[]{"https://tile.openstreetmap.org/"}
        ));

        IMapController controller = mapView.getController();
        controller.setZoom(10.0);
        controller.setCenter(DEFAULT_POINT);

        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) {
                updateMarker(point);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint point) { return false; }
        }));
    }

    private void setupRecyclerView() {
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        suggestionAdapter = new LocationSuggestionAdapter(suggestionList, suggestion -> {
            etSearch.setText(suggestion.displayName);
            rvSuggestions.setVisibility(View.GONE);
            GeoPoint point = new GeoPoint(suggestion.lat, suggestion.lon);
            updateMarker(point);
            mapView.getController().setZoom(16.0);
            mapView.getController().animateTo(point);
            tvLocationName.setText(suggestion.displayName);
        });
        rvSuggestions.setAdapter(suggestionAdapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners(View rootView) {
        rootView.findViewById(R.id.btnCancel)
                .setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        rootView.findViewById(R.id.btnConfirm)
                .setOnClickListener(v -> {
                    if (selectedPoint != null && listener != null) {
                        listener.onLocationSelected(tvLocationName.getText().toString());
                        requireActivity().getSupportFragmentManager().popBackStack();
                    } else showToast(R.string.select_location_prompt);
                });

        rootView.findViewById(R.id.btnMyLocation)
                .setOnClickListener(v -> getMyLocation());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) fetchSuggestions(s.toString().trim());
                else rvSuggestions.setVisibility(View.GONE);
            }
        });

        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            rvSuggestions.setVisibility(View.GONE);
            return false;
        });
    }

    // === MAP MARKER & ACTIONS ===
    private void updateMarker(GeoPoint point) {
        if (currentMarker != null) mapView.getOverlays().remove(currentMarker);

        currentMarker = new Marker(mapView);
        currentMarker.setPosition(point);
        currentMarker.setTitle(getString(R.string.selected_location));
        currentMarker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker));
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setDraggable(true);

        currentMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker marker) {}
            @Override public void onMarkerDrag(Marker marker) { selectedPoint = marker.getPosition(); }
            @Override public void onMarkerDragEnd(Marker marker) {
                selectedPoint = marker.getPosition();
                fetchLocationName(selectedPoint);
            }
        });

        mapView.getOverlays().add(currentMarker);
        mapView.invalidate();
        selectedPoint = point;
        fetchLocationName(point);
    }

    // === LOCATION SERVICES ===
    @SuppressLint("MissingPermission")
    private void getMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(requireActivity());
        settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(response ->
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener(location -> {
                                    if (location != null) {
                                        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                                        updateMarker(point);
                                        mapView.getController().setZoom(16.0);
                                        mapView.getController().animateTo(point);
                                    } else showToast(R.string.gps_unavailable);
                                })
                                .addOnFailureListener(e -> showToast(R.string.failed_get_location))
                )
                .addOnFailureListener(e -> showToast(R.string.turn_on_gps_prompt));
    }

    // === NOMINATIM API CALLS ===
    private void fetchSuggestions(String query) {
        rvSuggestions.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5&accept-language=%s",
                        query.replace(" ", "+"), getLanguage());

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "AlphaApp/1.0 (alpha@example.com)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) return;

                    JSONArray arr = new JSONArray(response.body().string());
                    List<LocationSuggestion> newList = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        newList.add(new LocationSuggestion(
                                obj.optString("display_name", getString(R.string.unknown_location_api)),
                                obj.getDouble("lat"),
                                obj.getDouble("lon")));
                    }

                    mainHandler.post(() -> {
                        if (!newList.isEmpty()) suggestionAdapter.updateData(newList);
                        else rvSuggestions.setVisibility(View.GONE);
                    });
                }

            } catch (Exception e) {
                mainHandler.post(() -> rvSuggestions.setVisibility(View.GONE));
            }
        }).start();
    }

    private void fetchLocationName(GeoPoint point) {
        tvLocationName.setText(R.string.loading_location);

        new Thread(() -> {
            String name = getString(R.string.unknown_location);
            try {
                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%f&lon=%f&accept-language=%s",
                        point.getLatitude(), point.getLongitude(), getLanguage());

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "AlphaApp/1.0 (alpha@example.com)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        JSONObject obj = new JSONObject(body);
                        if (obj.has("display_name")) {
                            name = obj.getString("display_name");
                        }
                    }
                }

            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to fetch location name", e);
                name = getString(R.string.failed_load_location);
            }

            final String finalName = name;
            mainHandler.post(() -> tvLocationName.setText(finalName));
        }).start();
    }

    // === UTILITIES ===
    private String getLanguage() {
        String lang = UserSession.getInstance().getLanguage();
        return (lang == null || lang.isEmpty()) ? "en" : lang;
    }

    private void showToast(int res) {
        Toast.makeText(getContext(), res, Toast.LENGTH_SHORT).show();
    }
}