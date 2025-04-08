package com.example.trackingapp;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.influxdb.client.DeleteApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class RoutesActivity extends AppCompatActivity {
    private ListView routesListView;
    private InfluxDBClient influxDBClient;
    private final String bucket = "TrackingApp";
    private final String org = "UMG";
    private final List<Map<String, String>> routesList = new ArrayList<>();
    private SimpleAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routes);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        routesListView = findViewById(R.id.routesListView);
        initializeInfluxDB();
        fetchRoutes();
    }
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    private void initializeInfluxDB() {
        String url = AppConstants.BASE_URL;
        String token = "xxx";
        influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray());
    }
    private void fetchRoutes() {
        new Thread(() -> {
            try {
                String query = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    query = """
                        from(bucket: "%s")
                          |> range(start: 0)
                          |> filter(fn: (r) => r._measurement == "location")
                          |> keep(columns: ["name", "_time"])
                          |> unique(column: "name")
                          |> sort(columns: ["_time"], desc: true)
                        """.formatted(bucket);
                }

                routesList.clear();

                assert query != null;
                influxDBClient.getQueryApi().query(query, org).forEach(table -> table.getRecords().forEach(record -> {
                    String name = Objects.requireNonNull(record.getValueByKey("name")).toString();
                    String date = Objects.requireNonNull(record.getTime()).toString().substring(0, 10);

                    Map<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("date", date);
                    routesList.add(map);
                }));

                runOnUiThread(() -> {
                    adapter = new SimpleAdapter(
                            RoutesActivity.this,
                            routesList,
                            R.layout.route_list_item,
                            new String[]{"name", "date"},
                            new int[]{R.id.routeName, R.id.routeDate}
                    ) {
                        @Override
                        public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView deleteButton = view.findViewById(R.id.deleteRoute);

                            view.setOnClickListener(v -> {
                                String selectedRoute = routesList.get(position).get("name");
                                Intent intent = new Intent(RoutesActivity.this, MapActivity.class);
                                intent.putExtra("routeName", selectedRoute);
                                startActivity(intent);
                            });


                            deleteButton.setOnClickListener(v -> {
                                String selectedRoute = routesList.get(position).get("name");
                                showDeleteConfirmationDialog(selectedRoute, position);
                            });

                            return view;
                        }
                    };

                    routesListView.setAdapter(adapter);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    private void showDeleteConfirmationDialog(String routeName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Usuń trasę")
                .setMessage("Czy na pewno chcesz usunąć trasę: " + routeName + "?")
                .setPositiveButton("Usuń", (dialog, which) -> deleteRoute(routeName, position))
                .setNegativeButton("Anuluj", null)
                .show();
    }
    private void deleteRoute(String routeName, int position) {
        new Thread(() -> {
            try {
                DeleteApi deleteApi = influxDBClient.getDeleteApi();

                OffsetDateTime start = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    start = OffsetDateTime.parse("1970-01-01T00:00:00Z");
                }
                OffsetDateTime stop = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stop = OffsetDateTime.now(ZoneOffset.UTC);
                }

                String predicate = String.format("_measurement = \"location\" AND \"name\" = \"%s\"", routeName);

                assert stop != null;
                deleteApi.delete(start, stop, predicate, bucket, org);


                runOnUiThread(() -> {
                    routesList.remove(position);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Trasa usunięta!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("DatabaseError", "Błąd usuwania trasy: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Błąd usuwania trasy", Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    @Override
    protected void onDestroy() {
        if (influxDBClient != null) {
            influxDBClient.close();
        }
        super.onDestroy();
    }
}
