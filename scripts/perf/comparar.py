#!/usr/bin/env python3
"""Compara a geometria medida nas capturas do emulador com a referência do Gboard (em pixels).

Uso: comparar.py <pasta_das_capturas>
Escreve <pasta>/comparacao.txt e imprime o resultado. Tolerância: 3 px.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from analisar_teclado import analyze  # noqa: E402

REF = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ref")
PARES = [
    ("03-teclado-sugestoes", "ref-sugestoes", False),
    ("04-teclado-ferramentas", "ref-ferramentas", True),
    ("07-teclado-pilula", "ref-pilula", False),
]
TOL = 3


def cmp(nome, a, b, tol=TOL):
    d = a - b
    return abs(d) <= tol, f"{'OK ' if abs(d) <= tol else 'DIF'} {nome:40s} nosso={a:8.1f}  gboard={b:8.1f}  dif={d:+7.1f}"


def comparar(n, r, icones):
    linhas = []
    if "erro" in n:
        return [f"ERRO: {n['erro']}"], 1
    def add(nome, a, b, tol=TOL):
        ok, txt = cmp(nome, a, b, tol)
        linhas.append(txt)
        return ok
    falhas = 0
    falhas += not add("barra até a 1ª linha de teclas", n["barra_altura"], r["barra_altura"])
    for i, (ln, lr) in enumerate(zip(n["linhas_teclas"], r["linhas_teclas"])):
        falhas += not add(f"linha {i + 1}: topo", ln["topo"], lr["topo"])
        falhas += not add(f"linha {i + 1}: altura", ln["altura"], lr["altura"])
    if len(n["linhas_teclas"]) != len(r["linhas_teclas"]):
        linhas.append(f"DIF número de linhas: nosso={len(n['linhas_teclas'])} gboard={len(r['linhas_teclas'])}")
        falhas += 1
    nb, rb = n["teclas_por_linha"][-1], r["teclas_por_linha"][-1]
    for i, (kn, kr) in enumerate(zip(nb, rb)):
        falhas += not add(f"linha de baixo, tecla {i + 1}: largura", kn["largura"], kr["largura"])
    n2, r2 = n["teclas_por_linha"][1], r["teclas_por_linha"][1]
    if n2 and r2:
        falhas += not add("QWERTY: largura da 1ª tecla", n2[0]["largura"], r2[0]["largura"])
        if len(n2) > 1 and len(r2) > 1:
            falhas += not add("QWERTY: passo entre teclas", n2[1]["x0"] - n2[0]["x0"], r2[1]["x0"] - r2[0]["x0"])
    if "microfone_circulo" in n and "microfone_circulo" in r:
        falhas += not add("microfone: diâmetro do círculo", n["microfone_circulo"]["diametro"], r["microfone_circulo"]["diametro"])
        falhas += not add("microfone: centro x", n["microfone_circulo"]["cx"], r["microfone_circulo"]["cx"])
    if icones:
        ni, ri = n["icones_barra"], r["icones_barra"]
        if len(ni) != len(ri):
            linhas.append(f"DIF ícones da barra: nosso={len(ni)} gboard={len(ri)}")
            falhas += 1
        for i, (a, b) in enumerate(zip(ni, ri)):
            falhas += not add(f"ícone {i + 1}: centro x", a["cx"], b["cx"])
            falhas += not add(f"ícone {i + 1}: largura", a["largura"], b["largura"])
            falhas += not add(f"ícone {i + 1}: altura", a["altura"], b["altura"])
    if "pilula" in r:
        if "pilula" in n:
            falhas += not add("pílula: largura", n["pilula"]["largura"], r["pilula"]["largura"], 4)
            falhas += not add("pílula: altura", n["pilula"]["altura"], r["pilula"]["altura"], 4)
        else:
            linhas.append("DIF pílula não encontrada na captura")
            falhas += 1
    return linhas, falhas


if __name__ == "__main__":
    pasta = sys.argv[1]
    out, total = [], 0
    for img, ref, icones in PARES:
        caminho = os.path.join(pasta, img + ".png")
        if not os.path.exists(caminho):
            out.append(f"### {img}: captura ausente")
            total += 1
            continue
        r = json.load(open(os.path.join(REF, ref + ".json"), encoding="utf-8"))
        linhas, falhas = comparar(analyze(caminho), r, icones)
        out.append(f"### {img} x {ref}  ({falhas} diferenças acima de {TOL} px)")
        out.extend(linhas)
        total += falhas
    out.append(f"\nTOTAL de diferenças: {total}")
    texto = "\n".join(out)
    open(os.path.join(pasta, "comparacao.txt"), "w", encoding="utf-8").write(texto + "\n")
    print(texto)
