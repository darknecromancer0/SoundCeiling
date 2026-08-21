package dev.soundceiling.app;

enum SpeedPreset {
    FAST("fast", 80L, 3, 400L, 1),
    BALANCED("balanced", 150L, 2, 800L, 1),
    GENTLE("gentle", 250L, 1, 1400L, 1),
    CUSTOM("custom", 150L, 2, 800L, 1);
    final String key; final long decreaseIntervalMs; final int maxDecreaseStep; final long raiseIntervalMs; final int maxRaiseStep;
    SpeedPreset(String key,long decreaseIntervalMs,int maxDecreaseStep,long raiseIntervalMs,int maxRaiseStep){this.key=key;this.decreaseIntervalMs=decreaseIntervalMs;this.maxDecreaseStep=maxDecreaseStep;this.raiseIntervalMs=raiseIntervalMs;this.maxRaiseStep=maxRaiseStep;}
    static SpeedPreset fromKey(String key){for(SpeedPreset value:values())if(value.key.equals(key))return value;return BALANCED;}
}
