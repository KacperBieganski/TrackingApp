package com.example.trackingapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** @noinspection ALL*/
public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private final List<Location> locationList = new ArrayList<>();
    private InfluxDBClient influxDBClient;
    private final String token = "xxx";
    private final String bucket = "TrackingApp";
    private final String org = "UMG";
    private String url = AppConstants.BASE_URL;
    private TextView timerText;
    private int seconds = 0;
    private boolean isTracking = false;
    private Handler handler = new Handler();
    private Runnable runnable;
    private Button startButton;
    private Button stopButton;
    private Button saveButton;
    private Button cancelButton;
    private Button showRoutesButton;
    private EditText inputName;
    private LinearLayout inputLayout;
    private ProgressBar saveProgress;
    private MapView mapView;
    private Location lastLocation = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Configuration.getInstance().setUserAgentValue("TrackingApp/1.0 (Android)");

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        showRoutesButton = findViewById(R.id.showRoutesButton);
        inputName = findViewById(R.id.inputName);
        inputLayout = findViewById(R.id.inputLayout);
        timerText = findViewById(R.id.timerText);
        saveProgress = findViewById(R.id.saveProgress);
        mapView = findViewById(R.id.map);
        mapView.getController().setZoom(3);
        stopButton.setVisibility(View.GONE);
        inputLayout.setVisibility(View.GONE);
        mapView.setMultiTouchControls(true);

        ActivityResultLauncher<String[]> locationPermissionRequest =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    // Handle permission request result if needed
                });


        startButton.setOnClickListener(v -> {
            locationPermissionRequest.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            startLocationUpdates();
            startTimer();
            locationList.clear();
            timerText.setVisibility(View.VISIBLE);
            mapView.setVisibility(View.VISIBLE);
            startButton.setVisibility(View.GONE);
            showRoutesButton.setVisibility(View.GONE);
            stopButton.setVisibility(View.VISIBLE);
        });

        stopButton.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Zakończyć nagrywanie?")
                    .setMessage("Czy na pewno chcesz zakończyć nagrywanie trasy?")
                    .setCancelable(false) // Użyj false, aby użytkownik nie mógł zamknąć dialogu bez wyboru
                    .setPositiveButton("Tak", (dialog, which) -> {
                        // Jeśli użytkownik wybierze 'Tak', zakończ nagrywanie
                        stopLocationUpdates();
                        stopTimer();
                        stopButton.setVisibility(View.GONE);
                        inputLayout.setVisibility(View.VISIBLE);
                    })
                    .setNegativeButton("Nie", null) // Jeśli użytkownik wybierze 'Nie', nie rób nic
                    .show();
        });


        saveButton.setOnClickListener(v -> {
            String name = inputName.getText().toString();
            if (!name.isEmpty()) {
                saveButton.setEnabled(false);
                saveProgress.setVisibility(View.VISIBLE);
                mapView.setVisibility(View.GONE);
                saveDataToInflux(name);
            } else {
                Toast.makeText(MainActivity.this, "Podaj nazwę!", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> {
            inputLayout.setVisibility(View.GONE);
            startButton.setVisibility(View.VISIBLE);
            showRoutesButton.setVisibility(View.VISIBLE);
            timerText.setVisibility(View.GONE);
            mapView.setVisibility(View.GONE);
            locationList.clear();
        });

        showRoutesButton.setOnClickListener(v -> showSavedRoutes());

    }
    @Override
    protected void onDestroy() {
        if (influxDBClient != null) {
            influxDBClient.close();
        }
        super.onDestroy();
    }
    private void initializeInfluxDB() {
        new Thread(() -> {
            try {
                InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                        .url(url)
                        .authenticateToken(token.toCharArray())
                        .build();

                influxDBClient = InfluxDBClientFactory.create(options);

                // Test połączenia
                boolean connected = influxDBClient.ping();
                if (connected) {
                    Log.d("InfluxDB", "Połączenie udane");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Połączono z bazą danych", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e("InfluxDB", "Błąd połączenia");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Błąd połączenia z bazą danych", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e("InfluxDB", "Błąd inicjalizacji: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Błąd inicjalizacji bazy danych: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(5000)  // Set interval in milliseconds
                .setMinUpdateIntervalMillis(2000)  // Set fastest interval in milliseconds
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)  // Set the location priority
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                // Dodaj punkty do listy locationList tylko, jeśli odległość jest większa lub równa 10m
                for (Location location : locationResult.getLocations()) {
                    if (lastLocation == null || location.distanceTo(lastLocation) >= 5) {
                        locationList.add(location);  // Dodaj lokalizację do listy, jeśli jest wystarczająco daleko od poprzedniej

                        // Ustaw nową lokalizację jako ostatnią
                        lastLocation = location;

                        // Aktualizuj mapę z nowymi punktami
                        runOnUiThread(() -> {
                            if (!locationList.isEmpty()) {
                                Location latestLocation = locationList.get(locationList.size() - 1);

                                // Rysuj punkt na mapie (używając GeoPoint z OpenStreetMap)
                                GeoPoint geoPoint = new GeoPoint(latestLocation.getLatitude(), latestLocation.getLongitude());

                                if (locationList.size() == 1) {
                                    mapView.getController().setCenter(geoPoint);
                                    mapView.getController().setZoom(20); // Ustaw poziom powiększenia
                                }

                                Marker marker = new Marker(mapView);
                                marker.setPosition(geoPoint);
                                //marker.setIcon(getResources().getDrawable(R.drawable.blue_dot, null));
                                marker.setVisible(false);
                                mapView.getOverlays().add(marker);

                                // Rysowanie linii łączącej punkty
                                List<GeoPoint> geoPoints = new ArrayList<>();
                                for (Location loc : locationList) {
                                    geoPoints.add(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
                                }
                                Polyline polyline = new Polyline();
                                polyline.setPoints(geoPoints);
                                mapView.getOverlays().add(polyline);

                                // Ustaw widok na ostatni punkt
                                mapView.getController().setCenter(geoPoint);
                                mapView.getController().setZoom(19); // Ustaw poziom powiększenia
                            }
                        });
                    }
                }
            }
        };

        // Sprawdź uprawnienia lokalizacji
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }
    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
    private void saveDataToInflux(String name) {
        new Thread(() -> {
            try {
                InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                        .url(url)
                        .authenticateToken(token.toCharArray())
                        .build();

                influxDBClient = InfluxDBClientFactory.create(options);

                // Sprawdzenie połączenia
                if (!influxDBClient.ping()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Błąd połączenia z bazą danych. Sprawdź połączenie sieciowe.", Toast.LENGTH_LONG).show();
                        saveProgress.setVisibility(View.GONE);
                        saveButton.setEnabled(true);
                        influxDBClient.close();
                        influxDBClient = null;
                    });
                    return;
                }

                WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

                List<Point> points = new ArrayList<>();

                synchronized (locationList) {
                    for (Location location : locationList) {

                        if (location != null) {
                            Point point = createPoint(name, location);
                            if (point != null) {
                                points.add(point);
                            }
                        }
                    }
                    locationList.clear();
                }

                if (!points.isEmpty()) {
                    writeApi.writePoints(bucket, org, points);
                }

                Log.d("DatabaseSuccess", "Zapisano " + points.size() + " punktów.");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Zapisano " + points.size() + " punktów!", Toast.LENGTH_SHORT).show();
                    saveProgress.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    inputLayout.setVisibility(View.GONE);
                    startButton.setVisibility(View.VISIBLE);
                    showRoutesButton.setVisibility(View.VISIBLE);
                    timerText.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e("DatabaseError", "Błąd zapisu trasy: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Błąd zapisu: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (influxDBClient != null) {
                    influxDBClient.close();
                }
            }
        }).start();
    }
    private Point createPoint(String name, Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Point.measurement("location")
                    .addTag("name", name)
                    .addField("latitude", location.getLatitude())
                    .addField("longitude", location.getLongitude())
                    .addField("accuracy", location.getAccuracy())
                    .time(Instant.now(), WritePrecision.NS);
        }
        return null; // In case device is not on a compatible version
    }
    private void startTimer() {
        isTracking = true;
        seconds = 0;
        timerText.setVisibility(View.VISIBLE);

        runnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    seconds++;

                    // Oblicz minuty i sekundy
                    int minutes = seconds / 60;
                    int secs = seconds % 60;

                    // Sformatuj czas w "MM:SS"
                    String timeFormatted = String.format("%02d:%02d", minutes, secs);
                    timerText.setText("Czas: " + timeFormatted);

                    handler.postDelayed(this, 1000); // Aktualizacja co 1 sekundę
                }
            }
        };
        handler.post(runnable);
    }
    private void stopTimer() {
        isTracking = false;
        handler.removeCallbacks(runnable);
    }
    private void showSavedRoutes() {
        Intent intent = new Intent(MainActivity.this, RoutesActivity.class);
        startActivity(intent);
    }


}

