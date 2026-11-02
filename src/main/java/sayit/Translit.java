package sayit;

import java.util.HashMap;
import java.util.Map;

/**
 * Cyrillic + Devanagari -> Latin. Used for BOTH the word shown on screen and the text the
 * speech engine heard, so the two are always compared in the same alphabet.
 * Latin text passes through unchanged.
 */
public final class Translit {
    private static final Map<Character, String> CYR = new HashMap<>();
    private static final Map<Character, String> INDEP = new HashMap<>();
    private static final Map<Character, String> CONS = new HashMap<>();
    private static final Map<Character, String> MATRA = new HashMap<>();
    private static final Map<Character, String> NUKTA = new HashMap<>();

    private static void c(String cyr, String... lat) {
        for (int i = 0; i < cyr.length(); i++) CYR.put(cyr.charAt(i), lat[i]);
    }

    static {
        // Russian
        c("абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
          "a","b","v","g","d","e","yo","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f",
          "kh","ts","ch","sh","shch","","y","","e","yu","ya");
        // Ukrainian extras (harmless to have)
        CYR.put('і', "i"); CYR.put('ї', "yi"); CYR.put('є', "ye"); CYR.put('ґ', "g");

        // Devanagari independent vowels
        String[][] iv = {{"\u0905","a"},{"\u0906","aa"},{"\u0907","i"},{"\u0908","ee"},{"\u0909","u"},{"\u090A","oo"},
            {"\u090B","ri"},{"\u090F","e"},{"\u0910","ai"},{"\u0913","o"},{"\u0914","au"},{"\u0911","o"},{"\u090D","e"}};
        for (String[] p : iv) INDEP.put(p[0].charAt(0), p[1]);

        // vowel signs (matras)
        String[][] mt = {{"\u093E","aa"},{"\u093F","i"},{"\u0940","ee"},{"\u0941","u"},{"\u0942","oo"},{"\u0943","ri"},
            {"\u0947","e"},{"\u0948","ai"},{"\u094B","o"},{"\u094C","au"},{"\u0949","o"},{"\u0945","e"}};
        for (String[] p : mt) MATRA.put(p[0].charAt(0), p[1]);

        // consonants (without the inherent 'a')
        String[][] cs = {
            {"\u0915","k"},{"\u0916","kh"},{"\u0917","g"},{"\u0918","gh"},{"\u0919","ng"},
            {"\u091A","ch"},{"\u091B","chh"},{"\u091C","j"},{"\u091D","jh"},{"\u091E","ny"},
            {"\u091F","t"},{"\u0920","th"},{"\u0921","d"},{"\u0922","dh"},{"\u0923","n"},
            {"\u0924","t"},{"\u0925","th"},{"\u0926","d"},{"\u0927","dh"},{"\u0928","n"},
            {"\u092A","p"},{"\u092B","ph"},{"\u092C","b"},{"\u092D","bh"},{"\u092E","m"},
            {"\u092F","y"},{"\u0930","r"},{"\u0932","l"},{"\u0935","v"},
            {"\u0936","sh"},{"\u0937","sh"},{"\u0938","s"},{"\u0939","h"},
            // precomposed nukta letters
            {"\u0958","q"},{"\u0959","kh"},{"\u095A","gh"},{"\u095B","z"},{"\u095C","r"},{"\u095D","rh"},{"\u095E","f"}
        };
        for (String[] p : cs) CONS.put(p[0].charAt(0), p[1]);

        // consonant + nukta (U+093C) combos
        NUKTA.put('\u0915', "q"); NUKTA.put('\u0916', "kh"); NUKTA.put('\u0917', "gh"); NUKTA.put('\u091C', "z");
        NUKTA.put('\u0921', "r"); NUKTA.put('\u0922', "rh"); NUKTA.put('\u092B', "f");
    }

    public static String toLatin(String in) {
        StringBuilder o = new StringBuilder();
        boolean lastInherent = false;   // last thing written was a consonant's implied 'a'
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);

            if (Character.isWhitespace(ch)) {
                if (lastInherent) o.setLength(o.length() - 1);   // Hindi drops the final 'a' ("seb", not "seba")
                lastInherent = false;
                o.append(ch);
                continue;
            }

            String cyr = CYR.get(Character.toLowerCase(ch));
            if (cyr != null) {
                if (Character.isUpperCase(ch) && !cyr.isEmpty())
                    cyr = Character.toUpperCase(cyr.charAt(0)) + cyr.substring(1);
                o.append(cyr);
                lastInherent = false;
                continue;
            }

            if (ch >= '\u0900' && ch <= '\u097F') {
                String vow = INDEP.get(ch);
                if (vow != null) { o.append(vow); lastInherent = false; continue; }

                String cons = CONS.get(ch);
                if (cons != null) {
                    if (i + 1 < in.length() && in.charAt(i + 1) == '\u093C') {   // nukta
                        String nk = NUKTA.get(ch);
                        if (nk != null) cons = nk;
                        i++;
                    }
                    o.append(cons);
                    char n = i + 1 < in.length() ? in.charAt(i + 1) : 0;
                    String m = MATRA.get(n);
                    if (m != null) { o.append(m); i++; lastInherent = false; }
                    else if (n == '\u094D') { i++; lastInherent = false; }       // virama: no vowel
                    else { o.append('a'); lastInherent = true; }
                    continue;
                }
                if (ch == '\u0902' || ch == '\u0901') { o.append('n'); lastInherent = false; continue; }  // anusvara / chandrabindu
                if (ch == '\u0903') { o.append('h'); lastInherent = false; continue; }                      // visarga
                continue;   // anything else in the block (digits, danda, nukta alone): skip
            }

            o.append(ch);
            lastInherent = false;
        }
        if (lastInherent) o.setLength(o.length() - 1);
        return o.toString();
    }
}
