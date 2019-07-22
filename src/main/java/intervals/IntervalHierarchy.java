/*
 * Copyright (C) 2019 xmw13bzu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package intervals;

import evaluation.storage.ClassifierResults;
import intervals.IntervalHierarchy.Interval;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;

/**
 *
 * @author James Large (james.large@uea.ac.uk)
 */
public class IntervalHierarchy implements Iterable<Interval> {

    public static final int maxNumDifferentIntervals = 210;
    public static final int maxNumIntervalPoints = 20; //so 21 values really, 0 .. 20 corresponding to props 0 .. 1

    
    Interval[][] heirarchy;
    ArrayList<Interval> orderedEvaluatedIntervals;
    SingleHierarchyEval hierEval;
    
    public static Function<ClassifierResults, Double> defaultMetric = ClassifierResults.GETTER_Accuracy;
    
    public class Interval implements Comparable<Interval> {
        public int intervalID;
        
        public double[] intervalPercents;
        public double startPercent;
        public double endPercent;
        
        public String intervalStr;
        
        public int startInd;
        public int endInd;
        public int[] intervalInds;
        
        public ClassifierResults res;
        public double score;
        
        public boolean betterThanFullSeries;
        
        public Interval() { 
            
        }
        
        public Interval(double[] intervalPercents) { 
            this.intervalPercents = intervalPercents;
            
            startPercent = intervalPercents[0];
            endPercent = intervalPercents[1];
            
            intervalStr = buildIntervalStr(intervalPercents);
        }
        
        public Interval(int intervalID) throws Exception { 
            this.intervalID = intervalID;
            
            intervalPercents = defineInterval(intervalID);
            
            startPercent = intervalPercents[0];
            endPercent = intervalPercents[1];
            
            intervalStr = buildIntervalStr(intervalPercents);
            
        }
        
        public boolean betterThanFullSeries(Interval fullSeriesInterval) {
            return betterThanFullSeries(fullSeriesInterval, defaultMetric);
        }
        
        public boolean betterThanFullSeries(Interval fullSeriesInterval, Function<ClassifierResults, Double> metric) {
            betterThanFullSeries = metric.apply(fullSeriesInterval.res) <= metric.apply(this.res);
            return betterThanFullSeries;
        }

        @Override
        public int compareTo(Interval o) {
            // if 'less than' means lower quality, then... 
            
            int c = Double.compare(defaultMetric.apply(this.res), defaultMetric.apply(o.res));
            if (c != 0) // smaller accuracy means less than
                return c;
            else        // same accuracy? then longer length means less than
                return Double.compare(o.intervalPercents[1] - o.intervalPercents[0], this.intervalPercents[1] - this.intervalPercents[0]);
        }
    }
    
    public class SingleHierarchyEval {
        public int numIntervalsBetterThanFullSeries;
        public double propIntervalsBetterThanFullSeries;
        
        public SingleHierarchyEval() { 
            for (Interval interval : orderedEvaluatedIntervals)
                if (interval.betterThanFullSeries)
                    numIntervalsBetterThanFullSeries++;
            
            propIntervalsBetterThanFullSeries = (double) numIntervalsBetterThanFullSeries / orderedEvaluatedIntervals.size();
        }
    }
    
    /**
     * An iterator that starts at the highest resolution (smallest length) intervals,
     * and then moves up in length. i.e. [0][0], [0][1] .... [1][0], [1][1] ... [maxNumIntervalPoints-1][0]
     */
    public class HeirarchyIterator implements Iterator<Interval> {

        int intervalLengthInd = 0;
        int intervalStartInd = 0;
        
        @Override
        public boolean hasNext() {
            return intervalLengthInd < heirarchy.length && intervalStartInd < heirarchy[intervalLengthInd].length;
        }

        @Override
        public Interval next() {
            Interval res = heirarchy[intervalLengthInd][intervalStartInd];
            
            if (++intervalStartInd == heirarchy[intervalLengthInd].length) {
                intervalStartInd = 0;
                intervalLengthInd++;
            }
            
            return res;
        }
        
    }
    
    @Override
    public Iterator<Interval> iterator() {
        return new HeirarchyIterator();
    }




    
    public IntervalHierarchy() throws Exception {
        buildHeirarchy();
    }
    
    public IntervalHierarchy(String split, String baseResPath, String baseClassifier, String dataset, int fold) throws Exception {
        buildHeirarchy(split, baseResPath, baseClassifier, dataset, fold);
    }
    
    public void buildHeirarchy() throws Exception {
        buildHeirarchy(null, null, null, null, 0);
    }
    
    public void buildHeirarchy(String split, String resultsPath, String baseClassifier, String dataset, int fold) throws Exception {
        
        heirarchy = new Interval[maxNumIntervalPoints][];
        
        int size = maxNumIntervalPoints;
        for (int i = 0; i < maxNumIntervalPoints; i++) {
            heirarchy[i] = new Interval[size--];
        }
        
        
        
        for (int i = 0; i < maxNumDifferentIntervals; i++) {
            Interval interval = new Interval(i);
            int[] inds = findHeirarchyIndices(interval.intervalPercents);
        
            if (resultsPath != null)
                interval.res = new ClassifierResults(resultsPath + buildIntervalClassifierName(baseClassifier, interval.intervalPercents) + "/Predictions/" + dataset + "/"+split+"Fold"+ fold + ".csv");
            
            if (heirarchy[inds[0]][inds[1]] != null) {
                throw new Exception("Same heirarchy position already populate, likely double precision error still");
//                Interval lookatme = heirarchy[inds[0]][inds[1]];
//                inds = findHeirarchyIndices(interval.intervalPercents);
            }
            heirarchy[inds[0]][inds[1]] = interval;
        }
    }
    
    
    public static int[] findHeirarchyIndices(double[] interval) {
        double rawIntervalLength_precision = interval[1] - interval[0];
        //can encounter double precision problems, e.g 0.15 - 0.1 = 0.499999999
        //round to nearest 1/maxNumIntervalPoints, i.e. nearest 0.05 by default
        double rawIntervalLength = Math.round(rawIntervalLength_precision * (double)maxNumIntervalPoints) / (double)maxNumIntervalPoints;
        
        int normIntervalLength = (int)(Math.round(rawIntervalLength * maxNumIntervalPoints));
        int intervalStart = (int)(Math.round(interval[0] * maxNumIntervalPoints));
        
        return new int[] { normIntervalLength-1, intervalStart }; //-1 to zero-index
    }
    
    
    public String toString() { 
        StringBuilder sb = new StringBuilder();
        
        //headings vals interleaved
        for (int i = maxNumIntervalPoints-1; i >= 0; i--) {
            for (int j = 0; j < heirarchy[i].length; j++)
                sb.append(String.format("%8s, ", heirarchy[i][j].intervalStr));
            sb.append("\n");
            
            for (int j = 0; j < heirarchy[i].length; j++)
                sb.append(String.format("%.6f, ", heirarchy[i][j].res.getAcc()));
            sb.append("\n\n");
        }
        sb.append("\n");
        
        //headings only
        for (int i = maxNumIntervalPoints-1; i >= 0; i--) {
            for (int j = 0; j < heirarchy[i].length; j++)
                sb.append(String.format("%8s, ", heirarchy[i][j].intervalStr));
            sb.append("\n");
        }
        sb.append("\n");
        
        //vals only
        for (int i = maxNumIntervalPoints-1; i >= 0; i--) {
            for (int j = 0; j < heirarchy[i].length; j++)
                sb.append(String.format("%.6f, ", heirarchy[i][j].res.getAcc()));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    public double[] getAvgImportances() { 
        return getAvgImportances(defaultMetric);
    }
    
    public double[] getMinImportances() { 
        return getMinImportances(defaultMetric);
    }
    
    public double[] getAvgImportances(Function<ClassifierResults, Double> metric) { 
        double[] importances = new double[maxNumIntervalPoints];
        int[] numTotalOccurences = new int[maxNumIntervalPoints]; //for dividing to find mean at end
        
        //init, highest resolution intervals align with the highest resolution importances we
        //can ascribe, naturally
        Interval[] smallestIntervals = heirarchy[0];
        for (int j = 0; j < maxNumIntervalPoints; j++) {
            importances[j] = metric.apply(smallestIntervals[j].res);
            numTotalOccurences[j] = 1;
        }
        
        for (int i = 1; i < maxNumIntervalPoints; i++) {
            for (int j = 0; j < heirarchy[i].length; j++) {
                Interval outer = heirarchy[i][j];
                
                for (int k = 0; k < maxNumIntervalPoints; k++) {
                    Interval inner = smallestIntervals[k];
                    
                    if (containedWithin(inner, outer)) {
                        importances[k] += metric.apply(outer.res);
                        numTotalOccurences[k]++;
                    }
                }
            }
        }
        
        for (int i = 0; i < maxNumIntervalPoints; i++)
            importances[i] /= numTotalOccurences[i];
        
        return importances;
    }
    
    public double[] getMinImportances(Function<ClassifierResults, Double> metric) { 
        double[] importances = new double[maxNumIntervalPoints];
        
        //init, highest resolution intervals align with the highest resolution importances we
        //can ascribe, naturally
        Interval[] smallestIntervals = heirarchy[0];
        for (int j = 0; j < maxNumIntervalPoints; j++)
            importances[j] = metric.apply(smallestIntervals[j].res);
        
        for (int i = 1; i < maxNumIntervalPoints; i++) {
            for (int j = 0; j < heirarchy[i].length; j++) {
                Interval outer = heirarchy[i][j];
                
                for (int k = 0; k < maxNumIntervalPoints; k++) {
                    Interval inner = smallestIntervals[k];
                    
                    if (containedWithin(inner, outer)) {
                        double score = metric.apply(outer.res);
                        if (score < importances[k])
                            importances[k] = score;
                    }
                }
            }
        }
        
        return importances;
    }
    
    /**
     * @return total build time of all intervals, in nanoseconds
     */
    public long getBuildTimeTotalHeirarchy() { 
        long totalTime = 0;
        
        for (Interval interval : this)
            totalTime += interval.res.getBuildTimeInNanos();
        
        return totalTime;
    }
    
    /**
     * @return build time of the full series 'interval', in nanoseconds
     */
    public long getBuildTimeFullSeries() {
        return getFullSeriesInterval().res.getBuildTimeInNanos();
    }
    
    public Interval getFullSeriesInterval() { 
        return heirarchy[maxNumIntervalPoints-1][0];
    }
    
    
    public ArrayList<Interval> getOrderedIntervals() { 
        if (orderedEvaluatedIntervals == null)
            computeIntervalOrdering();
        
        return orderedEvaluatedIntervals;
    }
    
    public void computeIntervalOrdering() {
        Interval fullSeries = getFullSeriesInterval();
        
        orderedEvaluatedIntervals = new ArrayList<>();
        for (Interval interval : this)
            orderedEvaluatedIntervals.add(interval);
        
        Collections.sort(orderedEvaluatedIntervals);
        Collections.reverse(orderedEvaluatedIntervals); //todo fix do do descending in one
        
        for (Interval interval : orderedEvaluatedIntervals)
            interval.betterThanFullSeries(fullSeries);
    }
    
    public SingleHierarchyEval getHierarchyEvaluation() { 
        if (hierEval == null)
            hierEval = new SingleHierarchyEval();
        return hierEval;
    }
    
    private boolean containedWithin(Interval inner, Interval outter) { 
        return inner.startPercent >= outter.startPercent && inner.endPercent <= outter.endPercent;
    }
    
    
    public static String buildIntervalStr(double[] interval) {
        return String.format("%.2f", interval[0]).split("\\.")[1] + "_" + String.format("%.2f", interval[1]).split("\\.")[1];
    }

    
    public static String buildIntervalClassifierName(String classifier, double[] interval) {
        return classifier + "_" + buildIntervalStr(interval);
    }

    public static double[] defineInterval(int intID) throws Exception {
        int startId = 0;
        int endId = 1;
        int c = 0;
        while (c != intID) {
            if (++endId > maxNumIntervalPoints) {
                startId++;
                endId = startId + 1;
                if (startId > maxNumIntervalPoints - 1) {
                    throw new Exception("something wrong in interval defintion, startId=" + startId + " endId=" + endId + " intId=" + intID);
                }
            }
            c++;
        }
        double startProp = (double) startId / maxNumIntervalPoints;
        double endProp = (double) endId / maxNumIntervalPoints;
        return new double[]{startProp, endProp};
    }

    
    
    
    
    
    
    public static void main(String[] args) throws Exception {
        IntervalHierarchy ih = new IntervalHierarchy("train", "E:/Intervals/GunpointExampleAna/locallyCreatedResults/", "ED", "Gunpoint", 0);
        
        System.out.println(ih);
        
        System.out.println("\n\n");
        
        System.out.println("avg");
        System.out.println(Arrays.toString(ih.getAvgImportances()));
        System.out.println("min");
        System.out.println(Arrays.toString(ih.getMinImportances()));
        
        System.out.println("\n\n");
        
        System.out.println("Heirarchy build time: " + ih.getBuildTimeTotalHeirarchy());
        System.out.println("Fullseries build time: " + ih.getBuildTimeFullSeries());
    }
    
}