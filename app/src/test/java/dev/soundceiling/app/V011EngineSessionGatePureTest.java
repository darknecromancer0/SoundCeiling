package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A frame may finish computing after Stop, but it cannot publish or write then. */
public final class V011EngineSessionGatePureTest {
    public static void main(String[] args) throws Exception {
        lateFrameCannotReviveStoppedState();
        oldCompletionCannotStopReplacement();
        stopSerializesWithAnAcceptedWrite();
        System.out.println("V011EngineSessionGatePureTest: PASS (3 lifecycle races)");
    }

    private static void lateFrameCannotReviveStoppedState() throws Exception {
        EngineSessionGate gate = new EngineSessionGate();
        long session = gate.start();
        List<String> effects = new ArrayList<>();
        CountDownLatch frameComputed = new CountDownLatch(1);
        CountDownLatch finishFrame = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean(true);
        Thread frame = new Thread(() -> {
            frameComputed.countDown();
            await(finishFrame);
            accepted.set(gate.runIfCurrent(session, () -> {
                effects.add("media_write");
                effects.add("running");
                effects.add("notification");
            }));
        });
        frame.start();
        await(frameComputed);
        check(gate.stopIfCurrent(session, () -> effects.add("stopped")), "Stop accepted");
        finishFrame.countDown();
        join(frame);
        check(!accepted.get(), "in-flight frame must be rejected after Stop");
        check(effects.equals(Arrays.asList("stopped")), "late frame revived state or wrote Media: " + effects);
    }

    private static void oldCompletionCannotStopReplacement() {
        EngineSessionGate gate = new EngineSessionGate();
        long old = gate.start();
        long replacement = gate.start();
        List<String> effects = new ArrayList<>();
        check(!gate.stopIfCurrent(old, () -> effects.add("old_cleanup")),
                "old worker completion must not stop a replacement");
        check(!gate.runIfCurrent(old, () -> effects.add("old_capture_install")),
                "late rebind must not install old capture");
        check(gate.runIfCurrent(replacement, () -> effects.add("replacement_running")),
                "replacement must remain active");
        check(effects.equals(Arrays.asList("replacement_running")), "stale lifecycle side effect: " + effects);
    }

    private static void stopSerializesWithAnAcceptedWrite() throws Exception {
        EngineSessionGate gate = new EngineSessionGate();
        long session = gate.start();
        List<String> effects = new ArrayList<>();
        CountDownLatch insideWrite = new CountDownLatch(1);
        CountDownLatch finishWrite = new CountDownLatch(1);
        CountDownLatch stopRequested = new CountDownLatch(1);
        Thread writer = new Thread(() -> gate.runIfCurrent(session, () -> {
            insideWrite.countDown();
            await(finishWrite);
            effects.add("media_write");
        }));
        writer.start();
        await(insideWrite);
        Thread stopper = new Thread(() -> {
            stopRequested.countDown();
            gate.stopIfCurrent(session, () -> effects.add("stopped"));
        });
        stopper.start();
        await(stopRequested);
        finishWrite.countDown();
        join(writer);
        join(stopper);
        check(effects.equals(Arrays.asList("media_write", "stopped")),
                "Stop must serialize with final write, not only precheck it: " + effects);
        check(!gate.isCurrent(session), "stopped token must stay invalid");
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(3, TimeUnit.SECONDS), "timed out waiting for race fixture"); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(3000L);
        check(!thread.isAlive(), "race fixture did not finish");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
