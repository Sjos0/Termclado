pub mod dictionary;
pub mod jni;
pub mod suggest;

#[cfg(test)]
mod tests {
    use crate::dictionary::{normalize, Dictionary, Unigram};
    use crate::suggest::{get_auto_correction, get_suggestions};

    fn create_test_dict() -> Dictionary {
        let mut dict = Dictionary::empty();

        let words = vec![
            ("não", "nao", 50000),
            ("você", "voce", 45000),
            ("obrigado", "obrigado", 40000),
            ("obrigada", "obrigada", 35000),
            ("abraço", "abraco", 30000),
            ("bom", "bom", 40000),
            ("boa", "boa", 38000),
            ("bem", "bem", 39000),
            ("muito", "muito", 42000),
            ("casa", "casa", 32000),
            ("coração", "coracao", 28000),
            ("fazer", "fazer", 31000),
            ("dia", "dia", 37000),
            ("noite", "noite", 33000),
        ];

        for (exact, norm, freq) in words {
            dict.word_set.insert(exact.to_string(), freq);
            dict.unigrams.push(Unigram {
                exact: exact.to_string(),
                norm: norm.to_string(),
                freq,
            });
        }

        // Bigramas
        dict.bigrams_by_prev
            .entry("muito".to_string())
            .or_default()
            .push(("obrigado".to_string(), 50000));
        dict.bigrams_by_prev
            .entry("muito".to_string())
            .or_default()
            .push(("bom".to_string(), 48000));
        dict.bigrams_by_prev
            .entry("bom".to_string())
            .or_default()
            .push(("dia".to_string(), 60000));

        dict
    }

    #[test]
    fn test_accents_normalization() {
        assert_eq!(normalize("você"), "voce");
        assert_eq!(normalize("coração"), "coracao");
        assert_eq!(normalize("não"), "nao");
        assert_eq!(normalize("água"), "agua");
        assert_eq!(normalize("é"), "e");
        assert_eq!(normalize("açúcar"), "acucar");
    }

    #[test]
    fn test_suggestions_ordering() {
        let dict = create_test_dict();
        let sugs = get_suggestions(&dict, "", "obr");
        assert!(!sugs.is_empty());
        assert_eq!(sugs[0], "obrigado");
        assert!(sugs.contains(&"obrigada".to_string()));
    }

    #[test]
    fn test_bigram_boost() {
        let dict = create_test_dict();
        // Com contexto "muito" e prefixo "b", "bom" deve ser altamente ranqueado
        let sugs = get_suggestions(&dict, "muito", "b");
        assert!(!sugs.is_empty());
        assert_eq!(sugs[0], "bom");

        // Contexto "bom" sem prefixo sugere "dia"
        let contextual = get_suggestions(&dict, "bom", "");
        assert_eq!(contextual[0], "dia");
    }

    #[test]
    fn test_conservative_correction() {
        let dict = create_test_dict();

        // 1. Palavra exata válida nunca deve ser corrigida
        assert_eq!(get_auto_correction(&dict, "", "casa"), None);
        assert_eq!(get_auto_correction(&dict, "", "não"), None);

        // 2. Sem acento deve corrigir para a forma com acento
        assert_eq!(get_auto_correction(&dict, "", "nao"), Some("não".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "voce"), Some("você".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "coracao"), Some("coração".to_string()));

        // 3. Erro de digitação simples (distância 1)
        assert_eq!(get_auto_correction(&dict, "", "abracp"), Some("abraço".to_string()));

        // 4. Palavra bizarra/aleatória não deve sofrer autocorreção
        assert_eq!(get_auto_correction(&dict, "", "xyzqwk"), None);
        assert_eq!(get_auto_correction(&dict, "", "ab"), None); // curto demais
    }

    /// Dicionário real empacotado no app (mesmo arquivo lido pelo teclado).
    fn real_dict() -> Dictionary {
        Dictionary::from_bytes(include_bytes!("../../app/src/main/assets/dict_pt_br.bin"))
            .expect("dict_pt_br.bin válido")
    }

    #[test]
    fn test_contrato_jni_com_dicionario_real() {
        // Mesmas asserções que o teste Robolectric antigo fazia no Kotlin.
        let dict = real_dict();
        let sugestoes = get_suggestions(&dict, "", "obr");
        assert!(!sugestoes.is_empty(), "deve haver sugestões para 'obr'");
        assert_eq!(sugestoes[0], "obrigado");
        assert_eq!(get_auto_correction(&dict, "", "nao"), Some("não".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "voce"), Some("você".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "casa"), None);
    }

    #[test]
    fn test_real_dictionary_never_swaps_valid_words() {
        // Regressão: estas palavras estavam sendo trocadas por outra ao pressionar espaço.
        let dict = real_dict();
        for w in [
            "parar", "muita", "quanto", "estar", "dessa", "desse", "disso", "nessa",
            "nisso", "daqui", "casal", "fazem", "entra", "tempos", "dá", "às",
        ] {
            assert_eq!(get_auto_correction(&dict, "", w), None, "'{}' é válida e não pode ser trocada", w);
        }
    }

    #[test]
    fn test_real_dictionary_still_fixes_plausible_typos() {
        let dict = real_dict();
        assert_eq!(get_auto_correction(&dict, "", "muuito"), Some("muito".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "obrigaddo"), Some("obrigado".to_string()));
        assert_eq!(get_auto_correction(&dict, "", "temoo"), Some("tempo".to_string()));
    }

    #[test]
    fn test_transposition_and_neighbor_key_typos() {
        let dict = create_test_dict();
        assert_eq!(get_auto_correction(&dict, "", "abracp"), Some("abraço".to_string()));
        // "casal" -> "casa": a letra extra 'l' não é dobrada, então não é erro de digitação plausível
        assert_eq!(get_auto_correction(&dict, "", "casal"), None);
    }

    #[test]
    fn test_top_suggestions_without_context() {
        let dict = create_test_dict();
        // Sem prefixo e sem contexto: as 3 palavras mais frequentes, em ordem decrescente
        assert_eq!(get_suggestions(&dict, "", ""), vec!["não", "você", "muito"]);
    }

    #[test]
    fn test_top_suggestions_keep_dictionary_order_on_ties() {
        let mut dict = Dictionary::empty();
        for w in ["alfa", "beta", "gama", "delta"] {
            dict.unigrams.push(Unigram {
                exact: w.to_string(),
                norm: w.to_string(),
                freq: 100,
            });
        }
        // Empate de frequência: vale a ordem em que as palavras estão no dicionário
        assert_eq!(get_suggestions(&dict, "", ""), vec!["alfa", "beta", "gama"]);
    }
}
