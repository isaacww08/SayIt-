package sayit;

import java.util.List;

/** One playable language. Non-Latin scripts are shown/matched in romanized (Latin) form. */
public record Language(String code, String name, String mcLang, String voskModel, Script script) {
    public enum Script { LATIN, CYRILLIC, DEVANAGARI }

    public static final List<Language> ALL = List.of(
        new Language("de", "German",     "de_de", "vosk-model-small-de-0.15",  Script.LATIN),
        new Language("es", "Spanish",    "es_es", "vosk-model-small-es-0.42",  Script.LATIN),
        new Language("fr", "French",     "fr_fr", "vosk-model-small-fr-0.22",  Script.LATIN),
        new Language("it", "Italian",    "it_it", "vosk-model-small-it-0.22",  Script.LATIN),
        new Language("pt", "Portuguese", "pt_br", "vosk-model-small-pt-0.3",   Script.LATIN),
        new Language("nl", "Dutch",      "nl_nl", "vosk-model-small-nl-0.22",  Script.LATIN),
        new Language("pl", "Polish",     "pl_pl", "vosk-model-small-pl-0.22",  Script.LATIN),
        new Language("tr", "Turkish",    "tr_tr", "vosk-model-small-tr-0.3",   Script.LATIN),
        new Language("ru", "Russian",    "ru_ru", "vosk-model-small-ru-0.22",  Script.CYRILLIC),
        new Language("hi", "Hindi",      "hi_in", "vosk-model-small-hi-0.22",  Script.DEVANAGARI)
    );

    public static Language byCode(String code) {
        for (Language l : ALL) if (l.code.equals(code)) return l;
        return ALL.get(0);
    }
}
