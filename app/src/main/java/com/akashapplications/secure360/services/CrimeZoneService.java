package com.akashapplications.secure360.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.akashapplications.secure360.Dashboard;
import com.akashapplications.secure360.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.tedpark.tedpermission.rx2.TedRx2Permission;

public class CrimeZoneService extends Service {

    double distnaceTravelledInMeters = 0;
    double lat = 0;
    double lon = 0;
    double safeDistance = 0;
    LocationManager locationManager;
    LocationListener locationListener;
    MediaPlayer mp;


    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference reference = database.getReference("parking");


    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Bundle extras = intent.getExtras();
        if(extras != null){
            lat = extras.getDouble("lat");
            lon = extras.getDouble("lon");
            safeDistance = extras.getDouble("safeDistance");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @SuppressLint({"CheckResult", "MissingPermission"})
    @Override
    public void onCreate() {
        super.onCreate();

        mp = MediaPlayer.create(this, R.raw.siren);
        mp.setLooping(true);

        Toast.makeText(getBaseContext(),"Crime Zone service starts", Toast.LENGTH_SHORT).show();

        distnaceTravelledInMeters = 0.0;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.i("check", location.toString());

                distnaceTravelledInMeters =  distance(lat,lon, location.getLatitude(), location.getLongitude()) * 1000;;
               Toast.makeText(getBaseContext(),"lat : "+location.getLatitude()+"\nlon : "+location.getLongitude()+"\ndist : "+distnaceTravelledInMeters+"\nalert : "+(distnaceTravelledInMeters < safeDistance), Toast.LENGTH_SHORT).show();
               if(distnaceTravelledInMeters < safeDistance)
               {
                 if(!mp.isPlaying())
                     mp.start();
               }
               else {
                   if(mp.isPlaying())
                       mp.pause();
               }

            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);


        TedRx2Permission.with(getBaseContext())
                .setRationaleTitle("Can we read your Location?")
                .setRationaleMessage("We need your permission to access your current location")
                .setPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
                .request()
                .subscribe(permissionResult -> {
                            if (permissionResult.isGranted()) {
                                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, locationListener);
                            } else {
                                Toast.makeText(getBaseContext(),
                                        "Permission Denied\n" + permissionResult.getDeniedPermissions().toString(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }, throwable -> {
                        }
                );

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(getBaseContext(),"Crime zone service stopped", Toast.LENGTH_SHORT).show();
        locationManager.removeUpdates(locationListener);
        locationManager = null;
        if (mp != null) {
            mp.pause();
            mp.stop();
        }

    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

}
