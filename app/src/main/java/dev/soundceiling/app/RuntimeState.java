package dev.soundceiling.app;

final class RuntimeState {
    enum CaptureStatus { STOPPED, STARTING, RUNNING, WAITING_SIGNAL, ERROR }
    enum ControlActivity { IDLE, HOLDING, DECREASING, RAISING, MINIMUM_LIMIT, MAXIMUM_LIMIT, ERROR }

    final boolean running;
    final CaptureStatus captureStatus;
    final ControlActivity controlActivity;
    final boolean signalPresent;
    final float rmsDbfs, peakDbfs, rawPeakDbfs, sourceLoudness, estimatedRmsSpl, estimatedPeakSpl;
    final int volumeIndex, volumeMax, effectiveMaxIndex, safetyLockIndex;
    final boolean manualSafetyPause, safetyLockEnabled;
    final String routeLabel, profileName, logStatus, message, backendLabel;
    final long lastVolumeChangeElapsedMs, lastReactionLatencyMs;
    final ControlDecision lastDecision;
    private final float[] bandLevels;

    private RuntimeState(Builder b) {
        running=b.running; captureStatus=b.captureStatus; controlActivity=b.controlActivity;
        signalPresent=b.signalPresent; rmsDbfs=b.rmsDbfs; peakDbfs=b.peakDbfs;
        rawPeakDbfs=b.rawPeakDbfs; sourceLoudness=b.sourceLoudness;
        estimatedRmsSpl=b.estimatedRmsSpl; estimatedPeakSpl=b.estimatedPeakSpl;
        volumeIndex=b.volumeIndex; volumeMax=b.volumeMax; effectiveMaxIndex=b.effectiveMaxIndex;
        safetyLockIndex=b.safetyLockIndex; manualSafetyPause=b.manualSafetyPause;
        safetyLockEnabled=b.safetyLockEnabled; routeLabel=n(b.routeLabel); profileName=n(b.profileName);
        logStatus=n(b.logStatus); message=n(b.message); backendLabel=n(b.backendLabel);
        lastVolumeChangeElapsedMs=b.lastVolumeChangeElapsedMs;
        lastReactionLatencyMs=b.lastReactionLatencyMs; lastDecision=b.lastDecision;
        bandLevels=b.bandLevels.clone();
    }

    private static String n(String s){return s==null?"":s;}
    float[] bandLevels(){return bandLevels.clone();}

    static RuntimeState stopped(String message) {
        return new Builder().running(false).captureStatus(CaptureStatus.STOPPED)
                .controlActivity(ControlActivity.IDLE).message(message).build();
    }

    static final class Builder {
        boolean running;
        CaptureStatus captureStatus=CaptureStatus.STOPPED;
        ControlActivity controlActivity=ControlActivity.IDLE;
        boolean signalPresent;
        float rmsDbfs=DbMath.SILENCE_DBFS, peakDbfs=DbMath.SILENCE_DBFS,
                rawPeakDbfs=DbMath.SILENCE_DBFS, sourceLoudness=DbMath.SILENCE_DBFS,
                estimatedRmsSpl=Float.NaN, estimatedPeakSpl=Float.NaN;
        int volumeIndex, volumeMax, effectiveMaxIndex, safetyLockIndex;
        boolean manualSafetyPause, safetyLockEnabled;
        String routeLabel="", profileName="", logStatus="", message="Остановлено", backendLabel="";
        long lastVolumeChangeElapsedMs, lastReactionLatencyMs=-1L;
        ControlDecision lastDecision;
        float[] bandLevels=new float[5];

        Builder running(boolean v){running=v;return this;}
        Builder captureStatus(CaptureStatus v){captureStatus=v;return this;}
        Builder controlActivity(ControlActivity v){controlActivity=v;return this;}
        Builder signalPresent(boolean v){signalPresent=v;return this;}
        Builder levels(float r,float p,float er,float ep){rmsDbfs=r;peakDbfs=p;estimatedRmsSpl=er;estimatedPeakSpl=ep;return this;}
        Builder loudness(float rawPeak,float loudness){rawPeakDbfs=rawPeak;sourceLoudness=loudness;return this;}
        Builder volume(int i,int m){volumeIndex=i;volumeMax=m;return this;}
        Builder safety(boolean paused,int effectiveMax,boolean lockEnabled,int lockIndex){manualSafetyPause=paused;effectiveMaxIndex=effectiveMax;safetyLockEnabled=lockEnabled;safetyLockIndex=lockIndex;return this;}
        Builder backendLabel(String v){backendLabel=v;return this;}
        Builder reactionLatencyMs(long v){lastReactionLatencyMs=v;return this;}
        Builder routeLabel(String v){routeLabel=v;return this;}
        Builder profileName(String v){profileName=v;return this;}
        Builder logStatus(String v){logStatus=v;return this;}
        Builder message(String v){message=v;return this;}
        Builder lastVolumeChangeElapsedMs(long v){lastVolumeChangeElapsedMs=v;return this;}
        Builder lastDecision(ControlDecision v){lastDecision=v;return this;}
        Builder bandLevels(float[] v){bandLevels=v.clone();return this;}
        RuntimeState build(){return new RuntimeState(this);}
    }
}
