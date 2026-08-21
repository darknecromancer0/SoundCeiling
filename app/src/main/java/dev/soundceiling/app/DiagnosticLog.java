package dev.soundceiling.app;
final class DiagnosticLog{private static volatile SessionLogger logger;static void attach(SessionLogger l){logger=l;}static void detach(SessionLogger l){if(logger==l)logger=null;}static void event(String code,String details){SessionLogger l=logger;if(l!=null)l.event(code,details);}private DiagnosticLog(){}}
