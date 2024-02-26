package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LemmaFinder {

    private static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private final LuceneMorphology luceneMorph;

    public LemmaFinder() throws IOException {
        luceneMorph = new RussianLuceneMorphology();
    }

    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();

        String[] words = text.split("[ ,;\\n]");
        for (String word : words) {
            if (checkOnParticle(word)) {
                List<String> wordBaseForms =
                        luceneMorph.getNormalForms(word.toLowerCase());
                wordBaseForms.forEach(lemma -> {
                    Integer value = lemmas.get(lemma);
                    value = value == null ? 1 : value + 1;
                    lemmas.put(lemma, value);
                });
            }
        }
        return lemmas;
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : PARTICLES_NAMES) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().
                anyMatch(this::hasParticleProperty);

    }

    private Boolean checkOnParticle(String word) {
        if (word.isBlank()) return false;
        try {
            List<String> wordBaseForms2 =
                    luceneMorph.getMorphInfo(word.toLowerCase());
            if (anyWordBaseBelongToParticle(wordBaseForms2)) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
