package io.gildongmu.voiceprobe;

/** Filters narration transcripts before accepting an interruption. */
final class BargeInGate {
    private static final String SEPARATOR = "[\\s,.:!?…]";
    // A longer cue gives ASR another way to recognize the intent when it mishears the app name.
    private static final java.util.regex.Pattern ADDRESS = java.util.regex.Pattern.compile(
            "^\\s*(?:(?:마일(?:아|야)?|매일|말일|말이)" + SEPARATOR + "+질문\\s*할게"
                    + "|질문\\s*할게|마일(?:아|야)?)(?=$|" + SEPARATOR + ")");
    private final String narration;
    private static final java.util.regex.Pattern DIRECT_QUESTION = java.util.regex.Pattern.compile(
            "(?:^|\\s)(?:언제|누가|왜|어떻게|어디서|몇|얼마나|무엇을|뭘)(?=\\s|$)"
                    + ".*(?:어|아|지|니|까|나요|인가요|어요|아요|죠|예요|이에요)[?!.…]*$");
    BargeInGate(String narration) { this.narration = normalize(narration); }
    boolean shouldInterrupt(String text) {
        if (text == null || text.length() > 1000) return false;
        // Do not infer a human speaker from ASR differences. Narration must never contain the address.
        if (!ADDRESS.matcher(text).find() || narration.contains("마일") || narration.contains("질문할게")) return false;
        // An ASR-inserted address before a verbatim stretch of narration is still echo.
        String question = normalize(questionText(text));
        return question.length() < 10 || !narration.contains(question);
    }
    boolean isDirectQuestion(String text) {
        if (text == null || text.length() > 1000) return false;
        String normalized = normalize(text);
        return normalized.length() >= 6 && !narration.contains(normalized)
                && DIRECT_QUESTION.matcher(text.trim()).find();
    }
    static String questionText(String text) {
        return text == null ? "" : ADDRESS.matcher(text).replaceFirst("").replaceFirst("^[\\s,.:!?…]+", "").trim();
    }
    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
