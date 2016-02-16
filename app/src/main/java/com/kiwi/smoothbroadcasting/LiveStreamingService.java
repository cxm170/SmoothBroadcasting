package com.kiwi.smoothbroadcasting;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.LinkedList;

public class LiveStreamingService extends Service {

    public static final String EXTRA_LIVE_STREAMING_TIME = "com.kiwi.smoothbroadcasting.extra.livestreamingtime";
    public static final String EXTRA_DECISION_MAKING_INTERVAL = "com.kiwi.smoothbroadcasting.extra.decisionmakinginterval";
    public static final String EXTRA_lIVE_STREAMING_STARTING_TIME = "com.kiwi.smoothbroadcasting.extra.livestreamingstartingtime";
    public static final String EXTRA_BANDWIDTH_PREDICTION_WINDOW = "com.kiwi.smoothbroadcasting.extra.bandwidthpredictionwindow";

    public LiveStreamingService() {
    }




    // Helper method
    public static void startLiveStreamingService(Context context, int liveStreamingTime, int decisionMakingInterval, double startingTime, int bandwidthPredictionWindow) {
        Intent intent = new Intent(context, LiveStreamingService.class);
        intent.putExtra(EXTRA_LIVE_STREAMING_TIME, liveStreamingTime);
        intent.putExtra(EXTRA_DECISION_MAKING_INTERVAL, decisionMakingInterval);
        intent.putExtra(EXTRA_lIVE_STREAMING_STARTING_TIME, startingTime);
        intent.putExtra(EXTRA_BANDWIDTH_PREDICTION_WINDOW, bandwidthPredictionWindow);
        context.startService(intent);
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int liveStreamingTime = intent.getIntExtra(EXTRA_LIVE_STREAMING_TIME, 0);
        int decisionMakingInterval = intent.getIntExtra(EXTRA_DECISION_MAKING_INTERVAL, 5);
        double startingTime = intent.getDoubleExtra(EXTRA_lIVE_STREAMING_STARTING_TIME, 0.0);
        int bandwidthPredictionWindow = intent.getIntExtra(EXTRA_BANDWIDTH_PREDICTION_WINDOW, 10);

        TransmissionQueue.transmissionQueue = new LinkedList<>();
        Statistics.delayTimes = new LinkedList<>();

        Statistics.arrivalTimes = new LinkedList<>();
        Statistics.decisions = new LinkedList<>();
        Statistics.resolutions = new LinkedList<>();
        Statistics.throughputs = new LinkedList<>();
        Statistics.historicalBandwidths = new LinkedList<>();
        Statistics.expectedArrivalTimes = new LinkedList<>();
        Statistics.resolutionChanges = new LinkedList<>();
        Statistics.throughputChanges = new LinkedList<>();
        Statistics.bandwidthsEveryInterval = new LinkedList<>();
        Statistics.startTimes = new LinkedList<>();
        Statistics.resolutionChanges = new LinkedList<>();
        Statistics.throughputChanges = new LinkedList<>();

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        int methodIndex = Integer.parseInt(SP.getString("method_index", "1"));

        int v = Integer.parseInt(SP.getString("v","1000000"));


        Toast.makeText(getApplicationContext(), "Statistics initialized", Toast.LENGTH_SHORT).show();

        Statistics.startTime = startingTime;
        Statistics.expectedArrivalTimes.add(startingTime + Statistics.initDelay);


        Log.e("error", "the size of transmission queue: " + TransmissionQueue.transmissionQueue.size());
        Thread liveStreamingThread = new Thread(new LiveStreamingRunnable(liveStreamingTime, decisionMakingInterval, bandwidthPredictionWindow, methodIndex, v));
        liveStreamingThread.start();

        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
