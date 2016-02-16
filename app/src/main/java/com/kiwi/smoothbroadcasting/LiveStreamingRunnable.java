package com.kiwi.smoothbroadcasting;

import android.net.TrafficStats;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by Administrator on 1/21/2016.
 */
public class LiveStreamingRunnable implements Runnable {

    private ExecutorService executor;
    private int liveStreamingTime;
    private int decisionMakingInterval;
    private int bandwidthPredictionWindow;

    private final String uploadFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();

    public final static int METHOD_1 = 1;   //360p
    public final static int METHOD_2 = 2;   //480p
    public final static int METHOD_3 = 3;   //unweighted
    public final static int METHOD_4 = 4;   //weighted
    public final static int METHOD_5 = 5;   //ResoMax

    public final static int METHOD_6 = 6;   //480p with preprecessing
    public final static int METHOD_7 = 7;   //ResoMax with preprocessing

    private int methodIndex;

    public int v;

    int[] resolutions = {144, 240, 360, 480, 720, 1080};

    int resolutionChoiceIndex;
    private int[] indexOfThroughput = {Statistics._144P_throughput, Statistics._240P_throughput, Statistics._360P_throughput, Statistics._480P_throughput, Statistics._720P_throughput,
            Statistics._1080P_throughput};

    int countHigherBitrate;
    double lastAddedTimeForHigherBitrate;
    int countLowerBitrate;
    double lastAddedTimeForLowerBitrate;

    boolean isPreprocessingEnabled;

    HashMap<Integer, Double> efficiency;
    HashMap<Integer, Integer> indexTable;
    HashMap<Integer, Integer> indexTableOfVideoIndex;
    int[] resolutionReduction;
    int[] fileSizeReduction;

    public LiveStreamingRunnable(int liveStreamingTime, int decisionMakingInterval, int bandwidthPredictionWindow, int methodIndex, int v) {
        this.executor = Executors.newSingleThreadExecutor();
        this.liveStreamingTime = liveStreamingTime;
        this.decisionMakingInterval = decisionMakingInterval;
        this.bandwidthPredictionWindow = bandwidthPredictionWindow;
        this.methodIndex = methodIndex;
        this.resolutionChoiceIndex = 3;
        this.v = 40000;

        countHigherBitrate = 0;
        countLowerBitrate = 0;
        lastAddedTimeForHigherBitrate = -1;
        lastAddedTimeForLowerBitrate = -1;
//        Log.e("error", "live streaming runnable works");

        if (methodIndex == METHOD_6 || methodIndex == METHOD_7) isPreprocessingEnabled = true;
        else isPreprocessingEnabled = false;

        updatePreprocessingIndexTable();

    }

    @Override
    public void run() {
//        Log.e("error", "live streaming runnable starts running");
        double temp = Statistics.startTime;


        double currentTime = temp;


//        Log.e("error", "live streaming time = " + liveStreamingTime);
//        Log.e("error", "State: "+ Environment.getExternalStorageState());

        long transmittedBytesBefore, transmittedBytesAfter;

        transmittedBytesAfter = TrafficStats.getTotalTxBytes();


        while ((currentTime < (temp + liveStreamingTime)) || (TransmissionQueue.transmissionQueue.size() != 0)) {
            Statistics.startTimes.add(currentTime);


            if (currentTime < (temp + liveStreamingTime))
                TransmissionQueue.transmissionQueue.add(makeDecisionAtCurrentTime(currentTime, methodIndex));


            transmittedBytesBefore = transmittedBytesAfter;


            executor.execute(new UploadThread());

            try {
                Thread.sleep(decisionMakingInterval * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            Log.e("error", "Transmission queue before preprocessing: " + TransmissionQueue.transmissionQueue);
            applyPreprocessing(v);
            Log.e("error", "Transmission queue after preprocessing: " + TransmissionQueue.transmissionQueue);


            transmittedBytesAfter = TrafficStats.getTotalTxBytes();

            currentTime += decisionMakingInterval;

            double realtimeBandwidth;

            if (Statistics.arrivalTimes.size() >= Statistics.bandwidthsEveryInterval.size() + 1) {
                realtimeBandwidth = (transmittedBytesAfter - transmittedBytesBefore) * 1.0 / (Statistics.unitVideoDuration - currentTime + Statistics.arrivalTimes.get(Statistics.bandwidthsEveryInterval.size())) / 1024;

            } else
                realtimeBandwidth = (transmittedBytesAfter - transmittedBytesBefore) * 1.0 / decisionMakingInterval / 1024;


            Statistics.bandwidthsEveryInterval.add(realtimeBandwidth);

//            Log.e("error", "realtime bandwidth: " + Statistics.bandwidthsEveryInterval.getLast());

            Log.e("error", "Throughputs: " + Statistics.throughputs.size());
            Log.e("error", "Throughputchange: " + Statistics.throughputChanges.size());


        }
        executor.shutdown();

    }


    //TODO: Add a video dimension based on decision making engine to a LinkedList
    private int makeDecisionAtCurrentTime(double currentTime, int method) {
        switch (method) {
            case METHOD_1:
                return decisionAtTime360p();
            case METHOD_2:
                return decisionAtTime480p();
            case METHOD_3:
                return decisionAtTimeUnweighted(currentTime);
            case METHOD_4:
                return decisionAtTimeWeighted(currentTime);
            case METHOD_5:
                return decisionAtTimeResoMax(currentTime);
            case METHOD_6:
                return decisionAtTime480p();
            case METHOD_7:
                return decisionAtTimeResoMax(currentTime);
            default:
                return 0;
        }


    }

    //channel aware algorithm, Method 1
    public int decisionAtTime360p() {

        Statistics.decisions.add(3);
        Statistics.throughputs.add(Statistics._360P_throughput);
        Statistics.resolutions.add(360);


        return Statistics.decisions.getLast();
    }

    //channel aware algorithm, Method 2
    public int decisionAtTime480p() {

        Statistics.decisions.add(4);
        Statistics.throughputs.add(Statistics._480P_throughput);
        Statistics.resolutions.add(480);


        return Statistics.decisions.getLast();
    }

    //channel aware algorithm, Method 3
    public int decisionAtTimeUnweighted(double decisionTime) {

        double predictedBandwidth = getPredictedBandwidth();
//        Log.e("error", "predictedBandwidth: " + predictedBandwidth);


        int decision;
        int resolutionDecision;
        int throughputDecision;


        double accumulatedDelayTimes = getAccumulatedDelayTimes();
//        Log.e("error", "accumulated delay times: " + accumulatedDelayTimes);

        double remainingOverallDelayTimes = Math.max(0, Statistics.maxNumberOfVideos * Statistics.averageDelay - accumulatedDelayTimes);
//        Log.e("error", "remainingOverallDelayTimes: " + remainingOverallDelayTimes);

        double deadline = getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime) + Math.min(Statistics.unitVideoDelay, remainingOverallDelayTimes);

//        Log.e("error", "getEstimatedExpectedArrivalTimes: " + (getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime)-decisionTime));


        double predictedThroughput = predictedBandwidth * (deadline - decisionTime);
//        Log.e("error", "predicted Throughoput: " + predictedThroughput);
//        Log.e("error", "transmission queue size: " + getTransmissionQueueSize());

        double availableBitrate = predictedThroughput - getTransmissionQueueSize();

//        Log.e("error", "available bitrate: " + availableBitrate);

//        double[] weights = {1.6, 1.6, 0.7, 0.3, 0.1};


        double[] weights = {1, 1, 1, 1, 1};

        if (availableBitrate >= Statistics._1080P_throughput * weights[0]) {
            throughputDecision = Statistics._1080P_throughput;
            resolutionDecision = 1080;
            decision = 6;
        } else if (availableBitrate >= Statistics._720P_throughput * weights[1]) {
            throughputDecision = Statistics._720P_throughput;
            resolutionDecision = 720;
            decision = 5;
        } else if (availableBitrate >= Statistics._480P_throughput * weights[2]) {
            throughputDecision = Statistics._480P_throughput;
            resolutionDecision = 480;
            decision = 4;
        } else if (availableBitrate >= Statistics._360P_throughput * weights[3]) {
            throughputDecision = Statistics._360P_throughput;
            resolutionDecision = 360;
            decision = 3;
        } else if (availableBitrate >= Statistics._240P_throughput * weights[4]) {
            throughputDecision = Statistics._240P_throughput;
            resolutionDecision = 240;
            decision = 2;
        } else {
            throughputDecision = Statistics._144P_throughput;
            resolutionDecision = 144;
            decision = 1;
        }

        Statistics.decisions.add(decision);
        Statistics.throughputs.add(throughputDecision);
        Statistics.resolutions.add(resolutionDecision);

//                    Statistics.decisions.add(6);
//            Statistics.throughputs.add(Statistics._1080P_throughput);
//            Statistics.resolutions.add(1080);


        return Statistics.decisions.getLast();
    }


    //channel aware algorithm, Method 4
    public int decisionAtTimeWeighted(double decisionTime) {

        double predictedBandwidth = getPredictedBandwidth();
//        Log.e("error", "predictedBandwidth: " + predictedBandwidth);


        int decision;
        int resolutionDecision;
        int throughputDecision;


        double accumulatedDelayTimes = getAccumulatedDelayTimes();
//        Log.e("error", "accumulated delay times: " + accumulatedDelayTimes);

        double remainingOverallDelayTimes = Math.max(0, Statistics.maxNumberOfVideos * Statistics.averageDelay - accumulatedDelayTimes);
//            Log.e("error", "remainingOverallDelayTimes: " + remainingOverallDelayTimes);

        double deadline = getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime) + Math.min(Statistics.unitVideoDelay, remainingOverallDelayTimes);

//            Log.e("error", "getEstimatedExpectedArrivalTimes: " + (getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime)-decisionTime));


        double predictedThroughput = predictedBandwidth * (deadline - decisionTime);
//            Log.e("error", "predicted Throughoput: " + predictedThroughput);
//            Log.e("error", "transmission queue size: " + getTransmissionQueueSize());

        double availableBitrate = predictedThroughput - getTransmissionQueueSize();

//            Log.e("error", "available bitrate: " + availableBitrate);

        double[] weights = {1.6, 1.6, 0.7, 0.3, 0.1};


//			double[] weights ={1, 1, 1, 1, 1};

        if (availableBitrate >= Statistics._1080P_throughput * weights[0]) {
            throughputDecision = Statistics._1080P_throughput;
            resolutionDecision = 1080;
            decision = 6;
        } else if (availableBitrate >= Statistics._720P_throughput * weights[1]) {
            throughputDecision = Statistics._720P_throughput;
            resolutionDecision = 720;
            decision = 5;
        } else if (availableBitrate >= Statistics._480P_throughput * weights[2]) {
            throughputDecision = Statistics._480P_throughput;
            resolutionDecision = 480;
            decision = 4;
        } else if (availableBitrate >= Statistics._360P_throughput * weights[3]) {
            throughputDecision = Statistics._360P_throughput;
            resolutionDecision = 360;
            decision = 3;
        } else if (availableBitrate >= Statistics._240P_throughput * weights[4]) {
            throughputDecision = Statistics._240P_throughput;
            resolutionDecision = 240;
            decision = 2;
        } else {
            throughputDecision = Statistics._144P_throughput;
            resolutionDecision = 144;
            decision = 1;
        }

        Statistics.decisions.add(decision);
        Statistics.throughputs.add(throughputDecision);
        Statistics.resolutions.add(resolutionDecision);

//                    Statistics.decisions.add(6);
//            Statistics.throughputs.add(Statistics._1080P_throughput);
//            Statistics.resolutions.add(1080);


        return Statistics.decisions.getLast();
    }


    //channel aware algorithm, Method 5
    public int decisionAtTimeResoMax(double decisionTime) {

        double predictedBandwidth = getPredictedBandwidth();
//        Log.e("error", "predictedBandwidth: " + predictedBandwidth);


        double accumulatedDelayTimes = getAccumulatedDelayTimes();
//        Log.e("error", "accumulated delay times: " + accumulatedDelayTimes);

        double remainingOverallDelayTimes = Math.max(0, Statistics.maxNumberOfVideos * Statistics.averageDelay - accumulatedDelayTimes);
//        Log.e("error", "remainingOverallDelayTimes: " + remainingOverallDelayTimes);

        double deadline = getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime) + Math.min(Statistics.unitVideoDelay, remainingOverallDelayTimes);

//        Log.e("error", "getEstimatedExpectedArrivalTimes: " + (getEstimatedExpectedArrivalTimes(predictedBandwidth, decisionTime)-decisionTime));


        double predictedThroughput = predictedBandwidth * (deadline - decisionTime);
//        Log.e("error", "predicted Throughoput: " + predictedThroughput);
//        Log.e("error", "transmission queue size: " + getTransmissionQueueSize());

        double availableBitrate = predictedThroughput - getTransmissionQueueSize();

        Log.e("error", "available bitrate: " + availableBitrate);

        //With preprocessing enabled, the suggested bitrate should be raised.

        //change 0.9 to 0.8, if want higher resolution

        if (resolutionChoiceIndex > 0)
            if (availableBitrate < indexOfThroughput[resolutionChoiceIndex] * 0.8) {
                if (lastAddedTimeForLowerBitrate == -1 || (decisionTime - lastAddedTimeForLowerBitrate) <= 10) {

                    countLowerBitrate++;

                } else {
                    countLowerBitrate = 1;

                }
                lastAddedTimeForLowerBitrate = decisionTime;
            }

        //change 0.9 to 0.8, if want higher resolution
        if (resolutionChoiceIndex < 5)
            if (availableBitrate > indexOfThroughput[resolutionChoiceIndex + 1] * 0.8) {
                if (lastAddedTimeForHigherBitrate == -1 || (decisionTime - lastAddedTimeForHigherBitrate) <= 10) {

                    countHigherBitrate++;

                } else {
                    countHigherBitrate = 1;

                }
                lastAddedTimeForHigherBitrate = decisionTime;
            }

        Log.e("error", "countHigherBitrate: " + countHigherBitrate + " countLowerBitrate: " + countLowerBitrate);

        if (countLowerBitrate >= 4) {
            countLowerBitrate = 0;
            lastAddedTimeForLowerBitrate = -1;
            if (resolutionChoiceIndex > 0) resolutionChoiceIndex--;
        }

        //change 5 to 4, if higher resolution is preferred
        if (countHigherBitrate >= 4) {
            countHigherBitrate = 0;
            lastAddedTimeForHigherBitrate = -1;
            if (resolutionChoiceIndex < 5) resolutionChoiceIndex++;
        }


        Statistics.decisions.add(resolutionChoiceIndex + 1);
        Statistics.throughputs.add(indexOfThroughput[resolutionChoiceIndex]);
        Statistics.resolutions.add(resolutions[resolutionChoiceIndex]);


        return Statistics.decisions.getLast();
    }


    public void applyPreprocessing(double v) {
        if (!isPreprocessingEnabled || (TransmissionQueue.transmissionQueue.size() <= 1)) {
            Statistics.resolutionChanges.add(0);
            Statistics.throughputChanges.add(0);
        } else {
            preprocessVidesoInQueue(v);

        }

    }


    public void updatePreprocessingIndexTable() {

        //preprocessing level = original resolution/output resolution
        //size reduction = original data size - output data size
        //there is a mapping between resolution and size


        //preprocessing efficiency sort order:
        //file size reduction/resolution reduction
        //how much file size can be reduced per unit resolution reduction


        //preprocessing level No., resolution reduction, file size reduction, efficiency
        //0, 0, 0
        //1080p
        //1, 1080-720=360,
        //2, 1080-480=600,
        //3, 1080-360=720,
        //4, 1080-240=840,
        //5, 1080-144=936,

        //720p
        //6, 720-480=240,
        //7, 720-360=360,
        //8,

        this.efficiency = new HashMap<>();

        efficiency.put(0, 0.0);

        this.resolutionReduction = new int[16];

        resolutionReduction[0] = 0;

        this.fileSizeReduction = new int[16];

        fileSizeReduction[0] = 0;

        this.indexTable = new HashMap<>();

        indexTable.put(0, 0);

        this.indexTableOfVideoIndex = new HashMap<>();

        indexTableOfVideoIndex.put(0, 0);


        int initIndex = 0;
        for (int i = resolutions.length - 1; i >= 0; i--) {
            for (int j = i - 1; j >= 0; j--) {
                initIndex++;
                indexTable.put(initIndex, indexOfThroughput[i]);
                indexTableOfVideoIndex.put(initIndex, j + 1);

                resolutionReduction[initIndex] = resolutions[i] - resolutions[j];
                fileSizeReduction[initIndex] = indexOfThroughput[i] - indexOfThroughput[j];
                efficiency.put(initIndex, fileSizeReduction[initIndex] * 1.0 / resolutionReduction[initIndex]);
//				System.out.println(resolutionReduction[initIndex]+ " " + fileSizeReduction[initIndex]);
                Log.e("error", resolutionReduction[initIndex] + " " + fileSizeReduction[initIndex]);
            }
        }
//		System.out.println(resolutionReduction.length + " " + fileSizeReduction.length);

        Log.e("error", indexTable.toString());
        Log.e("error", indexTableOfVideoIndex.toString());


    }


    public void preprocessVidesoInQueue(double v) {
        //L framework method: monitor queue length and bandwidth

        //iterate through all options for preprocessing, sort the answers by results
        //(current queue length - next timeslot data transmission) * fileSizeReduction - V * resolutionReduction
        //next timeslot data transmission is decided base on current queue length and fileSizeReduction
        double currentQueueLength = getTransmissionQueueSize();

        double predictedBandwidth = getPredictedBandwidth();

        HashMap<Integer, Double> results = new HashMap<>();

        for (int i = 0; i < fileSizeReduction.length; i++) {
            double currentFileSizeReduction = fileSizeReduction[i];
            int currentResolutionReduction = resolutionReduction[i];

            double nextTimeslotDataTransmission;
            if ((predictedBandwidth * Statistics.unitVideoDuration) < (currentQueueLength - currentFileSizeReduction))
                nextTimeslotDataTransmission = predictedBandwidth * Statistics.unitVideoDuration;
            else
                nextTimeslotDataTransmission = currentQueueLength - currentFileSizeReduction;

            double currentResult = (currentQueueLength - nextTimeslotDataTransmission) * currentFileSizeReduction - v * currentResolutionReduction;

            results.put(i, currentResult);
        }

        LinkedHashMap<Integer, Double> sortedResults = HashMapSorting.sortHashMapByValuesD(results);

//		System.out.println("sorted results: "+ sortedResults);

        Log.e("error", "sorted results: " + sortedResults);

//		Map.Entry<Integer, Double> firstEntry = sortedResults.entrySet().iterator().next();
//		int bestResultsKey = firstEntry.getKey();

        //If the best way is not to preprocess, then return directly.
//		if(sortedResults.entrySet().iterator().next().getKey() == 0) return;


        //If preprocessing is considered better than non-preprocessing, do the following qualification process:
        //video selection criteria: according to suggested proprocessing policy, look for qualified unit videos in the queue

        for (Map.Entry<Integer, Double> entry : sortedResults.entrySet()) {
//			System.out.println(entry);
            //if the suggested is non-processing, then return directly
            if (entry.getKey() == 0) {
                Statistics.throughputChanges.add(0);
                Statistics.resolutionChanges.add(0);
//				System.out.println("suggested is nonpreprocessing.");
                Log.e("error", "No preprocessing");
                return;
            }

            int targetFileSize = indexTable.get(entry.getKey());
            Log.e("error", "target File size: " + targetFileSize);


            //when qualified videos are found, verify if there is sufficient time to process it before it is its turn to transmit
            //current stats: 1080p transcoding needs 6 times its original duraiton

            double accumulatedDataSize = 0;
            if (TransmissionQueue.transmissionQueue.size() > 0)
                accumulatedDataSize += indexOfThroughput[TransmissionQueue.transmissionQueue.get(0) - 1];
            for (int i = 1; i < TransmissionQueue.transmissionQueue.size(); i++) {

//				System.out.println("queue:" + queue);
                if (indexOfThroughput[TransmissionQueue.transmissionQueue.get(i) - 1] == targetFileSize)
                    if (isPreprocessingThisVideoOK(indexTable.get(entry.getKey()), accumulatedDataSize / predictedBandwidth)) {
                        Statistics.throughputChanges.add(fileSizeReduction[entry.getKey()]);
                        Statistics.resolutionChanges.add(resolutionReduction[entry.getKey()]);

                        TransmissionQueue.transmissionQueue.set(i, indexTableOfVideoIndex.get(entry.getKey()));
//						System.out.println("after:"+ queue.get(i));
//						System.out.println("change occurs: " + Statistics.fileSizeChanges.getLast() + " "+ Statistics.resolutionChanges.getLast());
                        return;
                    }

                accumulatedDataSize += indexOfThroughput[TransmissionQueue.transmissionQueue.get(i) - 1];
            }


        }

//		Statistics.fileSizeChanges.add(0.0);
//		Statistics.resolutionChanges.add(0);
//		System.out.println("no changes.");


        //if qualification passes, do the actual transcoding:
        //video transcoding according to suggested preprocessing policy


    }


    public boolean isPreprocessingThisVideoOK(int targetThroughput, double estimatedTransmissionTime) {
        double processingTime;

//		System.out.println("Target bitrate:" + targetBitrate);
        switch (targetThroughput) {
            case Statistics._144P_throughput:
                processingTime = 0.1 * Statistics.unitVideoDuration;
                break;
            case Statistics._240P_throughput:
                processingTime = 0.2 * Statistics.unitVideoDuration;
                break;
            case Statistics._360P_throughput:
                processingTime = 0.4 * Statistics.unitVideoDuration;
                break;
            case Statistics._480P_throughput:
                processingTime = 1 * Statistics.unitVideoDuration;
                break;
            case Statistics._720P_throughput:
                processingTime = 1.6 * Statistics.unitVideoDuration;
                break;
            case Statistics._1080P_throughput:
                processingTime = 5 * Statistics.unitVideoDuration;
                break;
            default:
                processingTime = -1;
                Log.e("error", "Proprocessing time abnormal");
                break;
        }

//		System.out.println("processing time:" + processingTime + "needed time:" + estimatedTransmissionTime);
        Log.e("error", "enough time to transmit: " + (processingTime <= estimatedTransmissionTime));

        if (processingTime >= estimatedTransmissionTime) return false;
        else return true;


    }


    public double getEstimatedExpectedArrivalTimes(double predictedBandwidth, double currentTime) {

        double latestExpectedArrivalTime = Statistics.expectedArrivalTimes.getLast();
        for (int i = 0; i < TransmissionQueue.transmissionQueue.size(); i++) {
            if (i == 0)
                latestExpectedArrivalTime = Math.max(latestExpectedArrivalTime, (currentTime + Math.max(0, (TransmissionQueue.transmissionQueue.get(i) - predictedBandwidth * decisionMakingInterval)) / predictedBandwidth)) + Statistics.unitVideoDuration;
            else
                latestExpectedArrivalTime = Math.max(latestExpectedArrivalTime, (currentTime + TransmissionQueue.transmissionQueue.get(i) / predictedBandwidth)) + Statistics.unitVideoDuration;
        }


        return latestExpectedArrivalTime;
    }


    public double getTransmissionQueueSize() {
        double existingFileSizes = 0;
        for (int i = 0; i < TransmissionQueue.transmissionQueue.size(); i++) {
            existingFileSizes += indexOfThroughput[TransmissionQueue.transmissionQueue.get(i) - 1];
        }
        if (TransmissionQueue.transmissionQueue.size() > 0)
            existingFileSizes = existingFileSizes - getPredictedBandwidth() * Statistics.unitVideoDuration;

        return Math.max(0, existingFileSizes);
    }

    public double getPredictedBandwidth() {
        double predictedBandwidth = 60;
        if (Statistics.bandwidthsEveryInterval.size() > 0) {
            int i;
            double accumBand = 0;
            int theNumberOfDataConsidered = Math.min(Statistics.bandwidthsEveryInterval.size(), bandwidthPredictionWindow);
            for (i = Statistics.bandwidthsEveryInterval.size() - 1; i >= Statistics.bandwidthsEveryInterval.size() - theNumberOfDataConsidered; i--) {
                accumBand += Statistics.bandwidthsEveryInterval.get(i);
            }
            predictedBandwidth = accumBand / theNumberOfDataConsidered;
        }


        return predictedBandwidth;
    }

    public double getAccumulatedDelayTimes() {
        double accumDelay = 0;
        if (Statistics.delayTimes.size() > 0) {
            int i;
            for (i = 0; i < Statistics.delayTimes.size(); i++) {
                accumDelay += Statistics.delayTimes.get(i);
            }
        }


        return accumDelay;
    }


    class UploadThread implements Runnable {
        private int videoDimension;

        public UploadThread() {
        }

        @Override
        public void run() {
            //Only upload if the transmission queue is not empty
            if (TransmissionQueue.transmissionQueue.size() > 0) {
                videoDimension = TransmissionQueue.transmissionQueue.getFirst();


                try {
                    upload(videoDimension);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        public void upload(int videoDimension) throws IOException {
            // TODO: use tus.io protocol to do the upload task
//            try {
//                Thread.sleep((decisionMakingInterval+3) * 1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            uploadFile(uploadFilePath + File.separator + videoDimension);


        }

        public int uploadFile(String sourceFileUri) throws IOException {
            int serverResponseCode = 0;
            String upLoadServerUri = "http://158.132.10.194:25001/livestreaming/UploadToServer.php";

            String fileName = sourceFileUri;

            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1 * 1024 * 1024;
            File sourceFile = new File(sourceFileUri);
//            File sourceFile = new File(Environment.getExternalStorageDirectory(), "3");
//            Log.e("error", "source file url = "+sourceFileUri);

            InputStream is = null;
//            if (!sourceFile.isFile()) {
            if (false) {


                return 0;

            } else {
                try {

                    // open a URL connection to the Servlet
                    FileInputStream fileInputStream = new FileInputStream(sourceFile);
                    URL url = new URL(upLoadServerUri);

                    // Open a HTTP  connection to  the URL
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("POST");
//                    conn.setReadTimeout(10000 /* milliseconds */);
//                    conn.setConnectTimeout(15000 /* milliseconds */);
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                    conn.setRequestProperty("uploaded_file", fileName);

                    dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name='uploaded_file'; filename='"
                            + fileName + "'" + lineEnd);

                    dos.writeBytes(lineEnd);

                    // create a buffer of  maximum size
                    bytesAvailable = fileInputStream.available();

                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    buffer = new byte[bufferSize];

                    // read file and write it into form...
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    while (bytesRead > 0) {

                        dos.write(buffer, 0, bufferSize);
                        bytesAvailable = fileInputStream.available();
                        bufferSize = Math.min(bytesAvailable, maxBufferSize);
                        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    }

                    // send multipart form data necesssary after file data...
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                    // Responses from the server (code and message)
                    serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn.getResponseMessage();

//                    Log.i("uploadFile", "HTTP Response is : "
//                            + serverResponseMessage + ": " + serverResponseCode);

//                if(serverResponseCode == 200){
//
//                }

                    //close the streams //
                    try {
                        is = conn.getInputStream();
                        String contentAsString = readIt(is, 13);
                        double returnedTime = Double.parseDouble(contentAsString);

                        Log.i("uploadFile", "HTTP Response is : "
                                + returnedTime);

                        TransmissionQueue.transmissionQueue.remove(); //(The first element of the LinkedList will be removed)
                        Statistics.arrivalTimes.add(returnedTime);

                        double historicalBandwidth;
                        if (Statistics.arrivalTimes.size() >= 2) {
                            historicalBandwidth = Statistics.throughputs.get(Statistics.arrivalTimes.size() - 1) / (Statistics.arrivalTimes.getLast() - Math.max(Statistics.startTimes.get(Statistics.arrivalTimes.size() - 1), Statistics.arrivalTimes.get(Statistics.arrivalTimes.size() - 2)));

                        } else
                            historicalBandwidth = Statistics.throughputs.get(Statistics.arrivalTimes.size() - 1) / (Statistics.arrivalTimes.getLast() - Statistics.startTime);
//                        Log.e("error","historical bandwidth: "+ historicalBandwidth);
                        Statistics.historicalBandwidths.add(historicalBandwidth);

                        Statistics.delayTimes.add(Math.max(0, (Statistics.arrivalTimes.getLast() - Statistics.expectedArrivalTimes.getLast())));

                        double expectedArrivalTimeForNextVideo = Math.max(Statistics.expectedArrivalTimes.getLast(), returnedTime) + Statistics.unitVideoDuration;
                        if (Statistics.expectedArrivalTimes.size() <= (Statistics.maxNumberOfVideos - 1))
                            Statistics.expectedArrivalTimes.add(expectedArrivalTimeForNextVideo);
                        if (Statistics.expectedArrivalTimes.size() > 1)
                            Log.e("error", "the difference between two expected arrival times: " + (Statistics.expectedArrivalTimes.getLast() - Statistics.expectedArrivalTimes.get(Statistics.expectedArrivalTimes.size() - 2)));

                    } catch (Exception e) {
                        Log.e("error", "failed to read inputstream");
                    }

                    fileInputStream.close();
                    dos.flush();
                    dos.close();

//                    Log.e("error", "the size of transmission queue: " + TransmissionQueue.transmissionQueue);
//                    Log.e("error", "decisions: " + Statistics.decisions);
//                    Log.e("error", "arrivalTimes: " + Statistics.arrivalTimes);
//                    Log.e("error", "expectedArrivalTimes: " + Statistics.expectedArrivalTimes);
//                    Log.e("error", "delayTimes: " + Statistics.delayTimes);
//                    Log.e("error", "historicalBandwidths: " + Statistics.historicalBandwidths);
//                    Log.e("error", "realtime bandwidths: " + Statistics.bandwidthsEveryInterval);


                    messageHandler.sendEmptyMessage(0);


                } catch (MalformedURLException ex) {


                    ex.printStackTrace();


                    Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
                } catch (Exception e) {


                    e.printStackTrace();


                    Log.e("error", "Exception : "
                            + e.getMessage(), e);
                } finally {
                    if (is != null) {
                        is.close();

                    }
                }

                return serverResponseCode;

            } // End else block
        }

    }


    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    Handler messageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            LiveStreamingStatusActivity.textView2.setText("Progress: " + Statistics.delayTimes.size() * 1.0 / Statistics.maxNumberOfVideos * 100 + "%");

            LiveStreamingStatusActivity.textView.setText("Method: " + methodIndex + "\n");
            LiveStreamingStatusActivity.textView.append("Unit video delay: " + Statistics.unitVideoDelay + "\n");
            LiveStreamingStatusActivity.textView.append("Average delay: " + Statistics.averageDelay + "\n");
            LiveStreamingStatusActivity.textView.append("Init delay: " + Statistics.initDelay + "\n");
            LiveStreamingStatusActivity.textView.append("V = " + v + "\n");

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

            double overallChangedResolution = 0;

            for (int i = 0; i < Statistics.resolutionChanges.size(); i++) {
                overallChangedResolution -= Statistics.resolutionChanges.get(i);
            }

            double averageThroughput = 0;
            for (int i = 0; i < Statistics.throughputs.size(); i++) {
                averageThroughput += Statistics.throughputs.get(i);
            }

            averageThroughput = averageThroughput / Statistics.throughputs.size();

            double overallChangedThroughput = 0;

            for (int i = 0; i < Statistics.throughputChanges.size(); i++) {
                overallChangedThroughput -= Statistics.throughputChanges.get(i);
            }



            double averageBandwidth = 0;
            for (int i = 0; i < Statistics.bandwidthsEveryInterval.size(); i++) {
                averageBandwidth += Statistics.bandwidthsEveryInterval.get(i);
            }
            averageBandwidth = averageBandwidth / Statistics.bandwidthsEveryInterval.size();


            LiveStreamingStatusActivity.textView.append("Average delay time: " + averageDelayTime + "\n");
            LiveStreamingStatusActivity.textView.append("Average resolution: " + averageResolution + "\n");
            LiveStreamingStatusActivity.textView.append("Average throughput: " + averageThroughput + "\n");
            LiveStreamingStatusActivity.textView.append("Average bandwidth: " + averageBandwidth + "\n");
            LiveStreamingStatusActivity.textView.append("Overall changed resolution: " + overallChangedResolution + "\n");
            LiveStreamingStatusActivity.textView.append("Overall Changed throughput: " + overallChangedThroughput);
        }
    };


}
