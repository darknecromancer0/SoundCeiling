package dev.soundceiling.app;

/** Pure UI-state contract for the approved v0.7.1 Simple/Advanced interaction. */
public final class V071SimpleModePureTest {
    public static void main(String[] args) {
        linkedDefaultKeepsBothCeilingsVisibleButDisabled();
        unlockingEnablesInPlaceWithoutChangingValues();
        fallbackFormatterUsesActualDiscreteMediaStepForBothCeilings();
        samsungUserDeltaMovesLinkedTargetButOwnedWriteDoesNot();
        helpRegistryCoversEveryVisibleDiagnosticTerm();
        globalDspDefaultAndFallbackStatusStayTruthful();
        v090PresentationNeverClaimsAudibleGlobalDsp();
        globalDspAndLinkedLockAreIndependentInAllFourStates();
        System.out.println("V071SimpleModePureTest: PASS");
    }

    private static void linkedDefaultKeepsBothCeilingsVisibleButDisabled() {
        SimpleModeModel model = SimpleModeModel.defaults(samsungCurve(), false);
        assertTrue(model.linkedChecked(), "Default Linked Lock must start ON");
        assertFalse(model.ceilingControlsEnabled(), "linked ceilings stay visible but disabled");
        assertEquals(model.lowerProgress(), model.upperProgress(), "linked progress is one point");
        assertEquals(model.lowerValueText(), model.upperValueText(), "linked display is one point");
    }

    private static void unlockingEnablesInPlaceWithoutChangingValues() {
        SimpleModeModel linked = SimpleModeModel.defaults(samsungCurve(), false);
        int lower = linked.lowerProgress();
        int upper = linked.upperProgress();
        String lowerText = linked.lowerValueText();
        String upperText = linked.upperValueText();
        SimpleModeModel unlocked = linked.withLinked(false);
        assertFalse(unlocked.linkedChecked(), "switch OFF unlocks ceilings");
        assertTrue(unlocked.ceilingControlsEnabled(), "both ceiling controls become enabled");
        assertEquals(lower, unlocked.lowerProgress(), "unlock must not reset lower value");
        assertEquals(upper, unlocked.upperProgress(), "unlock must not reset upper value");
        assertEquals(lowerText, unlocked.lowerValueText(), "unlock must not rewrite lower display");
        assertEquals(upperText, unlocked.upperValueText(), "unlock must not rewrite upper display");
    }

    private static void fallbackFormatterUsesActualDiscreteMediaStepForBothCeilings() {
        ControlVolumeCurve curve = samsungCurve();
        SimpleModeModel model = new SimpleModeModel(OutputCeilingState.of(false, -38f, -14f), curve, false);
        assertFallbackShape(model.lowerValueText(), "lower");
        assertFallbackShape(model.upperValueText(), "upper");
        OutputCeilingScale.Display lower = OutputCeilingScale.displayForPercent(model.lowerProgress(), curve, true);
        assertContains(model.lowerValueText(), "ступень " + lower.mediaIndex() + " из " + curve.maxIndex(),
                "lower must report actual Media step");
        assertContains(model.lowerValueText(), lower.mediaPercent() + "%", "lower must report snapped Media percent");
    }

    private static void samsungUserDeltaMovesLinkedTargetButOwnedWriteDoesNot() {
        ControlVolumeCurve curve = samsungCurve();
        SimpleModeModel initial = SimpleModeModel.defaults(curve, false);
        float delta = curve.deltaDb(8, 9);
        SimpleModeModel userMoved = initial.onMediaIndexChanged(8, 9, delta, false);
        assertTrue(userMoved.lowerProgress() != initial.lowerProgress(), "real Samsung user delta shifts linked displayed target");
        assertEquals(userMoved.lowerProgress(), userMoved.upperProgress(), "linked user delta moves both together");
        SimpleModeModel appMoved = initial.onMediaIndexChanged(8, 9, delta, true);
        assertEquals(initial.lowerProgress(), appMoved.lowerProgress(), "app-owned delta must not move linked user target");
        assertEquals(initial.upperProgress(), appMoved.upperProgress(), "app-owned delta must not move upper target");
    }

    private static void helpRegistryCoversEveryVisibleDiagnosticTerm() {
        String[] ids = HelpText.visibleDiagnosticTermIds();
        assertTrue(ids.length >= 16, "technical help registry must be complete, not a token subset");
        for (String id : ids) {
            String help = HelpText.forKey(id);
            assertTrue(help != null && !help.trim().isEmpty(), "missing help for " + id);
            assertContains(help, "Что это", id + " help must say what it is");
            assertContains(help, "Влияет", id + " help must say what it affects");
            assertContains(help, "Точно", id + " help must say when it is trustworthy");
        }
    }


    private static void globalDspDefaultAndFallbackStatusStayTruthful() {
        ControlVolumeCurve curve = samsungCurve();
        SimpleModeModel fresh = SimpleModeModel.defaults(curve, false);
        assertTrue(fresh.globalDspPreferred(), "Global DSP fresh/default preference must be ON");
        assertFalse(fresh.globalDspActive(), "preference ON is not the same as verified active");
        assertContains(fresh.globalDspStatusText(), "audible output blocked",
                "v0.9 preference must disclose that it is not an audible actuator");
        SimpleModeModel active = new SimpleModeModel(OutputCeilingState.defaultLinked(), curve,
                true, true, true);
        assertTrue(active.globalDspActive(), "verified global mix can become active");
        assertFalse(active.selectiveControlsEnabled(),
                "indivisible active global mix disables selective controls");
    }

    private static void v090PresentationNeverClaimsAudibleGlobalDsp() {
        ControlVolumeCurve curve = samsungCurve();
        SimpleModeModel preferred = new SimpleModeModel(
                OutputCeilingState.defaultLinked(), curve, false, true, false);
        assertContains(preferred.globalDspStatusText(), "PCM Shadow",
                "v0.9 preference must be named as a shadow evaluation");
        assertContains(preferred.globalDspStatusText(), "audible output blocked",
                "v0.9 preference must disclose the blocked audible route");

        SimpleModeModel staleHistoricalActive = new SimpleModeModel(
                OutputCeilingState.defaultLinked(), curve, true, true, true);
        assertContains(staleHistoricalActive.globalDspStatusText(), "audible output blocked",
                "historical verified state must not become an audible v0.9 claim");
        assertFalse(staleHistoricalActive.globalDspStatusText().contains("Verified global mix"),
                "v0.9 UI must not advertise the unreachable legacy global actuator");
    }

    private static void globalDspAndLinkedLockAreIndependentInAllFourStates() {
        ControlVolumeCurve curve = samsungCurve();
        for (boolean global : new boolean[]{false, true}) {
            for (boolean linked : new boolean[]{false, true}) {
                SimpleModeModel model = new SimpleModeModel(
                        OutputCeilingState.of(linked, -24f, linked ? -24f : -12f),
                        curve, global, global, global);
                assertEquals(Boolean.valueOf(linked), Boolean.valueOf(model.linkedChecked()),
                        "Linked Lock must not depend on Global DSP");
                assertEquals(Boolean.valueOf(global), Boolean.valueOf(model.globalDspPreferred()),
                        "Global DSP preference must not depend on Linked Lock");
                assertEquals(Boolean.valueOf(!linked), Boolean.valueOf(model.ceilingControlsEnabled()),
                        "both Simple and Advanced can use the same shared lock state");
            }
        }
    }
    private static void assertFallbackShape(String value, String label) {
        assertContains(value, " dB · ступень ", label + " must include dB and actual step");
        assertContains(value, " из 15 · ", label + " must include route max step");
        assertTrue(value.endsWith("%"), label + " must end with actual Media percent");
    }

    private static ControlVolumeCurve samsungCurve() {
        float[] samsungLinear = {0f, 0.0022387f, 0.003981f, 0.007079f, 0.012589f, 0.022387f,
                0.039811f, 0.070795f, 0.089125f, 0.112202f, 0.149624f,
                0.199526f, 0.281838f, 0.398107f, 0.595662f, 1f};
        return ControlVolumeCurve.fromVendorRaw(0, 15, samsungLinear);
    }

    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void assertFalse(boolean value, String message) { if (value) throw new AssertionError(message); }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    private static void assertContains(String value, String needle, String message) {
        if (value == null || !value.contains(needle)) throw new AssertionError(message + " value=" + value + " needle=" + needle);
    }
}
