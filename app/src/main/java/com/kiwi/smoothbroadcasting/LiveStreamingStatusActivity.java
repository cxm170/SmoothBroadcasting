package com.kiwi.smoothbroadcasting;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;

public class LiveStreamingStatusActivity extends AppCompatActivity {

    private int liveStreamingTime, liveStreamingInterval, bandwidthPredictionWindow;

    private static final int REQUEST_WRITE = 0;

    private Button button;

    private Button button2;

    public static TextView textView;

    public static TextView textView2;

    private int methodIndex;

    private static final int RESULT_SETTINGS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.textView);

        textView2 = (TextView) findViewById(R.id.textView2);


        button = (Button) findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                //read settings
                SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                liveStreamingTime = Integer.parseInt(SP.getString("live_streaming_duration", "60"));
                liveStreamingInterval = Integer.parseInt(SP.getString("live_streaming_interval", "5"));
                bandwidthPredictionWindow = Integer.parseInt(SP.getString("prediction_window", "5"));
                Statistics.unitVideoDelay = Double.parseDouble(SP.getString("unit_video_delay", "2"));
                Statistics.averageDelay = Double.parseDouble(SP.getString("average_delay", "0.5"));
                Statistics.unitVideoDuration = liveStreamingInterval;
                Statistics.initDelay = Double.parseDouble(SP.getString("init_delay", "30"));

                Statistics.maxNumberOfVideos = (int) Math.ceil(liveStreamingTime / liveStreamingInterval);

                methodIndex = Integer.parseInt(SP.getString("method_index", "1"));


                // Here, thisActivity is the current activity
                if (ContextCompat.checkSelfPermission(LiveStreamingStatusActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

                    // Should we show an explanation?
                    if (ActivityCompat.shouldShowRequestPermissionRationale(LiveStreamingStatusActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                        // Show an expanation to the user *asynchronously* -- don't block
                        // this thread waiting for the user's response! After the user
                        // sees the explanation, try again to request the permission.

                    } else {

                        // No explanation needed, we can request the permission.

                        ActivityCompat.requestPermissions(LiveStreamingStatusActivity.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_WRITE);

                        // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                        // app-defined int constant. The callback method gets the
                        // result of the request.
                    }
                } else {


                    ConnectivityManager connMgr = (ConnectivityManager)
                            getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected()) {
                        new GetTheStartTimeOfLiveStreaming().execute("http://158.132.10.194:25001/livestreaming/start.php");
                    } else {
                        Toast.makeText(getApplicationContext(), "No network connections", Toast.LENGTH_SHORT).show();
                    }
                }


            }

        });

        button2 = (Button) findViewById(R.id.button2);

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (Statistics.delayTimes != null) {
                    double averageDelayTime = 0;
                    for (int i = 0; i < Statistics.delayTimes.size(); i++) {
                        averageDelayTime += Statistics.delayTimes.get(i);
                    }
                    averageDelayTime = averageDelayTime / Statistics.delayTimes.size();

                    double averageResolution = 0;
                    for (int i = 0; i < Statistics.resolutions.size(); i++) {
                        averageResolution += Statistics.resolutions.get(i);
                    }
                    averageResolution = averageResolution / Statistics.resolutions.size();

                    double averageThroughput = 0;
                    for (int i = 0; i < Statistics.throughputs.size(); i++) {
                        averageThroughput += Statistics.throughputs.get(i);
                    }

                    for (int i = 0; i < Statistics.throughputChanges.size(); i++) {
                        averageThroughput -= Statistics.throughputChanges.get(i);
                    }

                    averageThroughput = averageThroughput / Statistics.throughputs.size();

                    double averageBandwidth = 0;
                    for (int i = 0; i < Statistics.bandwidthsEveryInterval.size(); i++) {
                        averageBandwidth += Statistics.bandwidthsEveryInterval.get(i);
                    }
                    averageBandwidth = averageBandwidth / Statistics.bandwidthsEveryInterval.size();

                    textView.setText("Method: " + methodIndex + "\n");
                    textView.append("Unit video delay: " + Statistics.unitVideoDelay + "\n");
                    textView.append("Average delay: " + Statistics.averageDelay + "\n");
                    textView.append("Init delay: " + Statistics.initDelay + "\n");


                    textView.append("Average delay time: " + averageDelayTime + "\n");
                    textView.append("Average resolution: " + averageResolution + "\n");
                    textView.append("Average throughput: " + averageThroughput + "\n");
                    textView.append("Average bandwidth: " + averageBandwidth + "\n");
                    textView.append("Decision times: " + Statistics.throughputs.size() + "\n");
                    textView.append("Preprocessing times:" + Statistics.throughputChanges.size());

                } else
                    textView.setText("No results yet");

            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_settings:
                Intent i = new Intent(this, LiveStreamingSetting.class);
                startActivityForResult(i, RESULT_SETTINGS);
                break;

        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SETTINGS:
//                showUserSettings();
                break;

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }


    private class GetTheStartTimeOfLiveStreaming extends AsyncTask<String, Void, Double> {
        @Override
        protected Double doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return 0.0;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(Double startingTime) {
            LiveStreamingService.startLiveStreamingService(LiveStreamingStatusActivity.this, liveStreamingTime, liveStreamingInterval, startingTime, bandwidthPredictionWindow);
        }
    }

    private double downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 13;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d("FirstConnect", "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is, len);
            double time = Double.parseDouble(contentAsString);
            Log.e("error", "the starting time of live streaming: " + time);
            return time;

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream, int len) throws IOException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }


}
