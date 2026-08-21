package dev.soundceiling.app;

final class ControlVolumeCurve {
    private static final float MUTED_GAIN_DB=-80f, LOOKUP_TOLERANCE_DB=.20f;
    private final int minIndex,maxIndex; private final float[] gains;
    ControlVolumeCurve(int minIndex,int maxIndex){if(maxIndex<=minIndex)throw new IllegalArgumentException("degenerate volume range");this.minIndex=minIndex;this.maxIndex=maxIndex;gains=new float[maxIndex-minIndex+1];gains[0]=MUTED_GAIN_DB;for(int o=1;o<gains.length;o++){float n=o/(float)(gains.length-1);gains[o]=(float)(20.0*Math.log10(n));}}
    int minIndex(){return minIndex;} int maxIndex(){return maxIndex;}
    float gainDbForIndex(int index){return gains[DbMath.clamp(index,minIndex,maxIndex)-minIndex];}
    int capIndexFromPercent(int percent){float n=DbMath.clamp(percent,0,100)/100f;return DbMath.clamp(Math.round(minIndex+n*(maxIndex-minIndex)),minIndex,maxIndex);}
    int bestIndexAtOrBelowGain(float desiredGainDb,int capIndex){int cap=DbMath.clamp(capIndex,minIndex,maxIndex),best=minIndex;float err=Float.MAX_VALUE;for(int i=minIndex;i<=cap;i++){float g=gainDbForIndex(i);if(g<=desiredGainDb+LOOKUP_TOLERANCE_DB){float e=Math.abs(desiredGainDb-g);if(e<err){best=i;err=e;}}}return best;}
    float[] snapshot(){return gains.clone();}
}
