package com.kiwi.smoothbroadcasting;

import java.util.*;

/**
 * Created by cxm170 on 2015-10-23.
 */
public class HashMapSorting {
    public static void main(String[] args){

        HashMap<Integer, Double> efficiency = new HashMap<>();

        efficiency.put(0, 0.0);

        int[] resolutionReduction = new int[16];

        resolutionReduction[0] = 0;

        double[] fileSizeReduction = new double[16];

        fileSizeReduction[0] = 0;

        HashMap<Integer, Double> indexTable = new HashMap<>();

        indexTable.put(0,0.0);


        int[] resolutions = {144, 240, 360, 480, 720, 1080};
        double[] fileSizes = {Statistics._144P, Statistics._240P, Statistics._360P, Statistics._480P, Statistics._720P, Statistics._1080P};

        int initIndex = 0;
        for(int i = resolutions.length-1;i>=0;i--){
            for(int j = i-1;j>=0;j--){
                initIndex++;
                indexTable.put(initIndex, fileSizes[i]);

                resolutionReduction[initIndex] = resolutions[i] - resolutions[j];
                fileSizeReduction[initIndex] = fileSizes[i] - fileSizes[j];
                efficiency.put(initIndex, fileSizeReduction[initIndex]/resolutionReduction[initIndex]);
            }
        }

        System.out.println(efficiency);
        System.out.println(sortHashMapByValuesD(efficiency));

//        for(int i = 0; i < resolutionReduction.length; i++)
//        System.out.print(resolutionReduction[i]+" ");
//
//        System.out.println("");
//        for(int i = 0; i < resolutionReduction.length; i++)
//        System.out.print(fileSizeReduction[i] + " ");




    }


    public static LinkedHashMap sortHashMapByValuesD(HashMap passedMap) {
        List mapKeys = new ArrayList(passedMap.keySet());
        List mapValues = new ArrayList(passedMap.values());
        Collections.sort(mapValues, DESCEND_ORDER);
        Collections.sort(mapKeys, DESCEND_ORDER_2);

//        System.out.println(mapValues);

        LinkedHashMap sortedMap = new LinkedHashMap();

        Iterator valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            Object val = valueIt.next();
            Iterator keyIt = mapKeys.iterator();

            while (keyIt.hasNext()) {
                Object key = keyIt.next();
                String comp1 = passedMap.get(key).toString();
                String comp2 = val.toString();

                if (comp1.equals(comp2)){
                    passedMap.remove(key);
                    mapKeys.remove(key);
                    sortedMap.put((Integer)key, (Double)val);
                    break;
                }

            }

        }
        return sortedMap;
    }

    static final Comparator<Double> DESCEND_ORDER =
            new Comparator<Double>() {
                public int compare(Double e1, Double e2) {
                    if(e2.doubleValue() >= e1.doubleValue())
                        return 1;
                    else if(e2.doubleValue() == e1.doubleValue())
                        return 0;
                    else
                        return -1;

                }
            };

    static final Comparator<Integer> DESCEND_ORDER_2 =
            new Comparator<Integer>() {
                public int compare(Integer e1, Integer e2) {
                    return e1.intValue()-e2.intValue();
                }
            };

}
