# Dados do dicionário

`palavras_pt_br.tsv` é a lista de palavras usada para gerar `app/src/main/assets/dict_pt_br.bin`.
Cada linha é `palavra<TAB>peso` (peso de 0 a 65535, maior = mais frequente).

## Origem

1. **Frequência de uso:** `pt_br_50k.txt` do projeto FrequencyWords (hermitdave), baseado em legendas do OpenSubtitles 2018.
   Licença: CC BY-SA 4.0. Repositório: https://github.com/hermitdave/FrequencyWords
2. **Validação ortográfica:** dicionário pt_BR do VERO (LibreOffice), arquivos `pt_BR.dic` e `pt_BR.aff`.
   Licença: LGPL v3.0 / MPL 1.1. Repositório: https://github.com/LibreOffice/dictionaries
3. **Lista curada inicial:** palavras de uso diário escritas à mão, com peso próprio.

## Como a lista foi montada

- Cada forma vem do arquivo de frequência e só é mantida se: contém apenas letras, tem de 2 a 20 caracteres e
  - aparece no VERO (expandido com `unmunch`, mantendo formas só com letras e até 20 caracteres), ou
  - tem acento e está entre as 3000 mais frequentes (cobre pronomes e formas que o VERO não lista, como "nós" e "vocês").
- Formas informais (`vc`, `pq`, `tbm`, `blz`, `pra`, `ok`, `kkk`, ...) entram com peso fixo.
- Nomes ingleses e formas só ASCII fora do VERO ficam de fora.
- O peso é a escala logarítmica da contagem de ocorrências; a lista curada usa o próprio peso, e em caso de repetição vale o maior.

## Licença dos dados

A lista combina dados sob CC BY-SA 4.0 (frequências) e uma validação feita com dados LGPL/MPL.
Por isso o arquivo derivado (`dict_pt_br.bin`) é distribuído sob **CC BY-SA 4.0**, com atribuição aos dois projetos (ver `NOTICE`).
