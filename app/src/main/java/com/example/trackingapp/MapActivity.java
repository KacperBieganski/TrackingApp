package com.example.trackingapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.osmdroid.config.Configuration;
import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.List;

public class MapActivity extends AppCompatActivity {
    private MapView mapView;
    private TextView routeInfoText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.map);
        Toolbar toolbar = findViewById(R.id.toolbar);
        routeInfoText = findViewById(R.id.route_info_text);
        mapView.setMultiTouchControls(true);

        String routeName = getIntent().getStringExtra("routeName");
        String duration = getIntent().getStringExtra("routeDuration");
        if (routeName != null) {
            String infoText = "Trasa: " + routeName;
            if (duration != null) {
                infoText += " | Czas: " + duration;
            }
            routeInfoText.setText(infoText);
            loadRoute(routeName);
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    @SuppressLint("UseCompatLoadingForDrawables")
    private void loadRoute(String routeName) {
        new Thread(() -> {
            List<GeoPoint> points = InfluxHelper.fetchRoutePoints(routeName); // stwórz klasę pomocniczą
            runOnUiThread(() -> {
                if (!points.isEmpty()) {
                    Polyline polyline = new Polyline();
                    polyline.setPoints(points);
                    mapView.getOverlays().add(polyline);

                    // Ustawienie niestandardowych markerów dla pierwszego i ostatniego punktu
                    for (int i = 0; i < points.size(); i++) {
                        GeoPoint point = points.get(i);
                        org.osmdroid.views.overlay.Marker marker = new org.osmdroid.views.overlay.Marker(mapView);
                        marker.setPosition(point);

                        // Ustawienia dla markera
                        marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
                        marker.setTitle("Punkt");

                        // Pierwszy punkt - marker startowy
                        if (i == 0) {
                            marker.setIcon(getResources().getDrawable(R.drawable.black_dot, null));
                        }
                        // Ostatni punkt - marker końcowy
                        else if (i == points.size() - 1) {
                            marker.setIcon(getResources().getDrawable(R.drawable.black_dot, null));
                        }
                        // Dla pozostałych punktów używamy standardowego markera
                        else {
                            //marker.setIcon(getResources().getDrawable(R.drawable.blue_dot, null));
                            marker.setVisible(false);
                        }

                        mapView.getOverlays().add(marker);
                    }

                    // Ustaw zoom i środek mapy na pierwszy punkt
                    mapView.getController().setZoom(17.0);
                    mapView.getController().setCenter(points.get(0));
                    mapView.invalidate();
                }
            });
        }).start();
    }

}
