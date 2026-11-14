package io.gildongmu.voiceprobe;

/** Timing on the candidate A route. A modelled window is never treated as field evidence. */
final class VisibilityReplay {
    enum Cue { NONE, PREVIEW, OBSERVE }
    private boolean previewed, observed;
    private double eligibleSeconds;

    Cue step(double position, double uncertainty, double metersPerSecond,
             double seconds, double start, double end, boolean audioBusy) {
        if (audioBusy || !Double.isFinite(position) || !Double.isFinite(uncertainty)
                || uncertainty < 0 || !Double.isFinite(metersPerSecond)
                || metersPerSecond <= 0.5 || metersPerSecond > 120 / 3.6
                || !Double.isFinite(seconds) || seconds <= 0 || seconds > 1
                || !Double.isFinite(start) || !Double.isFinite(end) || start >= end) {
            eligibleSeconds = 0;
            return Cue.NONE;
        }
        double reserveSpeed = 120 / 3.6;
        boolean inside = position - uncertainty >= start
                && end - position - uncertainty >= reserveSpeed * 15;
        eligibleSeconds = inside ? eligibleSeconds + seconds : 0;
        if (!observed && eligibleSeconds >= 1) {
            observed = true;
            return Cue.OBSERVE;
        }
        double distanceToEntry = start - position - uncertainty;
        if (!previewed && !observed && (start - position) / metersPerSecond <= 12
                && distanceToEntry > reserveSpeed * 4 && end - start >= reserveSpeed * 15) {
            previewed = true;
            return Cue.PREVIEW;
        }
        return Cue.NONE;
    }
}
