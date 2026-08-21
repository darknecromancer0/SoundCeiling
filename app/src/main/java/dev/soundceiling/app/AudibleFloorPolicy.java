package dev.soundceiling.app;

final class AudibleFloorPolicy {
    static Result apply(int desiredIndex,int minIndex,int capIndex,boolean signalPresent,boolean allowAutoMute){int clamped=DbMath.clamp(desiredIndex,minIndex,capIndex);int audible=Math.min(capIndex,minIndex+1);if(signalPresent&&!allowAutoMute&&capIndex>=minIndex+1&&clamped<audible)return new Result(audible,ControlDecision.SafetyReason.AUDIBLE_FLOOR);return new Result(clamped,clamped==capIndex&&desiredIndex>capIndex?ControlDecision.SafetyReason.MAXIMUM_CAP:ControlDecision.SafetyReason.NONE);}
    static final class Result{final int index;final ControlDecision.SafetyReason reason;Result(int index,ControlDecision.SafetyReason reason){this.index=index;this.reason=reason;}}
    private AudibleFloorPolicy(){}
}
