#!/usr/bin/env python3
"""
Gerador do dicionário binário (PTKB v1) do Termclado.

Entrada:
  - scripts/data/palavras_pt_br.tsv: uma palavra por linha, "palavra<TAB>peso" (0 - 65535).
    Ver scripts/data/LEIA-ME.md para a origem dos dados e as licenças.
  - BIGRAMS (abaixo): pares frequentes "anterior -> próximo".

Saída: app/src/main/assets/dict_pt_br.bin

Formato:
  header: magic "PTKB" (4B), versão (2B, = 1), nº de unigramas (4B), nº de bigramas (4B), little-endian
  unigrama: len(exata) 1B + exata UTF-8, len(normalizada) 1B + normalizada UTF-8, peso 2B
  bigrama:  len(anterior normalizada) 1B + UTF-8, len(próxima) 1B + UTF-8, peso 2B
"""

import os
import struct
import sys
import unicodedata

DATA_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "palavras_pt_br.tsv")


def normalize_text(text: str) -> str:
    """Remove acentos e converte para minúsculas para chave de busca rápida."""
    text = text.lower().strip()
    decomposed = unicodedata.normalize("NFD", text)
    filtered = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    return filtered.replace("ç", "c")


def load_unigrams(path: str):
    """Lê palavra<TAB>peso. Ignora linhas vazias e palavras fora do limite de 255 bytes."""
    words = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 2:
                continue
            word, weight = parts[0].strip().lower(), int(parts[1])
            if not word or len(word.encode("utf-8")) > 255:
                continue
            weight = max(0, min(65535, weight))
            norm = normalize_text(word)
            if norm not in words or weight > words[norm][1]:
                words[norm] = (word, weight)
    return words


# Bigramas frequentes (anterior -> próximo, peso relativo 0 - 65535)
BIGRAMS = [
    ("muito", "obrigado", 60000),
    ("muito", "obrigada", 55000),
    ("muito", "bom", 58000),
    ("muito", "boa", 54000),
    ("muito", "bem", 56000),
    ("muito", "mais", 52000),
    ("muito", "feliz", 45000),
    ("muito", " legal", 43000),

    ("por", "favor", 62000),
    ("por", "que", 58000),
    ("por", "isso", 57000),
    ("por", "exemplo", 55000),
    ("por", "enquanto", 50000),

    ("bom", "dia", 64000),
    ("boa", "tarde", 62000),
    ("boa", "noite", 63000),
    ("boa", "sorte", 51000),

    ("tudo", "bem", 64000),
    ("tudo", "bom", 61000),
    ("tudo", "certo", 58000),

    ("com", "certeza", 58000),
    ("com", "você", 55000),
    ("com", "calma", 48000),
    ("com", "amor", 45000),

    ("de", "nada", 60000),
    ("de", "novo", 55000),
    ("de", "acordo", 52000),
    ("de", "repente", 49000),

    ("não", "sei", 57000),
    ("não", "posso", 54000),
    ("não", "tem", 55000),
    ("não", "é", 56000),
    ("não", "está", 53000),
    ("não", "quero", 52000),
    ("não", "consigo", 50000),
    ("não", "vai", 51000),

    ("eu", "acho", 55000),
    ("eu", "quero", 54000),
    ("eu", "estou", 56000),
    ("eu", "sou", 52000),
    ("eu", "tenho", 53000),
    ("eu", "não", 58000),
    ("eu", "posso", 52000),
    ("eu", "vou", 55000),
    ("eu", "também", 53000),

    ("você", "está", 57000),
    ("você", "tem", 54000),
    ("você", "pode", 55000),
    ("você", "sabe", 53000),
    ("você", "quer", 54000),
    ("você", "vai", 55000),

    ("vamos", "lá", 58000),
    ("vamos", "ver", 52000),

    ("até", "amanhã", 56000),
    ("até", "mais", 57000),
    ("até", "logo", 55000),
    ("até", "agora", 52000),

    ("um", "pouco", 56000),
    ("um", "abraço", 55000),
    ("um", "bom", 54000),
    ("uma", "boa", 53000),

    ("mais", "ou", 55000),
    ("ou", "menos", 55000),

    ("o", "que", 62000),
    ("do", "que", 58000),
    ("para", "você", 56000),
    ("para", "mim", 55000),

    ("em", "casa", 54000),
    ("no", "trabalho", 52000),
    ("na", "verdade", 54000),

    ("seja", "bem-vindo", 52000),
    ("dar", "certo", 53000),
    ("vai", "dar", 54000),
    ("deu", "certo", 52000),
]


def generate_binary_dictionary(output_path: str):
    unigram_dict = load_unigrams(DATA_FILE)
    sorted_unigrams = sorted(unigram_dict.items(), key=lambda x: x[0])

    cleaned_bigrams = []
    for prev, next_w, weight in BIGRAMS:
        cleaned_bigrams.append((normalize_text(prev), next_w.strip().lower(), weight))
    sorted_bigrams = sorted(cleaned_bigrams, key=lambda x: x[0])

    print(f"Empacotando {len(sorted_unigrams)} unigramas e {len(sorted_bigrams)} bigramas...")

    with open(output_path, "wb") as f:
        f.write(struct.pack("<4sHII", b"PTKB", 1, len(sorted_unigrams), len(sorted_bigrams)))
        for norm, (exact, weight) in sorted_unigrams:
            exact_bytes = exact.encode("utf-8")
            norm_bytes = norm.encode("utf-8")
            f.write(struct.pack("<B", len(exact_bytes)))
            f.write(exact_bytes)
            f.write(struct.pack("<B", len(norm_bytes)))
            f.write(norm_bytes)
            f.write(struct.pack("<H", int(weight)))
        for prev_norm, next_exact, weight in sorted_bigrams:
            p_bytes = prev_norm.encode("utf-8")
            n_bytes = next_exact.encode("utf-8")
            f.write(struct.pack("<B", len(p_bytes)))
            f.write(p_bytes)
            f.write(struct.pack("<B", len(n_bytes)))
            f.write(n_bytes)
            f.write(struct.pack("<H", int(weight)))

    file_size = os.path.getsize(output_path)
    print(f"Sucesso: {output_path} gerado ({file_size} bytes / {file_size / 1024:.1f} KB).")


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/dict_pt_br.bin"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    generate_binary_dictionary(out)
