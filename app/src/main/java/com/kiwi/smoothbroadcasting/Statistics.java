package com.kiwi.smoothbroadcasting;

import java.util.LinkedList;

/**
 * Created by Administrator on 1/28/2016.
 */
public class Statistics {
    public static LinkedList<Double> delayTimes;        //updated
    public static LinkedList<Double> startTimes;
    public static LinkedList<Double> arrivalTimes;      //updated
    public static LinkedList<Integer> decisions;   //updated
    public static LinkedList<Integer> resolutions;   //updated
    public static LinkedList<Integer> throughputs;    //updated
    public static LinkedList<Double> historicalBandwidths;  //updated

    public static LinkedList<Double> bandwidthsEveryInterval; //updated

    public static LinkedList<Double> expectedArrivalTimes;   //updated


    public static LinkedList<Integer> resolutionChanges;
    public static LinkedList<Integer> throughputChanges;

    //hard coded settings for unit video duration
    public static double unitVideoDuration;


    public static double initDelay;

    public static double averageDelay;

    public static double unitVideoDelay;

    public static double startTime;


    //unit: KB/s
    public final static double _1080P = 4*1024/5;
    public final static double _720P = 2765/5;
    public final static double _480P = 1024/5;
    public final static double _360P = 717/5;
    public final static double _240P = 410/5;
    public final static double _144P = 102/5;

    //KB
    public final static int _1080P_throughput = 4*1024;
    public final static int _720P_throughput = 2765;
    public final static int _480P_throughput = 1024;
    public final static int _360P_throughput = 717;
    public final static int _240P_throughput = 410;
    public final static int _144P_throughput = 102;


    public static int maxNumberOfVideos;
}
