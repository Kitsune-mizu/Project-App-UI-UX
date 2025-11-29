package com.android.alpha.ui.map;

public class LocationSuggestion {

    // Instance Variables
    public String displayName;
    public double lat;
    public double lon;
    public String mainText;
    public String secondaryText;

    // Constructor
    public LocationSuggestion(String displayName, double lat, double lon) {
        this.displayName = displayName;
        this.lat = lat;
        this.lon = lon;

        parseDisplayName(displayName);
    }

    // Helper Method to Parse Display Name
    private void parseDisplayName(String displayName) {
        String[] parts = displayName.split(",", 2);
        mainText = parts[0].trim();
        secondaryText = (parts.length > 1) ? parts[1].trim() : "";
    }
}
