package com.example.trackingapp;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import com.example.trackingapp.AppConstants;

public class InfluxHelper {

    private static final String token = "xxx";

    public static List<GeoPoint> fetchRoutePoints(String routeName) {
        List<GeoPoint> points = new ArrayList<>();

        // Połącz się z bazą danych (możesz przekazać klienta z RoutesActivity lub utworzyć tu nowego)
        String url = AppConstants.BASE_URL;
        InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray());

        String query = String.format("""
            from(bucket: "TrackingApp")
              |> range(start: 0)
              |> filter(fn: (r) => r._measurement == "location" and r.name == "%s")
              |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> sort(columns: ["_time"])
              |> keep(columns: ["latitude", "longitude"])
            """, routeName);


        client.getQueryApi().query(query, "UMG").forEach(table -> {
            table.getRecords().forEach(record -> {
                Double lat = (Double) record.getValueByKey("latitude");
                Double lon = (Double) record.getValueByKey("longitude");
                if (lat != null && lon != null) {
                    points.add(new GeoPoint(lat, lon));
                }
            });
        });

        client.close();
        return points;
    }
}

