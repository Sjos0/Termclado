# Termclado v1.9.3

Atualização focada em precisão de toque, edição de texto e qualidade das sugestões.

## Novidades

- **Dicionário de ~36 mil palavras** (antes eram 538). Palavras comuns que antes pareciam não existir agora são reconhecidas. Origem e licença em `scripts/data/LEIA-ME.md`.
- **Autocorreção mais precisa:** erros como `abracp` → `abraço`, `amnha` → `manhã`, `celluar` → `celular`, `mensagen` → `mensagem`. Palavras válidas não são alteradas.
- **Motor único em Rust:** o motor Kotlin foi removido. A autocorreção ficou cerca de 28 vezes mais rápida nas medições.

## Correções

- **Anti toque duplo de 80 ms, global:** qualquer toque em menos de 80 ms depois de outro é ignorado, no app e no teclado, incluindo os botões "?123" e "ABC". Antes a proteção era de 60 ms e valia só por tecla.
- **Edição no começo do texto:** ao revisar um texto e tocar em um trecho anterior, o teclado não volta mais para o final do que estava sendo digitado.
- **Texto de privacidade corrigido:** o app informa que a digitação por voz envia o áudio à Groq, e só quando há chave configurada.

## Observações

- Não há ainda aprendizado de palavras, pílula de autopreenchimento nem IA local. Estão planejados para versões seguintes.
- Testes: unitários e de regras no CI, testes do motor Rust (10 de 10), e medição no emulador Android 14.
