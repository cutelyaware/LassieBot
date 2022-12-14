package com.superliminal.android.lassiebot;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.superliminal.android.lassiebot.IntSpinner.IntSpinnerListener;
import com.superliminal.util.android.EmailUtils;

import java.util.HashSet;
import java.util.Set;

public class LassieBotActivity extends Activity {
    private SharedPreferences mPrefs; // Seems to be important to not instantiate here.
    private Intent mServiceIntent;
    private final int SEND_SMS_REQUEST_CODE = 1;
    private final int READ_CONTACTS_REQUEST_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mServiceIntent = new Intent(LassieBotActivity.this, LassieBotService.class);
        mPrefs = getSharedPreferences(LassieBotService.PREFS_NAME, LassieBotService.PREFS_SHARE_MODE);
        setContentView(R.layout.lert);
        final IntSpinner intSpinner = (IntSpinner) findViewById(R.id.timeout_spinner);
        int timeout_hours = mPrefs.getInt(LassieBotService.PREFS_KEY_TIMEOUT_HOURS, LassieBotService.DEFAULT_TIMEOUT_HOURS);
        LassieBotService.CONFIGURE = mPrefs.getBoolean(LassieBotService.PREFS_KEY_CONFIGURE, false);
        intSpinner.setAll(1, 24, timeout_hours);
        intSpinner.addListener(new IntSpinnerListener() {
            @Override
            public void valueChanged(int new_val) {
                mPrefs.edit().putInt(LassieBotService.PREFS_KEY_TIMEOUT_HOURS, new_val).apply();
            }
        });
        // Initialize configuration controls.
        final CheckBox configureCheckBox = (CheckBox) findViewById(R.id.calibrate);
        configureCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LassieBotService.CONFIGURE = isChecked;
                mPrefs.edit().putBoolean(LassieBotService.PREFS_KEY_CONFIGURE, isChecked).apply();
                updateControls();
            }
        });

        final ToggleButton toggle = ((ToggleButton) findViewById(R.id.toggle));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean toggleChecked) {
                if(toggleChecked)
                    acquireSmsPermission();
                else
                    stopService(mServiceIntent);
                updateControls();
            }
        });
        // Setting a dummy click listener enables default button click sound. Go figure.
        toggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
        // Listen for service running state changes. Most of the time the changes come from
        // this Activity when the user toggles the start/stop button, but the service also
        // updates it when the alarm goes off and it stops itself.
        OnSharedPreferenceChangeListener runningListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if(!LassieBotService.PREFS_KEY_RUNNING.equals(key))
                    return;
                toggle.setChecked(prefs.getBoolean(key, false));
            }
        };
        mPrefs.registerOnSharedPreferenceChangeListener(runningListener);
        // Initialize service.
        updateControls();
        // Note: The pref_running value can be out of sync with sys_running because the
        // service may have been killed without its onDestroy method being called.
        // In general use, that would be a terrible thing but it does happen
        // during testing when restarting the app in Eclipse.
        // Log the error and restart service to get back in sync.
        if(shouldBeRunning()) {
            Log.e(LassieBotService.TAG, "Shared pref out of sync with service state!");
            acquireSmsPermission();
        }

        // Refresh ICEs button.
        ((Button) findViewById(R.id.refresh)).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateControls();
            }
        });

        class SeekAdapter implements SeekBar.OnSeekBarChangeListener {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        }
//        LassieBotService.ACCELEROMETER_THRESHOLD = mPrefs.getFloat(LassieBotService.PREFS_KEY_ACCEL_THRESHOLD, (float) LassieBotService.ACCELEROMETER_THRESHOLD_DEFAULT);
//        final SeekBar accelThresh = (SeekBar) findViewById(R.id.accel_threshold);
//        accelThresh.setProgress((int) ((1 - LassieBotService.ACCELEROMETER_THRESHOLD) * 100));
//        accelThresh.setOnSeekBarChangeListener(new SeekAdapter() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if(!fromUser)
//                    return;
//                double frac = 1 - accelThresh.getProgress() / 100.0;
//                double new_thresh = LassieBotService.ACCELEROMETER_MAX * frac;
//                LassieBotService.ACCELEROMETER_THRESHOLD = new_thresh;mPrefs.edit().putString(LassieBotService.PREFS_KEY_ACCEL_THRESHOLD, "" + new_thresh).apply();
//                mPrefs.edit().putString(LassieBotService.PREFS_KEY_ACCEL_THRESHOLD, "" + new_thresh).apply();
//                Log.e(LassieBotService.TAG, "accel new threshold: " + new_thresh);
//            }
//        });

        LassieBotService.GYROSCOPE_THRESHOLD = mPrefs.getFloat(LassieBotService.PREFS_KEY_GYRO_THRESHOLD, (float) LassieBotService.GYROSCOPE_THRESHOLD_DEFAULT);
        final SeekBar gyroThresh = (SeekBar) findViewById(R.id.gyro_threshold);
        gyroThresh.setProgress((int) ((1 - LassieBotService.GYROSCOPE_THRESHOLD) * 100));
        gyroThresh.setOnSeekBarChangeListener(new SeekAdapter() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(!fromUser)
                    return;
                double frac = 1 - gyroThresh.getProgress() / 100.0;
                double new_thresh = LassieBotService.GYROSCOPE_MAX * frac;
                LassieBotService.GYROSCOPE_THRESHOLD = new_thresh;
                mPrefs.edit().putFloat(LassieBotService.PREFS_KEY_GYRO_THRESHOLD, (float) new_thresh).apply();
                Log.e(LassieBotService.TAG, "gyro new threshold: " + new_thresh);
            }
        });

        ((Button) findViewById(R.id.test)).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int last_test_num = mPrefs.getInt(LassieBotService.PREFS_KEY_TEST, 0);
                mPrefs.edit().putInt(LassieBotService.PREFS_KEY_TEST, last_test_num + 1).apply();
            }
        });

        final CheckBox disableCheckBox = (CheckBox) findViewById(R.id.disable_while_charging);
        disableCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.edit().putBoolean(LassieBotService.PREFS_KEY_DISABLE_WHILE_CHARGING, isChecked).apply();
                updateControls();
            }
        });
    } // end onCreate()

    private void acquireSmsPermission() {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) == PERMISSION_GRANTED) {
            startService(mServiceIntent);
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
            // Show reasoning here
        } else {
            requestPermissions(new String[]{ Manifest.permission.SEND_SMS }, SEND_SMS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == SEND_SMS_REQUEST_CODE) {
            if (grantResults.length == 1 && grantResults[0] == PERMISSION_GRANTED) {
                startService(mServiceIntent);
            }
        }
    }

    private void updateControls() {
        boolean mHaveICEs = updateContacts();
        final ToggleButton toggle = ((ToggleButton) findViewById(R.id.toggle));
        boolean running = mPrefs.getBoolean(LassieBotService.PREFS_KEY_RUNNING, false);
        if(running && !mHaveICEs) // Rare case but possible when user removes last ICE.
            stopService(mServiceIntent);
        toggle.setEnabled(mHaveICEs);
        ((TextView)findViewById(R.id.timeout_label)).setText("Timeout " + (LassieBotService.DEBUG ? "Minutes" : "Hours"));
        final CheckBox configureCheckBox = (CheckBox) findViewById(R.id.calibrate);
        boolean configuring = LassieBotService.CONFIGURE;
        configureCheckBox.setChecked(configuring);
        configureCheckBox.setText(configuring && running ? "Uncheck to silence" : "Configure & Test");
        final View configureControls = findViewById(R.id.test_controls);
        configureCheckBox.setEnabled(running);
        boolean configure_checkbox_checked = configureCheckBox.isChecked();
        configureControls.setVisibility((configure_checkbox_checked && running) ? View.VISIBLE : View.GONE);
        toggle.setChecked(running);
        final CheckBox chargingCheckBox = (CheckBox) findViewById(R.id.disable_while_charging);
        chargingCheckBox.setChecked(mPrefs.getBoolean(LassieBotService.PREFS_KEY_DISABLE_WHILE_CHARGING, LassieBotService.DEFAULT_DISABLE_WHILE_CHARGING));
    }

    private boolean shouldBeRunning() {
        boolean pref_running = mPrefs.getBoolean(LassieBotService.PREFS_KEY_RUNNING, false);
        boolean sys_running = isServiceRunning();
        return pref_running && !sys_running;
    }

    private boolean updateContacts() {
        final Set<String> unique_ices = new HashSet<String>();
        String text = "";
        Set<String> ices = getICEPhoneNumbers();
        mPrefs.edit().putStringSet(LassieBotService.PREFS_KEY_ICE_PHONES, ices).apply();
        for(String s : ices) {
            if(unique_ices.contains(s))
                continue;
            String display_contact = s.substring(LassieBotService.ICE_PREFIX.length());
            unique_ices.add(display_contact);
        }
        Object[] uices = unique_ices.toArray();
        boolean have_ices = unique_ices.size() > 0;
        for(int i = 0; i < uices.length; i++)
            text += uices[i] + (i < uices.length - 1 ? "\n" : "");
        if(!have_ices)
            text = "You must enable the Read Contacts permission and add at least one ICE contact to use " + getString(getApplicationInfo().labelRes);
        TextView contacts_text_view = ((TextView) findViewById(R.id.contacts));
        contacts_text_view.setText(text);
        contacts_text_view.setTextColor(have_ices ? getResources().getColor(R.color.ss_text_variables, this.getTheme()) : Color.RED);
        return have_ices;
    } // end updateContacts()

    private Set<String> getICEPhoneNumbers() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PERMISSION_GRANTED) {
            String phone_kind = ContactsContract.CommonDataKinds.Phone.DATA;
            Cursor contacts = EmailUtils.buildFilteredPhoneCursor(this, LassieBotService.ICE_PREFIX);
            int count = contacts.getCount();
            System.out.println("" + count + " ICE's");
            int nameIdx = contacts.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME);
            int phoneIdx = contacts.getColumnIndexOrThrow(phone_kind);
            Set<String> numbers = new HashSet<>();
            if(contacts.moveToFirst()) {
                do {
                    String name = contacts.getString(nameIdx);
                    String phone = contacts.getString(phoneIdx);
                    numbers.add(name + LassieBotService.NAME_PHONE_SEPARATOR + " " + phone);
                    Log.d(LassieBotService.TAG, phone);
                    System.out.println(phone);
                } while(contacts.moveToNext());
            }
            return numbers;
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            // Show reasoning here
        } else {
            requestPermissions(new String[]{ Manifest.permission.READ_CONTACTS }, READ_CONTACTS_REQUEST_CODE);
        }
        return new HashSet<>();
    }

    private Set<String> getICEAddresses() {
        String email_kind = ContactsContract.CommonDataKinds.Email.DATA;
        Cursor contacts = EmailUtils.buildFilteredEmailCursor(this, LassieBotService.ICE_PREFIX);
        int count = contacts.getCount();
        System.out.println("" + count + " ICE's");
        int nameIdx = contacts.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME);
        int emailIdx = contacts.getColumnIndexOrThrow(email_kind);
        Set<String> addresses = new HashSet<String>();
        if(contacts.moveToFirst()) {
            do {
                String name = contacts.getString(nameIdx);
                String email = contacts.getString(emailIdx);
                if(email.contains("@")) { // TODO: Figure how to add this condition to query.
                    addresses.add(email);
                    Log.d(LassieBotService.TAG, email);
                    System.out.println(email);
                }
            } while(contacts.moveToNext());
        }
        return addresses;
    }

    @SuppressWarnings("deprecation")
    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        for(RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(LassieBotService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
