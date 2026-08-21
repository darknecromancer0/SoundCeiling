package dev.soundceiling.app;

/** Pure math for adaptive gain targets. */
final class GainPlanner {
    static Plan dbfs(float sourceRmsDb,float sourcePeakDb,float currentGainDb,float targetRmsDb,float peakCeilingDb,boolean normalize,float strength){return calculate(sourceRmsDb,sourcePeakDb,currentGainDb,targetRmsDb,peakCeilingDb,0f,normalize,strength);}
    static Plan loudness(float sourceLoudness,float sourcePeakDb,float currentGainDb,float targetLoudness,float peakCeilingDb,boolean normalize,float strength){return calculate(sourceLoudness,sourcePeakDb,currentGainDb,targetLoudness,peakCeilingDb,0f,normalize,strength);}
    static Plan spl(float sourceRmsDb,float sourcePeakDb,float currentGainDb,float calibrationOffsetDb,float targetSpl,float peakCeilingSpl,boolean normalize,float strength){return calculate(sourceRmsDb,sourcePeakDb,currentGainDb,targetSpl,peakCeilingSpl,calibrationOffsetDb,normalize,strength);}
    static Plan splFromEstimate(float sourceRmsDb,float sourcePeakDb,float currentControlGainDb,float currentMeasurementGainDb,float calibrationOffsetDb,float targetSpl,float peakCeilingSpl,boolean normalize,float strength){float estimatedRmsSpl=sourceRmsDb+currentMeasurementGainDb+calibrationOffsetDb;float estimatedPeakSpl=sourcePeakDb+currentMeasurementGainDb+calibrationOffsetDb;float ideal=currentControlGainDb+targetSpl-estimatedRmsSpl;float peakLimit=currentControlGainDb+peakCeilingSpl-estimatedPeakSpl;float controlled=normalize?currentControlGainDb+DbMath.clamp(strength,0f,1f)*(ideal-currentControlGainDb):currentControlGainDb;return new Plan(ideal,peakLimit,Math.min(controlled,peakLimit),estimatedPeakSpl,peakCeilingSpl);}
    private static Plan calculate(float rms,float peak,float current,float target,float ceiling,float offset,boolean normalize,float strength){strength=DbMath.clamp(strength,0f,1f);float ideal=target-rms-offset;float peakLimit=ceiling-peak-offset;float controlled=normalize?current+strength*(ideal-current):current;return new Plan(ideal,peakLimit,Math.min(controlled,peakLimit),peak+current+offset,ceiling);}
    static final class Plan{final float idealTargetGainDb,peakGainLimitDb,desiredGainDb,projectedPeak,ceiling;Plan(float i,float p,float d,float projected,float c){idealTargetGainDb=i;peakGainLimitDb=p;desiredGainDb=d;projectedPeak=projected;ceiling=c;}}
    private GainPlanner(){}
}
