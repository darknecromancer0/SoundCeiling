package dev.soundceiling.app;

import java.util.List;

final class RuntimeState {
    enum CaptureStatus { STOPPED, STARTING, RUNNING, WAITING_SIGNAL, ERROR }
    enum ControlActivity { IDLE, HOLDING, DECREASING, RECOVERING, MINIMUM_LIMIT, MAXIMUM_LIMIT, ERROR }

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

    // Controller telemetry. Configured values come from resolved policy; effective values include
    // the current non-positive manual dB offset from the user-authority envelope.
    final float configuredTargetLoudness, effectiveTargetLoudness;
    final float configuredPeakThresholdDbfs, effectivePeakThresholdDbfs;
    final float manualThresholdOffsetDb;
    final long meterAgeMs, lastEmergencyLatencyMs;
    final String lastControllerAction, lastControllerReason;
    final boolean unexpectedZero;

    // v0.7 adaptive-envelope authority telemetry.
    final int userCeilingIndex, safetyCeilingIndex, recoverableCeilingIndex;
    final float automaticAttenuationDb;

    // v0.5 independent Hybrid Engine dimensions. Defaults deliberately describe uncertainty.
    final PcmAvailabilityState pcmState;
    final EngineCapabilities.SourceIdentityConfidence sourceConfidence;
    final EngineCapabilities.MeteringCapability meteringCapability;
    final EngineCapabilities.VolumeControlCapability volumeControlCapability;
    final EngineCapabilities.DspTransportCapability dspTransportCapability;
    final String sourcePackage, sourceLabel, appRuleLabel, downgradeReason;
    final CaptureRequestCoordinator.SourceAccessState sourceAccessState;

    // v0.7.1 coordinator truth: selected actuator, verified capability and pure decision inputs.
    final String controlActuator, captureReferenceMode, directionDwell;
    final boolean controlCapabilityVerified, linkedCeilings, programActive;
    final float desiredGainDb, appliedGainDb, projectedPeakDbfs, controlLoudnessDb;
    final float lowerOutputCeilingDb, upperOutputCeilingDb, routeStepGainDb;

    // v0.7.7 Enhanced Session DSP setup and bound-session telemetry.
    final boolean enhancedSessionPermissionGranted, sessionDspActive;
    final int sessionId, sessionUid;
    final String sessionPackage, sessionDspReason;
    final float sessionDspRequestedGainDb, sessionDspAppliedGainDb;

    // v0.9 public playback-capture feasibility and non-audible PCM shadow telemetry.
    final String pcmDspMode, pcmDspReason, pcmShadowReason;
    final boolean pcmDspAudibleOutputAllowed, pcmShadowActive;
    final float pcmShadowRequestedGainDb, pcmShadowAppliedGainDb;
    final float pcmShadowProjectedPeakDbfs, pcmShadowPcmPeakDbfs;
    final int pcmShadowClippedSamples;

    // v0.9.1 experimental Accessibility Relay telemetry. This is separate from Shadow truth:
    // only relayAudible may describe PCM that reached an audible renderer.
    final long relayEpoch;
    final String relayState, relayReason;
    final boolean relayAudible, relayFullExperimental,
            relayRecoveryRequired;
    final int relayVolumeIndex, relayVolumeHardMaximum;
    final float relayRequestedGainDb, relayAppliedGainDb,
            relayOutputPeakDbfs;
    final long relayLatencyMs, relayProbeRemainingMs;

    private final float[] bandLevels;
    private final DiagnosticItem[] diagnostics;

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
        configuredTargetLoudness=b.configuredTargetLoudness;
        effectiveTargetLoudness=b.effectiveTargetLoudness;
        configuredPeakThresholdDbfs=b.configuredPeakThresholdDbfs;
        effectivePeakThresholdDbfs=b.effectivePeakThresholdDbfs;
        manualThresholdOffsetDb=b.manualThresholdOffsetDb;
        meterAgeMs=Math.max(0L,b.meterAgeMs);
        lastEmergencyLatencyMs=b.lastEmergencyLatencyMs;
        lastControllerAction=n(b.lastControllerAction);
        lastControllerReason=n(b.lastControllerReason);
        unexpectedZero=b.unexpectedZero;
        userCeilingIndex=Math.max(0,b.userCeilingIndex);
        safetyCeilingIndex=Math.max(0,b.safetyCeilingIndex);
        recoverableCeilingIndex=Math.max(0,b.recoverableCeilingIndex);
        automaticAttenuationDb=Math.min(0f,b.automaticAttenuationDb);
        pcmState=b.pcmState; sourceConfidence=b.sourceConfidence;
        meteringCapability=b.meteringCapability; volumeControlCapability=b.volumeControlCapability;
        dspTransportCapability=b.dspTransportCapability; sourcePackage=n(b.sourcePackage);
        sourceLabel=n(b.sourceLabel); appRuleLabel=n(b.appRuleLabel); downgradeReason=n(b.downgradeReason);
        sourceAccessState=b.sourceAccessState;
        controlActuator=n(b.controlActuator); captureReferenceMode=n(b.captureReferenceMode);
        directionDwell=n(b.directionDwell); controlCapabilityVerified=b.controlCapabilityVerified;
        linkedCeilings=b.linkedCeilings; programActive=b.programActive;
        desiredGainDb=b.desiredGainDb; appliedGainDb=b.appliedGainDb;
        projectedPeakDbfs=b.projectedPeakDbfs; controlLoudnessDb=b.controlLoudnessDb;
        lowerOutputCeilingDb=b.lowerOutputCeilingDb; upperOutputCeilingDb=b.upperOutputCeilingDb;
        routeStepGainDb=b.routeStepGainDb;
        enhancedSessionPermissionGranted=b.enhancedSessionPermissionGranted;
        sessionId=b.sessionId > 0 ? b.sessionId : -1;
        sessionUid=b.sessionUid > 0 ? b.sessionUid : -1;
        sessionDspActive=b.sessionDspActive && sessionId > 0;
        sessionPackage=n(b.sessionPackage); sessionDspReason=n(b.sessionDspReason);
        sessionDspRequestedGainDb=Float.isFinite(b.sessionDspRequestedGainDb)
                ? b.sessionDspRequestedGainDb : 0f;
        sessionDspAppliedGainDb=Float.isFinite(b.sessionDspAppliedGainDb)
                ? b.sessionDspAppliedGainDb : 0f;
        pcmDspMode=n(b.pcmDspMode); pcmDspReason=n(b.pcmDspReason);
        pcmShadowReason=n(b.pcmShadowReason);
        pcmDspAudibleOutputAllowed=b.pcmDspAudibleOutputAllowed;
        pcmShadowActive=b.pcmShadowActive;
        pcmShadowRequestedGainDb=finite(b.pcmShadowRequestedGainDb);
        pcmShadowAppliedGainDb=finite(b.pcmShadowAppliedGainDb);
        pcmShadowProjectedPeakDbfs=b.pcmShadowProjectedPeakDbfs;
        pcmShadowPcmPeakDbfs=b.pcmShadowPcmPeakDbfs;
        pcmShadowClippedSamples=Math.max(0,b.pcmShadowClippedSamples);
        relayEpoch=Math.max(0L,b.relayEpoch);
        relayState=n(b.relayState).isEmpty()?"OFF":n(b.relayState);
        relayReason=n(b.relayReason).isEmpty()?"relay_off":n(b.relayReason);
        relayAudible=b.relayAudible && "ACTIVE".equals(relayState);
        relayFullExperimental=b.relayFullExperimental && relayAudible;
        relayRecoveryRequired=b.relayRecoveryRequired
                || "RECOVERY_REQUIRED".equals(relayState);
        relayVolumeIndex=Math.max(0,b.relayVolumeIndex);
        relayVolumeHardMaximum=Math.max(0,b.relayVolumeHardMaximum);
        relayRequestedGainDb=finite(b.relayRequestedGainDb);
        relayAppliedGainDb=finite(b.relayAppliedGainDb);
        relayOutputPeakDbfs=b.relayOutputPeakDbfs;
        relayLatencyMs=b.relayLatencyMs < 0L ? -1L : b.relayLatencyMs;
        relayProbeRemainingMs=Math.max(0L,b.relayProbeRemainingMs);
        bandLevels=b.bandLevels.clone(); diagnostics=b.diagnostics.clone();
    }

    private static String n(String s){return s==null?"":s;}
    private static float finite(float value){return Float.isFinite(value)?value:0f;}
    float[] bandLevels(){return bandLevels.clone();}
    DiagnosticItem[] diagnostics(){return diagnostics.clone();}

    RuntimeState withDiagnostics(List<DiagnosticItem> items) {
        return copyWithDiagnostics(items, meterAgeMs);
    }

    RuntimeState withDiagnostics(List<DiagnosticItem> items, long currentMeterAgeMs) {
        return copyWithDiagnostics(items, currentMeterAgeMs);
    }

    private RuntimeState copyWithDiagnostics(List<DiagnosticItem> items, long currentMeterAgeMs) {
        return copyBuilder().meterAgeMs(currentMeterAgeMs)
                .diagnostics(items).build();
    }

    RuntimeState withRelay(long epoch, String state, String reason,
            boolean audible, boolean full, boolean recovery, int volume,
            int hardMaximum, float requestedGain, float appliedGain,
            float outputPeak, long latencyMs, long probeRemainingMs) {
        return copyBuilder().relay(epoch, state, reason, audible, full,
                recovery, volume, hardMaximum, requestedGain, appliedGain,
                outputPeak, latencyMs, probeRemainingMs).build();
    }

    private Builder copyBuilder() {
        return new Builder()
                .running(running).captureStatus(captureStatus).controlActivity(controlActivity)
                .signalPresent(signalPresent).levels(rmsDbfs, peakDbfs, estimatedRmsSpl, estimatedPeakSpl)
                .loudness(rawPeakDbfs, sourceLoudness).volume(volumeIndex, volumeMax)
                .safety(manualSafetyPause, effectiveMaxIndex, safetyLockEnabled, safetyLockIndex)
                .backendLabel(backendLabel).reactionLatencyMs(lastReactionLatencyMs)
                .routeLabel(routeLabel).profileName(profileName).logStatus(logStatus).message(message)
                .lastVolumeChangeElapsedMs(lastVolumeChangeElapsedMs).lastDecision(lastDecision)
                .thresholds(configuredTargetLoudness, effectiveTargetLoudness,
                        configuredPeakThresholdDbfs, effectivePeakThresholdDbfs, manualThresholdOffsetDb)
                .envelope(userCeilingIndex, safetyCeilingIndex, recoverableCeilingIndex,
                        automaticAttenuationDb)
                .controller(lastControllerAction, lastControllerReason,
                        lastReactionLatencyMs, lastEmergencyLatencyMs)
                .meterAgeMs(meterAgeMs).unexpectedZero(unexpectedZero)
                .hybrid(pcmState, sourceConfidence, meteringCapability, volumeControlCapability,
                        dspTransportCapability, sourcePackage, sourceLabel, appRuleLabel, downgradeReason)
                .sourceAccessState(sourceAccessState)
                .coordinator(controlActuator, controlCapabilityVerified, desiredGainDb, appliedGainDb,
                        projectedPeakDbfs, controlLoudnessDb, captureReferenceMode, linkedCeilings,
                        lowerOutputCeilingDb, upperOutputCeilingDb, routeStepGainDb, programActive,
                        directionDwell)
                .enhancedSession(enhancedSessionPermissionGranted, sessionDspActive, sessionId,
                        sessionUid, sessionPackage, sessionDspRequestedGainDb,
                        sessionDspAppliedGainDb, sessionDspReason)
                .pcmDsp(pcmDspMode, pcmDspReason, pcmDspAudibleOutputAllowed, pcmShadowActive,
                        pcmShadowRequestedGainDb, pcmShadowAppliedGainDb,
                        pcmShadowProjectedPeakDbfs, pcmShadowPcmPeakDbfs,
                        pcmShadowClippedSamples, pcmShadowReason)
                .relay(relayEpoch, relayState, relayReason, relayAudible,
                        relayFullExperimental, relayRecoveryRequired,
                        relayVolumeIndex, relayVolumeHardMaximum,
                        relayRequestedGainDb, relayAppliedGainDb,
                        relayOutputPeakDbfs, relayLatencyMs,
                        relayProbeRemainingMs)
                .bandLevels(bandLevels).diagnostics(diagnostics);
    }

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
        float configuredTargetLoudness=Float.NaN, effectiveTargetLoudness=Float.NaN;
        float configuredPeakThresholdDbfs=Float.NaN, effectivePeakThresholdDbfs=Float.NaN;
        float manualThresholdOffsetDb;
        long meterAgeMs, lastEmergencyLatencyMs=-1L;
        String lastControllerAction="", lastControllerReason="";
        boolean unexpectedZero;
        int userCeilingIndex, safetyCeilingIndex, recoverableCeilingIndex;
        float automaticAttenuationDb;
        PcmAvailabilityState pcmState=PcmAvailabilityState.IDLE;
        EngineCapabilities.SourceIdentityConfidence sourceConfidence=EngineCapabilities.SourceIdentityConfidence.UNKNOWN;
        EngineCapabilities.MeteringCapability meteringCapability=EngineCapabilities.MeteringCapability.NONE;
        EngineCapabilities.VolumeControlCapability volumeControlCapability=EngineCapabilities.VolumeControlCapability.NONE;
        EngineCapabilities.DspTransportCapability dspTransportCapability=EngineCapabilities.DspTransportCapability.UNAVAILABLE;
        String sourcePackage="", sourceLabel="", appRuleLabel="Global", downgradeReason="";
        CaptureRequestCoordinator.SourceAccessState sourceAccessState=CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING;
        String controlActuator="NONE", captureReferenceMode="UNKNOWN", directionDwell="idle";
        boolean controlCapabilityVerified, linkedCeilings, programActive;
        float desiredGainDb, appliedGainDb, projectedPeakDbfs=Float.NaN, controlLoudnessDb=Float.NaN;
        float lowerOutputCeilingDb=OutputCeilingState.DEFAULT_DB;
        float upperOutputCeilingDb=OutputCeilingState.DEFAULT_DB, routeStepGainDb;
        boolean enhancedSessionPermissionGranted, sessionDspActive;
        int sessionId=-1, sessionUid=-1;
        String sessionPackage="", sessionDspReason="not_discovered";
        float sessionDspRequestedGainDb, sessionDspAppliedGainDb;
        String pcmDspMode="SHADOW_ONLY";
        String pcmDspReason="public_playback_capture_keeps_original_audio";
        String pcmShadowReason="not_started";
        boolean pcmDspAudibleOutputAllowed, pcmShadowActive;
        float pcmShadowRequestedGainDb, pcmShadowAppliedGainDb;
        float pcmShadowProjectedPeakDbfs=Float.NaN, pcmShadowPcmPeakDbfs=Float.NaN;
        int pcmShadowClippedSamples;
        long relayEpoch;
        String relayState="OFF", relayReason="relay_off";
        boolean relayAudible, relayFullExperimental,
                relayRecoveryRequired;
        int relayVolumeIndex, relayVolumeHardMaximum;
        float relayRequestedGainDb, relayAppliedGainDb;
        float relayOutputPeakDbfs=Float.NaN;
        long relayLatencyMs=-1L, relayProbeRemainingMs;
        float[] bandLevels=new float[5];
        DiagnosticItem[] diagnostics=new DiagnosticItem[0];

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
        Builder thresholds(float configuredTarget, float effectiveTarget,
                           float configuredPeak, float effectivePeak, float manualOffset) {
            configuredTargetLoudness=configuredTarget; effectiveTargetLoudness=effectiveTarget;
            configuredPeakThresholdDbfs=configuredPeak; effectivePeakThresholdDbfs=effectivePeak;
            manualThresholdOffsetDb=Math.min(0f, manualOffset); return this;
        }
        Builder envelope(int userCeiling, int safetyCeiling, int recoverableCeiling,
                         float autoAttenuationDb) {
            userCeilingIndex=Math.max(0,userCeiling);
            safetyCeilingIndex=Math.max(0,safetyCeiling);
            recoverableCeilingIndex=Math.max(0,recoverableCeiling);
            automaticAttenuationDb=Math.min(0f,autoAttenuationDb);
            return this;
        }
        Builder controller(String action, String reason, long reactionLatency, long emergencyLatency) {
            lastControllerAction=action; lastControllerReason=reason;
            lastReactionLatencyMs=reactionLatency; lastEmergencyLatencyMs=emergencyLatency; return this;
        }
        Builder meterAgeMs(long v){meterAgeMs=Math.max(0L,v);return this;}
        Builder unexpectedZero(boolean v){unexpectedZero=v;return this;}
        Builder hybrid(PcmAvailabilityState pcm,
                       EngineCapabilities.SourceIdentityConfidence confidence,
                       EngineCapabilities.MeteringCapability metering,
                       EngineCapabilities.VolumeControlCapability control,
                       EngineCapabilities.DspTransportCapability dsp,
                       String pkg, String label, String appRule, String downgrade) {
            pcmState=pcm==null?PcmAvailabilityState.UNCERTAIN:pcm;
            sourceConfidence=confidence==null?EngineCapabilities.SourceIdentityConfidence.UNKNOWN:confidence;
            meteringCapability=metering==null?EngineCapabilities.MeteringCapability.NONE:metering;
            volumeControlCapability=control==null?EngineCapabilities.VolumeControlCapability.NONE:control;
            dspTransportCapability=dsp==null?EngineCapabilities.DspTransportCapability.UNAVAILABLE:dsp;
            sourcePackage=pkg;sourceLabel=label;appRuleLabel=appRule;downgradeReason=downgrade;return this;
        }
        Builder sourceAccessState(CaptureRequestCoordinator.SourceAccessState value) {
            sourceAccessState=value==null?CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING:value;
            return this;
        }
        Builder coordinator(String actuator, boolean verified, float desiredGain, float appliedGain,
                            float projectedPeak, float loudness, String captureMode, boolean linked,
                            float lowerCeiling, float upperCeiling, float routeStepGain,
                            boolean active, String dwell) {
            controlActuator=actuator; controlCapabilityVerified=verified;
            desiredGainDb=desiredGain; appliedGainDb=appliedGain; projectedPeakDbfs=projectedPeak;
            controlLoudnessDb=loudness; captureReferenceMode=captureMode; linkedCeilings=linked;
            lowerOutputCeilingDb=lowerCeiling; upperOutputCeilingDb=upperCeiling;
            routeStepGainDb=routeStepGain; programActive=active; directionDwell=dwell; return this;
        }
        Builder enhancedSession(boolean permissionGranted, boolean active, int id, int uid,
                                String pkg, float requestedGainDb, float appliedGainDb,
                                String reason) {
            enhancedSessionPermissionGranted=permissionGranted; sessionDspActive=active;
            sessionId=id; sessionUid=uid; sessionPackage=pkg;
            sessionDspRequestedGainDb=requestedGainDb; sessionDspAppliedGainDb=appliedGainDb;
            sessionDspReason=reason; return this;
        }
        Builder pcmDsp(String mode, String reason, boolean audibleOutputAllowed,
                       boolean shadowActive, float requestedGainDb, float appliedGainDb,
                       float projectedPeakDbfs, float shadowPcmPeakDbfs,
                       int clippedSamples, String shadowReason) {
            pcmDspMode=mode; pcmDspReason=reason;
            pcmDspAudibleOutputAllowed=audibleOutputAllowed; pcmShadowActive=shadowActive;
            pcmShadowRequestedGainDb=requestedGainDb; pcmShadowAppliedGainDb=appliedGainDb;
            pcmShadowProjectedPeakDbfs=projectedPeakDbfs;
            pcmShadowPcmPeakDbfs=shadowPcmPeakDbfs;
            pcmShadowClippedSamples=clippedSamples; pcmShadowReason=shadowReason; return this;
        }
        Builder relay(long epoch, String state, String reason,
                      boolean audible, boolean full, boolean recovery,
                      int volume, int hardMaximum, float requestedGain,
                      float appliedGain, float outputPeak, long latencyMs,
                      long probeRemainingMs) {
            relayEpoch=Math.max(0L,epoch);
            relayState=state==null?"OFF":state;
            relayReason=reason==null?"relay_off":reason;
            relayAudible=audible && "ACTIVE".equals(relayState);
            relayFullExperimental=full && relayAudible;
            relayRecoveryRequired=recovery
                    || "RECOVERY_REQUIRED".equals(relayState);
            relayVolumeIndex=Math.max(0,volume);
            relayVolumeHardMaximum=Math.max(0,hardMaximum);
            relayRequestedGainDb=Float.isFinite(requestedGain)
                    ? requestedGain : 0f;
            relayAppliedGainDb=Float.isFinite(appliedGain)
                    ? appliedGain : 0f;
            relayOutputPeakDbfs=outputPeak;
            relayLatencyMs=latencyMs < 0L ? -1L : latencyMs;
            relayProbeRemainingMs=Math.max(0L,probeRemainingMs);
            return this;
        }
        Builder bandLevels(float[] v){bandLevels=v==null?new float[5]:v.clone();return this;}
        Builder diagnostics(List<DiagnosticItem> v){diagnostics=v==null?new DiagnosticItem[0]:v.toArray(new DiagnosticItem[0]);return this;}
        Builder diagnostics(DiagnosticItem[] v){diagnostics=v==null?new DiagnosticItem[0]:v.clone();return this;}
        RuntimeState build(){return new RuntimeState(this);}
    }
}
