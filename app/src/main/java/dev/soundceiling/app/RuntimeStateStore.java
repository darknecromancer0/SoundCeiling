package dev.soundceiling.app;
import java.util.Objects;import java.util.concurrent.atomic.AtomicReference;
final class RuntimeStateStore{private static final AtomicReference<RuntimeState>CURRENT=new AtomicReference<>(RuntimeState.stopped("Остановлено"));static RuntimeState get(){return CURRENT.get();}static void publish(RuntimeState state){CURRENT.set(Objects.requireNonNull(state));}private RuntimeStateStore(){}}
