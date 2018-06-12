package com.example.elibosley.flowlighttestproject;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.android.things.contrib.driver.bmx280.Bmx280;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.device.TimeManager;
import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.sql.Time;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity implements View.OnClickListener {

    Gpio led;
    Bmx280 sensor;
    AlphanumericDisplay segment;
    Button saveButton, startTimeButton, endTimeButton;
    TextView setStartTime, setEndTime;
    LocalTime startTime, endTime; // these are the actual times that have been set
    SharedPreferences sharedPreferences;
    final static IntentFilter s_intentFilter;
    final String appName = "com.example.elibosley.flowlighttestproject";
    static {
        s_intentFilter = new IntentFilter();
        s_intentFilter.addAction(Intent.ACTION_TIME_TICK);
        s_intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        s_intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);
        // Don't allow the keyboard to show when the main screen starts
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        /* Set up time manager to set the time zone */
        TimeManager timeManager = TimeManager.getInstance();
        // Use 24-hour time
        timeManager.setTimeFormat(TimeManager.FORMAT_12);
        // Set time zone to Eastern Standard Time
        timeManager.setTimeZone("America/New_York");

        startTimeButton = findViewById(R.id.flowStartTime);
        endTimeButton = findViewById(R.id.flowEndTime);
        startTimeButton.setOnClickListener(this);
        endTimeButton.setOnClickListener(this);
        setStartTime = findViewById(R.id.setStartTime);
        setEndTime = findViewById(R.id.setEndTime);
        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(this);

        try {
            led = RainbowHat.openLedRed();
            led.setValue(true);

            // Close the device when done.


            // Display the temperature on the segment display.
            segment = RainbowHat.openDisplay();
            segment.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            segment.setEnabled(true);
            segment.clear();

        } catch (IOException e) {
            Log.e("RainbowHat", "onCreate: ", e);
        }

        registerReceiver(m_timeChangedReceiver, MainActivity.s_intentFilter);
        sharedPreferences = this.getSharedPreferences(appName, Context.MODE_PRIVATE);

    }

    @Override
    protected void onDestroy() {
        // Close the devices when done.
        try {
            sensor.close();
            segment.close();
            led.close();
            unregisterReceiver(m_timeChangedReceiver);
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private void loadSavedTime() {
            String tempStartTime = sharedPreferences.getString("startTime", "Start Time");
            String tempEndTime = sharedPreferences.getString("endTime", "End Time");
            startTimeButton.setText(tempStartTime);
            endTimeButton.setText(tempEndTime);
    }

    private void saveFlowTime() {
        if (startTimeButton.getText() != null && endTimeButton.getText() != null) {
            try {
                startTime = LocalTime.parse(startTimeButton.getText());
                endTime = LocalTime.parse(endTimeButton.getText());
                setStartTime.setText(startTime.toString());
                setEndTime.setText(endTime.toString());

                sharedPreferences.edit().putString("startTime", startTimeButton.getText().toString());
                sharedPreferences.edit().putString("endTime", endTimeButton.getText().toString());
                checkTimes();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void launchDatePicker(final Button v) {
        TimePickerDialog.OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                v.setText(String.format("%02d", hourOfDay) + ":" + String.format("%02d", minute) + ":00");
            }
        };
        TimePickerDialog dialog = new TimePickerDialog(v.getContext(), timeSetListener, 0, 0, false);
        dialog.show();
    }

    private final BroadcastReceiver m_timeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_CHANGED) ||
                    action.equals(Intent.ACTION_TIMEZONE_CHANGED) ||
                    action.equals(Intent.ACTION_TIME_TICK)) {
                checkTimes();
            }
        }
    };


    private void checkTimes() {
        LocalTime t = LocalTime.now();
        if (t.isAfter(startTime) && t.isBefore(endTime)) {
            try {
                segment.display("FLOW");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                segment.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.saveButton:
                saveFlowTime();
                break;
            case R.id.flowStartTime:
                launchDatePicker(startTimeButton);
                break;

            case R.id.flowEndTime:
                launchDatePicker(endTimeButton);
                break;
        }
    }
}
