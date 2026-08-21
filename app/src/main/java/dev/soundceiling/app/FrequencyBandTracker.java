package dev.soundceiling.app;

final class FrequencyBandTracker {
    private static final float[] CENTERS={80f,250f,1000f,4000f,10000f};private final int sampleRate,channelCount;private final float[] levels=new float[CENTERS.length];
    FrequencyBandTracker(int sampleRate,int channelCount){if(sampleRate<=0||channelCount<=0)throw new IllegalArgumentException("positive format required");this.sampleRate=sampleRate;this.channelCount=channelCount;}
    static float[] centerFrequencies(){return CENTERS.clone();}float[] levels(){return levels.clone();}
    float[] update(short[] pcm,int sampleCount){int count=DbMath.clamp(sampleCount,0,pcm.length),frames=count/channelCount;if(frames<2)return levels();for(int band=0;band<CENTERS.length;band++){double coefficient=2.0*Math.cos(2.0*Math.PI*CENTERS[band]/sampleRate),previous=0,previous2=0;for(int frame=0;frame<frames;frame++){double mono=0;for(int channel=0;channel<channelCount;channel++)mono+=pcm[frame*channelCount+channel]/32768.0;mono/=channelCount;double window=.5-.5*Math.cos(2.0*Math.PI*frame/(frames-1));double current=mono*window+coefficient*previous-previous2;previous2=previous;previous=current;}double power=previous*previous+previous2*previous2-coefficient*previous*previous2;double amplitude=2.0*Math.sqrt(Math.max(0,power))/frames;float dbfs=(float)(20.0*Math.log10(Math.max(.0001,amplitude)));float normalized=DbMath.clamp((dbfs+80f)/80f,0f,1f);levels[band]=levels[band]*.65f+normalized*.35f;}return levels();}
}
