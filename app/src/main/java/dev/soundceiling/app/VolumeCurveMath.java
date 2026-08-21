package dev.soundceiling.app;

final class VolumeCurveMath {
    private static final float MUTED=-80f,TOL=.5f,LOOKUP=.20f;
    static final class ValidationResult{final float[] gains;final boolean fallbackUsed;final String reason;ValidationResult(float[] g,boolean f,String r){gains=g.clone();fallbackUsed=f;reason=r;}}
    static ValidationResult validate(float[] raw,int min,int max){int count=max-min+1;if(count<=0||raw==null||raw.length!=count)return new ValidationResult(fallback(min,max),true,"shape_mismatch");float[] g=new float[count];boolean nonFinite=false,nonMono=false,different=false;float first=Float.NaN,prev=Float.NEGATIVE_INFINITY;for(int o=0;o<count;o++){float r=raw[o],comp;if(r==Float.NEGATIVE_INFINITY){g[o]=MUTED;comp=Float.NEGATIVE_INFINITY;}else if(!Float.isFinite(r)){nonFinite=true;g[o]=MUTED;comp=prev;}else{g[o]=r;comp=r;if(Float.isNaN(first))first=r;else if(r!=first)different=true;}if(o>0&&comp+TOL<prev)nonMono=true;prev=comp;}if(nonFinite)return new ValidationResult(fallback(min,max),true,"non_finite");if(nonMono)return new ValidationResult(fallback(min,max),true,"non_monotonic");if(!different)return new ValidationResult(fallback(min,max),true,"flat_or_single_finite");return new ValidationResult(g,false,"valid");}
    static float[] validatedGains(float[] raw,int min,int max){return validate(raw,min,max).gains.clone();}
    static int bestIndexAtOrBelowGain(float[] gains,int min,int cap,float desired){int max=min+gains.length-1;cap=DbMath.clamp(cap,min,max);int best=min;float err=Float.MAX_VALUE;for(int i=min;i<=cap;i++){float g=gains[i-min];if(g<=desired+LOOKUP){float e=Math.abs(desired-g);if(e<err){err=e;best=i;}}}return best;}
    private static float[] fallback(int min,int max){int count=Math.max(0,max-min+1);float[] g=new float[count];for(int o=0;o<count;o++)g[o]=o==0?MUTED:(float)(20.0*Math.log10(o/(float)Math.max(1,count-1)));return g;}private VolumeCurveMath(){}
}
