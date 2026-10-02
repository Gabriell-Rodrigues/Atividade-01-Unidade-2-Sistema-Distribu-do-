import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

PASTA = Path(__file__).resolve().parent.parent / "resultados"
BANDA_MBPS = float(sys.argv[1]) if len(sys.argv) > 1 else 100.0
ORDEM = ["Sequencial", "Threads", "Pool", "P2P"]
NOMES = {"Sequencial": "Sequencial", "Threads": "Thread por cliente", "Pool": "Pool de threads", "P2P": "P2P"}


# F/us: tempo para o servidor enviar uma cópia do arquivo
def tempo_uma_copia(mb):
    return mb * 1048576 * 8 / (BANDA_MBPS * 1e6)


def ler_tempos():
    grupos = defaultdict(list)
    with open(PASTA / "tempos.csv", newline="", encoding="utf-8") as arquivo:
        for linha in csv.DictReader(arquivo):
            chave = (linha["arquitetura"], int(linha["tamanho_mb"]), int(linha["clientes"]))
            grupos[chave].append(float(linha["tempo_s"]))
    return grupos


def resumir(grupos):
    chaves = sorted(grupos, key=lambda c: (c[1], c[2], ORDEM.index(c[0])))
    resumo = []
    for arquitetura, mb, clientes in chaves:
        tempos = grupos[(arquitetura, mb, clientes)]
        resumo.append({
            "arquitetura": arquitetura,
            "tamanho_mb": mb,
            "clientes": clientes,
            "amostras": len(tempos),
            "minimo_s": round(min(tempos), 3),
            "medio_s": round(statistics.mean(tempos), 3),
            "maximo_s": round(max(tempos), 3),
        })
    with open(PASTA / "resumo.csv", "w", newline="", encoding="utf-8") as arquivo:
        escritor = csv.DictWriter(arquivo, fieldnames=list(resumo[0].keys()))
        escritor.writeheader()
        escritor.writerows(resumo)
    return resumo


def mostrar(resumo):
    print(f"{'arquitetura':<12}{'MB':>5}{'clientes':>10}{'amostras':>10}{'minimo':>10}{'medio':>10}{'maximo':>10}")
    for linha in resumo:
        print(f"{linha['arquitetura']:<12}{linha['tamanho_mb']:>5}{linha['clientes']:>10}{linha['amostras']:>10}"
              f"{linha['minimo_s']:>10.3f}{linha['medio_s']:>10.3f}{linha['maximo_s']:>10.3f}")


def graficos(resumo):
    for mb in sorted({linha["tamanho_mb"] for linha in resumo}):
        do_tamanho = [linha for linha in resumo if linha["tamanho_mb"] == mb]
        clientes = sorted({linha["clientes"] for linha in do_tamanho})
        figura, (medio, maximo) = plt.subplots(1, 2, figsize=(11, 4.2))

        for arquitetura in ORDEM:
            dados = [linha for linha in do_tamanho if linha["arquitetura"] == arquitetura]
            if not dados:
                continue
            x = [linha["clientes"] for linha in dados]
            medio.plot(x, [linha["medio_s"] for linha in dados], marker="o", label=NOMES[arquitetura])
            maximo.plot(x, [linha["maximo_s"] for linha in dados], marker="o", label=NOMES[arquitetura])

        maximo.plot(clientes, [n * tempo_uma_copia(mb) for n in clientes], "k--", linewidth=1,
                    label="Teórico cliente-servidor (N·F/us)")
        maximo.plot(clientes, [tempo_uma_copia(mb) for _ in clientes], "k:", linewidth=1,
                    label="Mínimo teórico P2P (F/us)")

        for eixo, titulo in ((medio, "Tempo médio"), (maximo, "Tempo máximo")):
            eixo.set_title(f"{titulo}, arquivo de {mb} MB")
            eixo.set_xlabel("Quantidade de clientes")
            eixo.set_ylabel("Tempo (s)")
            eixo.set_xticks(clientes)
            eixo.grid(True, alpha=0.3)
        maximo.legend(fontsize=8)
        medio.legend(fontsize=8)
        figura.tight_layout()
        figura.savefig(PASTA / f"grafico_{mb}MB.png", dpi=150)
        plt.close(figura)


def origem_p2p():
    caminho = PASTA / "origem_p2p.csv"
    if not caminho.exists():
        return
    grupos = defaultdict(list)
    with open(caminho, newline="", encoding="utf-8") as arquivo:
        for linha in csv.DictReader(arquivo):
            chave = (int(linha["tamanho_mb"]), int(linha["clientes"]))
            grupos[chave].append(100 * int(linha["pedacos_do_semeador"]) / int(linha["total_pedacos"]))
    linhas = [{"tamanho_mb": mb, "clientes": n, "percentual_medio_do_semeador": round(statistics.mean(v), 1)}
              for (mb, n), v in sorted(grupos.items())]
    with open(PASTA / "origem_resumo.csv", "w", newline="", encoding="utf-8") as arquivo:
        escritor = csv.DictWriter(arquivo, fieldnames=list(linhas[0].keys()))
        escritor.writeheader()
        escritor.writerows(linhas)
    print()
    print("pedacos recebidos do semeador no P2P (media por peer)")
    for linha in linhas:
        print(f"  {linha['tamanho_mb']:>4} MB, {linha['clientes']} peers: {linha['percentual_medio_do_semeador']}%")


if __name__ == "__main__":
    resumo = resumir(ler_tempos())
    mostrar(resumo)
    graficos(resumo)
    origem_p2p()
    print()
    print(f"resumo em {PASTA / 'resumo.csv'} e graficos em {PASTA}")
