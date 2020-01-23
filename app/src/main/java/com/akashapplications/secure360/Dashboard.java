package com.akashapplications.secure360;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.akashapplications.secure360.services.CrimeZoneService;
import com.akashapplications.secure360.services.LocationWatcher;
import com.akashapplications.secure360.utilities.Constants;
import com.akashapplications.secure360.utilities.Util;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.tedpark.tedpermission.rx2.TedRx2Permission;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Dashboard extends AppCompatActivity {

    ImageView parkingSwitch, sosSwitch, intruderSwitch, crimeZoneSwitch;
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference PARKING_REF = database.getReference("parking");
    MediaPlayer siren;
    static boolean parkingService = false, sosService = false, intruderService = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);
        Util.makeStatusBarTextDark(getWindow());

        parkingSwitch = findViewById(R.id.parkingSwitch);
        sosSwitch = findViewById(R.id.sosSwitch);
        intruderSwitch = findViewById(R.id.intruderDetectionSwitch);
        crimeZoneSwitch = findViewById(R.id.crimeZoneSwitch);

        siren = MediaPlayer.create(this, R.raw.siren);
        siren.setLooping(true);
        findViewById(R.id.parking).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                PARKING_REF.child("status").child("stolen").setValue("no");
                siren.start();
                if (!parkingService) {
                    parkingSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.on), android.graphics.PorterDuff.Mode.SRC_IN);
                    parkingService = true;
                    updateParkingLocation();

                    PARKING_REF.child("status").addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            Log.e("check", "status: " + String.valueOf(dataSnapshot.child("stolen").getValue()));
                            if ("yes".equalsIgnoreCase(String.valueOf(dataSnapshot.child("stolen").getValue()))) {

                                if (!siren.isPlaying() && parkingService)
                                    siren.start();
                            } else {
                                siren.pause();
                            }

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                } else {
                    parkingSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.off), android.graphics.PorterDuff.Mode.SRC_IN);
                    parkingService = false;
                    if (siren.isPlaying())
                        siren.pause();
                }
            }
        });

        findViewById(R.id.sos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                TedRx2Permission.with(getBaseContext())
                        .setRationaleTitle("Can we access your phone call?")
                        .setRationaleMessage("We need your permission to make automatic phone call")
                        .setPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.RECORD_AUDIO)
                        .request()
                        .subscribe(permissionResult -> {
                                    if (permissionResult.isGranted()) {

                                    } else {
                                        Toast.makeText(getBaseContext(),
                                                "Permission Denied\n" + permissionResult.getDeniedPermissions().toString(), Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                }, throwable -> {
                                }
                        );

                if (!sosService) {
                    sosSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.on), android.graphics.PorterDuff.Mode.SRC_IN);
                    sosService = true;
                } else {
                    sosSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.off), android.graphics.PorterDuff.Mode.SRC_IN);
                    sosService = false;
                }
            }
        });

        findViewById(R.id.findPhone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                triggerFindMyPhone();
            }
        });

        findViewById(R.id.findBuddy).setOnClickListener(new View.OnClickListener() {
            @SuppressLint("CheckResult")
            @Override
            public void onClick(View view) {
                TedRx2Permission.with(getBaseContext())
                        .setRationaleTitle("Can we read your Location?")
                        .setRationaleMessage("We need your permission to access your current location")
                        .setPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                        .request()
                        .subscribe(permissionResult -> {
                                    if (permissionResult.isGranted()) {
                                        triggerFindMyBuddy();

                                    } else {
                                        Toast.makeText(getBaseContext(),
                                                "Permission Denied\n" + permissionResult.getDeniedPermissions().toString(), Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                }, throwable -> {
                            }
                        );
            }
        });


        findViewById(R.id.intruderDetection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!intruderService) {
                    intruderSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.on), android.graphics.PorterDuff.Mode.SRC_IN);
                    intruderService = true;

                    database.getReference("intruder").addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if(dataSnapshot.child("status").getValue().toString().equalsIgnoreCase("yes"))
                            {
                                if (!siren.isPlaying() && intruderService)
                                    siren.start();
                            }
                            else
                            {
                                if (siren.isPlaying())
                                    siren.pause();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });


                } else {
                    intruderSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.off), android.graphics.PorterDuff.Mode.SRC_IN);
                    intruderService = false;
                    if (siren.isPlaying())
                        siren.pause();
                }
            }
        });

        if(isServiceRunning(CrimeZoneService.class))
            crimeZoneSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.on), android.graphics.PorterDuff.Mode.SRC_IN);
        findViewById(R.id.crimeZone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!isServiceRunning(CrimeZoneService.class)){
                    crimeZoneSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.on), android.graphics.PorterDuff.Mode.SRC_IN);
                    PARKING_REF.child("parkedLocation").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                            double parkedLat = (double) dataSnapshot.child("latitude").getValue();
                            double parkedLon = (double) dataSnapshot.child("longitude").getValue();
                            double safeDistance = (double) dataSnapshot.child("safeDistance").getValue();
                            startService(new Intent(getBaseContext(), CrimeZoneService.class).putExtra("lat",parkedLat).putExtra("lon",parkedLon).putExtra("safeDistance",safeDistance));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });

                }
                else
                {
                    crimeZoneSwitch.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.off), android.graphics.PorterDuff.Mode.SRC_IN);
                    stopService(new Intent(getBaseContext(), CrimeZoneService.class));
                }
            }
        });

    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void triggerFindMyBuddy() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        final EditText edittext = new EditText(this);
        alert.setMessage("Enter Your Buddy name");
        edittext.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        alert.setView(edittext);

        alert.setPositiveButton("Find", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                String name = edittext.getText().toString().replace(" ","").toLowerCase();
                if (name.length() > 0) {
                    database.getReference("findMyBuddy").child(name).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                            if (dataSnapshot.child("latitude").getValue() == null || dataSnapshot.child("longitude").getValue() == null) {
                                Toast.makeText(getBaseContext(), "Could not find your buddy", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            startActivity(new Intent(getBaseContext(), FindMyBuddy.class).putExtra("name",name));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }
        });

        alert.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    private void triggerFindMyPhone() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        final EditText edittext = new EditText(this);
        alert.setMessage("Enter Your Phone number");
        edittext.setInputType(InputType.TYPE_CLASS_PHONE);
        alert.setView(edittext);

        alert.setPositiveButton("Navigate", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                String phone = edittext.getText().toString();
                if (phone.length() > 0) {
                    database.getReference("findMyPhone").child(phone).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                            if (dataSnapshot.child("latitude").getValue() == null || dataSnapshot.child("longitude").getValue() == null) {
                                Toast.makeText(getBaseContext(), "Could not find this device", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            double lat = (double) dataSnapshot.child("latitude").getValue();
                            double lon = (double) dataSnapshot.child("longitude").getValue();
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                                    Uri.parse("geo:0,0?q=" + lat + "," + lon + " (My phone)"));
                            startActivity(intent);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }
        });

        alert.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    @SuppressLint("CheckResult")
    private void updateParkingLocation() {
        TedRx2Permission.with(getBaseContext())
                .setRationaleTitle("Can we read your Location?")
                .setRationaleMessage("We need your permission to access your current location")
                .setPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                .request()
                .subscribe(permissionResult -> {
                            if (permissionResult.isGranted()) {
                                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                                @SuppressLint("MissingPermission") Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);


                                Map<String, Object> locationMap = new HashMap<>();
                                locationMap.put("longitude", location.getLongitude());
                                locationMap.put("latitude", location.getLatitude());
                                Log.i("check", locationMap.toString());
                                Toast.makeText(getBaseContext(), "Location Updated", Toast.LENGTH_SHORT).show();
                                PARKING_REF.child("parkedLocation").updateChildren(locationMap);

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
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (siren != null) {
            siren.stop();
        }
    }

    static int i = 0;

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {

            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                i++;
                if (i > 2) {
                    i = 0;
                    if (sosService) {
                        Toast.makeText(this, "Start recording", Toast.LENGTH_SHORT).show();
                        triggerRecording();
                    }
                }
                return true;

        }
        return super.onKeyDown(keyCode, event);
    }

    private void triggerRecording() {
        File f = null;
        try {
            String path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES).getAbsolutePath();
            f = new File(path, "/rec_" + System.currentTimeMillis() + ".mp3");
        } catch (Exception e) {
            Log.e("check", e.getMessage());
        }
        Log.e("check", "recording starts : " + f.getAbsolutePath().toString());
        MediaRecorder recorder = null;
        if (recorder != null)
            recorder.release();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setMaxDuration(Constants.RECORDING_DURATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            recorder.setOutputFile(f.getAbsoluteFile());

            MediaRecorder finalRecorder = recorder;
            File finalF = f;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        finalRecorder.prepare();
                        finalRecorder.start();
                        Util.shortVibrate(getBaseContext());
                        SystemClock.sleep(Constants.RECORDING_DURATION + 1000);

                        Log.e("check", "Uploading starts : " + finalF.getAbsolutePath().toString());
                        uploadAudioClip(Uri.fromFile(finalF), finalF.getName());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }).start();
        }

    }

    private void uploadAudioClip(Uri uri, String name) {
        StorageReference mStorageRef = FirebaseStorage.getInstance().getReference();
        StorageReference audioRef = mStorageRef.child("audio/" + name);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("audio/mpeg")
                .build();
        audioRef.putFile(uri, metadata)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                        audioRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @SuppressLint("MissingPermission")
                            @Override
                            public void onSuccess(Uri uri) {
                                Log.e("check", "Upload complete: " + uri);
                                Util.shortVibrate(getBaseContext());
                                Intent intent = new Intent(Intent.ACTION_CALL);
                                intent.setData(Uri.parse("tel:7772000567"));
                                startActivity(intent);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Toast.makeText(getBaseContext(), "Uploading failed : " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

}
