#!/usr/bin/env python3
"""
Mede a geometria do teclado em uma captura de tela: topo, barra de ferramentas (ícones e círculo
do microfone), pílula, linhas e larguras de teclas. Usado para comparar o Termclado com a referência
do Gboard em pixels, no mesmo aparelho (ou no emulador com a mesma geometria: 720x1520, 288 dpi).

Uso: analisar_teclado.py captura.png [--json]
Dependências: pillow, numpy, scipy.
"""
import json
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

BG = (38, 50, 56)        # fundo do teclado (#263238)
KEY = (64, 74, 80)       # tecla de letra e círculo do microfone (#404A50)
PILL = (59, 70, 76)      # pílula de sugestão/captura (#3B464C)


def near(a, color, tol):
    return (np.abs(a - np.array(color)) <= tol).all(axis=-1)


def runs(flags, min_len=1, merge_gap=0):
    """Intervalos [ini, fim] consecutivos de True, juntando vãos menores ou iguais a merge_gap."""
    out, start = [], None
    for i, f in enumerate(list(flags) + [False]):
        if f and start is None:
            start = i
        elif not f and start is not None:
            out.append([start, i - 1])
            start = None
    merged = []
    for r in out:
        if merged and r[0] - merged[-1][1] - 1 <= merge_gap:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    return [r for r in merged if r[1] - r[0] + 1 >= min_len]


def keyboard_top(a):
    col = near(a[:, 4:8, :], BG, 4).all(axis=1)
    for y0, y1 in runs(col, min_len=25):
        return y0
    return None


def analyze(path):
    a = np.array(Image.open(path).convert("RGB")).astype(int)
    h, w, _ = a.shape
    top = keyboard_top(a)
    if top is None:
        return {"erro": "teclado não encontrado", "tamanho": [w, h]}

    nonbg = ~near(a, BG, 10)
    # Linhas de teclas: pixels da cor de tecla de letra (os cantos arredondados não atrapalham).
    # Ignora os primeiros 60 px abaixo do topo (barra de ferramentas e círculo do microfone).
    cand = near(a, KEY, 3).sum(axis=1) > 150
    cand[: top + 60] = False
    rows = [[y0, y1] for y0, y1 in runs(cand, min_len=30)][:5]
    first_key = rows[0][0] if rows else top + 80

    res = {
        "tamanho": [w, h],
        "teclado_topo": top,
        "barra_altura": first_key - top,
        "linhas_teclas": [
            {"topo": y0 - top, "altura": y1 - y0 + 1} for y0, y1 in rows
        ],
        "passo_vertical": [rows[i + 1][0] - rows[i][0] for i in range(len(rows) - 1)],
    }

    # teclas de cada linha: posição, largura e altura (varredura vertical no centro de cada tecla)
    por_linha = []
    for y0, y1 in rows:
        mid = (y0 + y1) // 2
        lista = []
        for x0, x1 in runs(nonbg[mid], min_len=20, merge_gap=2):
            cx = (x0 + x1) // 2
            v0 = mid
            while v0 > 0 and nonbg[v0 - 1, cx]:
                v0 -= 1
            v1 = mid
            while v1 < h - 1 and nonbg[v1 + 1, cx]:
                v1 += 1
            lista.append({"x0": x0, "largura": x1 - x0 + 1, "topo": v0 - top, "altura": v1 - v0 + 1})
        por_linha.append(lista)
    res["teclas_por_linha"] = por_linha

    # barra: ícones claros
    bar = a[top:first_key]
    light = bar.mean(axis=2) > 100
    lab, n = ndimage.label(light)
    boxes = []
    for sl in ndimage.find_objects(lab):
        ys, xs = sl
        if xs.stop - xs.start >= 4 and ys.stop - ys.start >= 4:
            boxes.append((xs.start, ys.start, xs.stop - 1, ys.stop - 1))
    boxes.sort()
    clusters = []
    for b in boxes:
        if clusters and b[0] - clusters[-1][2] <= 18:
            c = clusters[-1]
            clusters[-1] = (min(c[0], b[0]), min(c[1], b[1]), max(c[2], b[2]), max(c[3], b[3]))
        else:
            clusters.append(b)
    res["icones_barra"] = [
        {
            "cx": (c[0] + c[2]) / 2.0,
            "cy": (c[1] + c[3]) / 2.0,
            "largura": c[2] - c[0] + 1,
            "altura": c[3] - c[1] + 1,
        }
        for c in clusters
    ]
    cx = [i["cx"] for i in res["icones_barra"]]
    res["espacamento_entre_icones"] = [round(cx[i + 1] - cx[i], 1) for i in range(len(cx) - 1)]

    # círculo do microfone (cor da tecla, no canto direito da barra)
    right = near(bar, KEY, 4)
    right[:, : w - 140] = False
    lab, n = ndimage.label(right)
    best = None
    for sl in ndimage.find_objects(lab):
        ys, xs = sl
        area = (xs.stop - xs.start) * (ys.stop - ys.start)
        if best is None or area > best[0]:
            best = (area, xs.start, ys.start, xs.stop - 1, ys.stop - 1)
    if best and best[0] > 300:
        res["microfone_circulo"] = {
            "x0": best[1], "x1": best[3], "y0": best[2], "y1": best[4],
            "diametro": best[3] - best[1] + 1,
            "cx": (best[1] + best[3]) / 2.0,
        }

    # pílula de captura/sugestão (centro da barra)
    pil = near(bar, PILL, 3)
    pil[:, : int(w * 0.15)] = False
    pil[:, int(w * 0.85):] = False
    lab, n = ndimage.label(pil)
    best = None
    for sl in ndimage.find_objects(lab):
        ys, xs = sl
        area = (xs.stop - xs.start) * (ys.stop - ys.start)
        if best is None or area > best[0]:
            best = (area, xs.start, ys.start, xs.stop - 1, ys.stop - 1)
    if best and best[0] > 1500:
        res["pilula"] = {
            "x0": best[1], "x1": best[3], "y0": best[2], "y1": best[4],
            "largura": best[3] - best[1] + 1, "altura": best[4] - best[2] + 1,
        }
    return res


def show(path):
    r = analyze(path)
    print(f"== {path.split('/')[-1]}")
    for k, v in r.items():
        if isinstance(v, list) and v and isinstance(v[0], dict):
            print(f"  {k}:")
            for item in v:
                print("     ", item)
        else:
            print(f"  {k}: {v}")
    return r


if __name__ == "__main__":
    if "--json" in sys.argv:
        print(json.dumps(analyze(sys.argv[1]), ensure_ascii=False, indent=1))
    else:
        show(sys.argv[1])
