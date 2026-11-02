package sayit;

import java.text.Normalizer;

public final class TextMatch {
    /** lowercase, strip accents/umlauts, ß->ss, letters only. */
    public static String norm(String s) {
        String t = s.toLowerCase().replace("ß", "ss").replace("ł", "l").replace("ı", "i").replace("ø", "o")
                .replace("æ", "ae").replace("œ", "oe").replace("đ", "d");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.replaceAll("[^a-z]", "");
    }

    public static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return 1.0 - (double) prev[b.length()] / Math.max(a.length(), b.length());
    }

    /** Best similarity of the target against the whole utterance or any single word / adjacent pair. */
    public static double best(String heard, String target) {
        String t = norm(target);
        String[] words = heard.trim().split("\\s+");
        double best = similarity(norm(heard), t);
        for (int i = 0; i < words.length; i++) {
            best = Math.max(best, similarity(norm(words[i]), t));
            if (i + 1 < words.length) best = Math.max(best, similarity(norm(words[i] + words[i + 1]), t));
        }
        return best;
    }
}
