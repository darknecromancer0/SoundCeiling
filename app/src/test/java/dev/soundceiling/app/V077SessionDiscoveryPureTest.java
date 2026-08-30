package dev.soundceiling.app;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class V077SessionDiscoveryPureTest {
    public static void main(String[] args) {
        parserKeepsOnlyUniqueActiveNonzeroSessions();
        ownershipRequiresExactUidAndSingleFreshSession();
        enhancedHandleCannotBeCreatedWithoutAcceptedOwnership();
        historicalParserCannotRestoreRuntimeDiscovery();
        System.out.println("V077SessionDiscoveryPureTest: PASS");
    }

    private static void parserKeepsOnlyUniqueActiveNonzeroSessions() {
        String dump = "Port ID: 57; Session ID: 233; uid 10292; State: Active\n"
                + "Port ID: 58; Session ID: 0; uid 10292; State: Active\n"
                + "Port ID: 59; Session ID: 244; uid 10292; State: Inactive\n"
                + "Port ID: 60; Session ID: 233; uid 10292; State: Active\n";
        List<AudioSessionRecord> records = AudioSessionDumpParser.parse(dump, 1000L);
        require(records.size() == 1, "parser must keep one unique active nonzero session");
        require(records.get(0).sessionId == 233 && records.get(0).uid == 10292,
                "parser must preserve session/uid");
    }

    private static void ownershipRequiresExactUidAndSingleFreshSession() {
        SourceDescriptor yandex = new SourceDescriptor(
                "ru.yandex.music", 10292, "Yandex Music", false, false);
        AudioSessionRecord owned = new AudioSessionRecord(
                233, 10292, true, 1000L, "test");
        AudioSessionOwnershipResolver.Decision exact = AudioSessionOwnershipResolver.resolve(
                List.of(owned), yandex, 1200L);
        require(exact.accepted && exact.sessionId == 233 && exact.uid == 10292,
                "one fresh exact-UID session must be accepted");

        AudioSessionOwnershipResolver.Decision mismatch = AudioSessionOwnershipResolver.resolve(
                List.of(new AudioSessionRecord(233, 12345, true, 1000L, "test")),
                yandex, 1200L);
        require(!mismatch.accepted && "uid_session_not_found".equals(mismatch.reason),
                "UID mismatch must fail closed");

        AudioSessionOwnershipResolver.Decision ambiguous = AudioSessionOwnershipResolver.resolve(
                Arrays.asList(owned,
                        new AudioSessionRecord(234, 10292, true, 1000L, "test")),
                yandex, 1200L);
        require(!ambiguous.accepted && "ambiguous_sessions".equals(ambiguous.reason),
                "multiple same-UID sessions must not be guessed in v0.7.7");

        AudioSessionOwnershipResolver.Decision stale = AudioSessionOwnershipResolver.resolve(
                List.of(owned), yandex, 4000L);
        require(!stale.accepted, "stale dump evidence must expire");
    }

    private static void enhancedHandleCannotBeCreatedWithoutAcceptedOwnership() {
        SourceDescriptor yandex = new SourceDescriptor(
                "ru.yandex.music", 10292, "Yandex Music", false, false);
        AudioSessionOwnershipResolver.Decision exact = AudioSessionOwnershipResolver.resolve(
                List.of(new AudioSessionRecord(233, 10292, true, 1000L, "test")),
                yandex, 1200L);
        Optional<DspEndpointHandle> handle = exact.toDspHandle(
                "ru.yandex.music", AppPolicy.global());
        require(handle.isPresent() && handle.get().audioSessionId == 233
                        && handle.get().isEnhancedSession(),
                "accepted ownership may become a trusted enhanced endpoint");

        AudioSessionOwnershipResolver.Decision rejected = AudioSessionOwnershipResolver.resolve(
                List.of(new AudioSessionRecord(233, 12345, true, 1000L, "test")),
                yandex, 1200L);
        require(rejected.toDspHandle("ru.yandex.music", AppPolicy.global()).isEmpty(),
                "rejected ownership must never self-promote to DSP authority");
        require(DspEndpointHandle.tryCreate(233,
                DspEndpointHandle.Provenance.ENHANCED_SESSION_DISCOVERY,
                "ru.yandex.music", AppPolicy.global()).isEmpty(),
                "generic handle factory must reject raw enhanced-discovery provenance");
    }

    private static void historicalParserCannotRestoreRuntimeDiscovery() {
        AudioSessionDiscovery.Snapshot quarantined =
                new QuarantinedAudioSessionDiscovery().discover(5L);
        require(!quarantined.permissionGranted && quarantined.records.isEmpty()
                        && EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON.equals(quarantined.reason),
                "historical parser fixtures must not restore production session discovery");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
