package io.gildongmu.voiceprobe;

/** Synthetic route distance input; not GPS or a visibility prediction. */
final class NearbyStory {
    private boolean offered;
    boolean offer(double distanceMeters, boolean sessionActive, boolean conversationBusy) {
        if (!sessionActive || conversationBusy || offered || !Double.isFinite(distanceMeters)
                || distanceMeters < 0 || distanceMeters > 350) return false;
        offered = true;
        return true;
    }
}
