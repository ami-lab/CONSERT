package org.aimas.consert.eventmodel;

import java.util.Calendar;


public class AnnotationUtils {
	
	public static double meanConfidence(double... confidenceValues) {
		int nrVals = confidenceValues.length;
		
		if (nrVals == 0) 
			return 0;
		
		double sum = 0;
		for (double val : confidenceValues) {
			sum += val;
		}
		
		return sum / nrVals;
	}
	
	
	public static double maxConfidence(double... confidenceValues) {
		double max = 0;
		
		for (double val : confidenceValues) {
			if (val > max) 
				max = val;
		}
		
		return max;
	}
	
	
	public static double minConfidence(double... confidenceValues) {
		if (confidenceValues.length == 0)
			return 0;
		
		double min = confidenceValues[0];
		for (double val : confidenceValues) {
			if (val < min) 
				min = val;
		}
		
		return min;
	}
	
	
	public static double maxTimestamp(double... timestampValues) {
		double max = 0;
		
		for (double val : timestampValues) {
			if (val > max) 
				max = val;
		}
		
		return max;
	}
	
	
	public static double minTimestamp(double... timestampValues) {
		
		if (timestampValues.length == 0) 
			return 0;
		
		double min = timestampValues[0];
		for (double val : timestampValues) {
			if (val < min) 
				min = val;
		}
		
		return min;
	}
	
	
	public static boolean allowsTimestampContinuity(long firstEventEnd, long secondEventStart, long threshold) {
		return secondEventStart - firstEventEnd < threshold;
	}
	
	
	public static boolean allowsConfidenceContinuity(double confidence, double valueThreshold) {
		if (confidence < valueThreshold) 
			return false;
		
		return true;
	}
	
	
	public static boolean allowsConfidenceContinuity(double firstEventConfidence, double secondEventConfidence, double differenceThreshold) {
		if (Math.abs(firstEventConfidence - secondEventConfidence) > differenceThreshold)
			return false;
		
		return true;
	}
	
	
	public static ValidityInterval computeIntersection(Calendar firstStart, Calendar firstEnd, Calendar secondStart, Calendar secondEnd) {
		Calendar intersectStart = null;
		Calendar intersectEnd = null;
		
		if (firstEnd.before(secondStart))
			return null;
		
		if (secondEnd.before(firstStart))
			return null;
		
		intersectStart = firstStart.before(secondStart) ? (Calendar)secondStart.clone() : (Calendar)firstStart.clone();		// max start
		intersectEnd = firstEnd.before(secondEnd) ? (Calendar)firstEnd.clone() : (Calendar)secondEnd.clone();				// min end
		
		return new ValidityInterval(intersectStart, intersectEnd);
	}
	
	
	public static ValidityInterval computeIntersection(long firstTsStart, long firstTsEnd, long secondTsStart, long secondTsEnd) {
		Calendar firstStart = Calendar.getInstance();
		firstStart.setTimeInMillis(firstTsStart);
		
		Calendar firstEnd = Calendar.getInstance();
		firstEnd.setTimeInMillis(firstTsEnd);
		
		Calendar secondStart = Calendar.getInstance();
		secondStart.setTimeInMillis(secondTsStart);
		
		Calendar secondEnd = Calendar.getInstance();
		secondEnd.setTimeInMillis(secondTsEnd);
		
		return computeIntersection(firstStart, firstEnd, secondStart, secondEnd);
	}
	
	
	public boolean interesects(Calendar firstStart, Calendar firstEnd, Calendar secondStart, Calendar secondEnd) {
		return !(firstEnd.compareTo(secondStart) <= 0 || secondEnd.compareTo(firstStart) <= 0);
	}
}
