package dev.soundceiling.app;

final class ToneSamples {
    static short[] sinePcm16(int sampleRate,int durationMs,float frequencyHz,float peakDbfs,int channels){if(sampleRate<=0||durationMs<=0||frequencyHz<=0f||channels<=0)throw new IllegalArgumentException("positive tone parameters required");int frames=Math.multiplyExact(sampleRate,durationMs)/1000;short[] pcm=new short[Math.multiplyExact(frames,channels)];double scale=32767.0*Math.pow(10.0,peakDbfs/20.0);for(int frame=0;frame<frames;frame++){int value=(int)Math.round(Math.sin(2.0*Math.PI*frequencyHz*frame/sampleRate)*scale);short sample=(short)DbMath.clamp(value,Short.MIN_VALUE,Short.MAX_VALUE);for(int channel=0;channel<channels;channel++)pcm[frame*channels+channel]=sample;}return pcm;}
    private ToneSamples(){}
}
