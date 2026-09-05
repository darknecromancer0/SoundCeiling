package dev.soundceiling.app;

public final class V091RelayLeaseVolumePureTest {
    public static void main(String[] args) {
        mediaZeroNeedsStableOwnedAcknowledgement();
        unavailableMediaReadingNeverLooksLikeUserIntent();
        muteBudgetAndTimeoutFailClosed();
        invalidLeaseCannotBeCreated();
        legacyRecoverySchemaRemainsExplicitlyRestorable();
        accessibilityRestoreRequiresOwnedWrite();
        userAccessibilityChangesRevokeRestoration();
        accessibilityRestoreNeverRaisesVolume();
        volumePolicyUsesOneHardClamp();
        keyPolicyKeepsDownImmediateAndBlocksUnsafeUp();
        relayConsumesCompleteHardwareKeyPairs();
        volumeDownAtMinimumNeverWritesUpward();
        awaitingConfirmationAndOffKeepTheirAuthorities();
        relayDoesNotOwnUnrelatedHardwareKeys();
        System.out.println("V091RelayLeaseVolumePureTest: PASS");
    }

    private static void mediaZeroNeedsStableOwnedAcknowledgement() {
        RelayMediaLease lease = RelayMediaLease.begin(9L, 8, 3, 4, 1000L);
        eq(RelayMediaLease.MuteAction.WRITE_ZERO,
                lease.nextMuteAction(1000L).action, "first mute write");
        lease.noteMuteWrite(1000L);
        eq(RelayMediaLease.MuteAction.WAIT,
                lease.observeMedia(0, 1050L).action,
                "zero is not stable for 100 ms");
        eq(RelayMediaLease.MuteAction.ACKNOWLEDGED,
                lease.observeMedia(0, 1150L).action,
                "stable zero acknowledgement");
        eq(RelayMediaLease.MuteAction.USER_EXIT,
                lease.observeMedia(7, 1160L).action,
                "non-owned Samsung panel write exits");
        require(!lease.mayRestoreMedia(7),
                "user Media must never be overwritten");
        eq(5, lease.restoreMediaTarget(5),
                "restore is bounded by current Safety Maximum");
    }

    private static void muteBudgetAndTimeoutFailClosed() {
        RelayMediaLease exhausted = RelayMediaLease.begin(10L, 8, 3, 4, 2000L);
        for (int i = 0; i < 3; i++) {
            exhausted.noteMuteWrite(2000L + i * 20L);
        }
        eq(RelayMediaLease.MuteAction.FAILED,
                exhausted.nextMuteAction(2060L).action,
                "three unacknowledged writes fail closed");

        RelayMediaLease timedOut = RelayMediaLease.begin(11L, 8, 3, 4, 3000L);
        eq(RelayMediaLease.MuteAction.FAILED,
                timedOut.nextMuteAction(3501L).action,
                "500 ms window is bounded");
        eq(RelayMediaLease.MuteAction.FAILED,
                timedOut.observeMedia(0, 3501L).action,
                "late zero observation cannot bypass the timeout");
    }

    private static void unavailableMediaReadingNeverLooksLikeUserIntent() {
        RelayMediaLease lease = RelayMediaLease.begin(
                13L, 8, 3, 4, 5000L);
        lease.noteMuteWrite(5000L);
        lease.observeMedia(0, 5050L);
        eq(RelayMediaLease.MuteAction.ACKNOWLEDGED,
                lease.observeMedia(0, 5150L).action,
                "fixture owns stable Media zero");
        RelayMediaLease.Decision unavailable =
                lease.observeMedia(-1, 5160L);
        eq(RelayMediaLease.MuteAction.FAILED, unavailable.action,
                "unknown Media cannot be classified as a user panel exit");
        eq("relay_media_volume_unavailable", unavailable.reason,
                "unknown Media keeps a machine-stable recovery reason");
    }

    private static void invalidLeaseCannotBeCreated() {
        boolean rejected = false;
        try {
            RelayMediaLease.begin(0L, 8, 3, 4, 1000L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "zero epoch cannot own stream state");
    }

    private static void legacyRecoverySchemaRemainsExplicitlyRestorable() {
        RelayGenerationToken valid = new RelayGenerationToken(
                1L, 2L, 3L, 4L, 5L);
        eq(RelayRecoveryGenerationPolicy.Schema.LEGACY,
                RelayRecoveryGenerationPolicy.classify(
                        false, false, false, false, false, null),
                "a complete pre-generation record remains explicit recovery data");
        eq(RelayRecoveryGenerationPolicy.Schema.CURRENT,
                RelayRecoveryGenerationPolicy.classify(
                        true, true, true, true, true, valid),
                "a complete independent generation tuple is current");
        eq(RelayRecoveryGenerationPolicy.Schema.INVALID,
                RelayRecoveryGenerationPolicy.classify(
                        true, false, false, false, false, null),
                "a partially written generation tuple stays fail-closed");
        eq(RelayRecoveryGenerationPolicy.Schema.INVALID,
                RelayRecoveryGenerationPolicy.classify(
                        true, true, true, true, true,
                        new RelayGenerationToken(1L, 2L, 0L, 4L, 5L)),
                "a current record with an invalid generation cannot resume");
    }

    private static void accessibilityRestoreRequiresOwnedWrite() {
        RelayMediaLease lease = RelayMediaLease.begin(12L, 8, 3, 4, 4000L);
        require(!lease.mayRestoreAccessibility(4),
                "saved Accessibility value alone is not app ownership");
        require(!lease.record().accessibilityValueOwned,
                "recovery record starts without Accessibility ownership");
        lease.noteAccessibilityWrite(2);
        require(lease.mayRestoreAccessibility(2),
                "owned Accessibility value may restore");
        require(!lease.mayRestoreAccessibility(3),
                "external Accessibility change is preserved");
        RelayMediaLease.Record record = lease.record();
        eq(12L, record.epoch, "record keeps epoch");
        eq(8, record.preMediaIndex, "record keeps pre-Relay Media");
        eq(3, record.capturedSafetyMaxIndex, "record keeps captured Safety Maximum");
        eq(4, record.preAccessibilityIndex,
                "record keeps pre-Relay Accessibility");
        eq(2, record.lastOwnedAccessibilityIndex,
                "record keeps the last owned Accessibility write");
        require(record.accessibilityValueOwned,
                "recovery record persists proven Accessibility ownership");
    }

    private static void userAccessibilityChangesRevokeRestoration() {
        RelayMediaLease lease = RelayMediaLease.begin(14L, 8, 3, 4, 6000L);
        lease.noteAccessibilityWrite(2);
        lease.revokeAccessibilityRestore();
        require(!lease.record().accessibilityValueOwned,
                "hardware-key or slider input revokes app restoration ownership");
        require(!lease.mayRestoreAccessibility(2),
                "revoked ownership cannot repay a user Volume Down");
    }

    private static void accessibilityRestoreNeverRaisesVolume() {
        RelayMediaLease lease = RelayMediaLease.begin(15L, 8, 3, 4, 7000L);
        lease.noteAccessibilityWrite(2);
        eq(2, lease.restoreAccessibilityTarget(2, 0, 3),
                "cleanup cannot raise Accessibility toward the saved value");
        eq(1, lease.restoreAccessibilityTarget(2, 0, 1),
                "cleanup still obeys the current hard maximum");
    }

    private static void volumePolicyUsesOneHardClamp() {
        eq(3, RelayVolumePolicy.hardMaxIndex(0, 15, 20),
                "percentage maps with floor");
        eq(1, RelayVolumePolicy.probeIndex(0, 15, 3),
                "probe uses minimum audible step");
        eq(0, RelayVolumePolicy.hardMaxIndex(15, 0, -5),
                "malformed range and percent clamp");
        eq(15, RelayVolumePolicy.hardMaxIndex(15, 0, 105),
                "upper malformed range clamps");
        eq(3, RelayVolumePolicy.clampRequestedIndex(8, 0, 3),
                "UI request shares hard maximum");
        eq(0, RelayVolumePolicy.clampRequestedIndex(-4, 0, 3),
                "UI request shares lower bound");
    }

    private static void keyPolicyKeepsDownImmediateAndBlocksUnsafeUp() {
        eq(2, RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.ACTIVE,
                RelayVolumePolicy.KEY_VOLUME_DOWN, RelayVolumePolicy.ACTION_DOWN,
                3, 0, 3).targetIndex, "Volume Down is immediate");
        eq(3, RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.ACTIVE,
                RelayVolumePolicy.KEY_VOLUME_UP, RelayVolumePolicy.ACTION_DOWN,
                3, 0, 3).targetIndex, "Volume Up holds at hard maximum");
        RelayVolumePolicy.Decision blocked = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.PROBE, RelayVolumePolicy.KEY_VOLUME_UP,
                RelayVolumePolicy.ACTION_DOWN, 1, 0, 3);
        eq(1, blocked.targetIndex, "probe blocks Volume Up");
        require(blocked.consume && !blocked.write,
                "blocked probe Up is consumed without a stream write");
        RelayVolumePolicy.Decision released = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.ACTIVE, RelayVolumePolicy.KEY_VOLUME_DOWN,
                RelayVolumePolicy.ACTION_UP, 2, 0, 3);
        require(released.consume && !released.write,
                "owned key release is consumed without a second step");
        require(!RelayVolumePolicy.onKey(RelayVolumePolicy.Phase.OFF,
                RelayVolumePolicy.KEY_VOLUME_DOWN, RelayVolumePolicy.ACTION_DOWN,
                2, 0, 3).consume, "keys pass through while Relay is off");
    }

    private static void relayConsumesCompleteHardwareKeyPairs() {
        int[] keys = {
                RelayVolumePolicy.KEY_VOLUME_UP,
                RelayVolumePolicy.KEY_VOLUME_DOWN
        };
        for (int key : keys) {
            RelayVolumePolicy.Decision down = RelayVolumePolicy.onKey(
                    RelayVolumePolicy.Phase.ACTIVE, key,
                    RelayVolumePolicy.ACTION_DOWN, 2, 0, 3);
            require(down.consume, "Relay consumes owned key down");
            RelayVolumePolicy.Decision up = RelayVolumePolicy.onKey(
                    RelayVolumePolicy.Phase.ACTIVE, key,
                    RelayVolumePolicy.ACTION_UP, 2, 0, 3);
            require(up.consume && !up.write,
                    "Relay consumes key up without a second write");
        }
    }

    private static void volumeDownAtMinimumNeverWritesUpward() {
        RelayVolumePolicy.Decision atMinimum = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.ACTIVE,
                RelayVolumePolicy.KEY_VOLUME_DOWN,
                RelayVolumePolicy.ACTION_DOWN, 0, 0, 3);
        eq(0, atMinimum.targetIndex,
                "Volume Down holds at Accessibility minimum");
        require(atMinimum.consume && !atMinimum.write,
                "Volume Down at minimum cannot issue an upward write");
    }

    private static void awaitingConfirmationAndOffKeepTheirAuthorities() {
        RelayVolumePolicy.Decision waitingUp = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.AWAITING_CONFIRMATION,
                RelayVolumePolicy.KEY_VOLUME_UP,
                RelayVolumePolicy.ACTION_DOWN, 1, 0, 3);
        require(waitingUp.consume && !waitingUp.write,
                "awaiting confirmation blocks Volume Up");

        RelayVolumePolicy.Decision offUp = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.OFF,
                RelayVolumePolicy.KEY_VOLUME_UP,
                RelayVolumePolicy.ACTION_DOWN, 2, 0, 3);
        require(!offUp.consume && !offUp.write,
                "OFF leaves Volume Up to legacy Strict Safety");
    }

    private static void relayDoesNotOwnUnrelatedHardwareKeys() {
        RelayVolumePolicy.Decision mediaPlay = RelayVolumePolicy.onKey(
                RelayVolumePolicy.Phase.ACTIVE, 85,
                RelayVolumePolicy.ACTION_DOWN, 2, 0, 3);
        require(!mediaPlay.consume && !mediaPlay.write,
                "Relay must not consume unrelated hardware keys");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }
}
