# Transferência de arquivos: cliente-servidor x P2P

Atividade 01 da Unidade 2 de Sistemas Distribuídos (COMP0470, UFS).

Avaliação de desempenho da transferência de um arquivo usando a arquitetura cliente-servidor (servidor sequencial, servidor com uma thread por cliente e servidor com pool de threads) e a arquitetura P2P, variando o tamanho do arquivo e a quantidade de clientes.

## Arquivos de teste

Os arquivos usados nos testes (5 MB, 50 MB e 500 MB) não ficam no repositório. Para gerar na pasta `arquivos`:

```bash
mvn package
java -cp target/transferencia.jar transferencia.GeradorArquivos arquivos 5 50 500
```

## Cliente-servidor

O servidor envia o tamanho do arquivo e depois o conteúdo. O cliente recebe tudo, descarta os bytes e mostra o tempo desde a conexão até o último byte.

Servidor sequencial (atende um cliente por vez):

```bash
java -cp target/transferencia.jar transferencia.ServidorSequencial 5000 arquivos/arquivo_50MB.bin
```

Servidor com uma thread por cliente (atende todos ao mesmo tempo):

```bash
java -cp target/transferencia.jar transferencia.ServidorThreads 5000 arquivos/arquivo_50MB.bin
```

Servidor com pool de threads (atende no máximo N clientes ao mesmo tempo, aqui N = 4):

```bash
java -cp target/transferencia.jar transferencia.ServidorPool 5000 arquivos/arquivo_50MB.bin 4
```

Cliente:

```bash
java -cp target/transferencia.jar transferencia.Cliente localhost 5000 cliente1
```

## P2P

O arquivo é dividido em pedaços de 256 KB. O tracker guarda a lista de peers e o tamanho do arquivo. Cada peer abre uma conexão com cada outro peer, pede o mapa dos pedaços que ele tem e baixa, em paralelo, pedaços escolhidos ao acaso entre os que ainda faltam. Ao mesmo tempo, envia para os outros os pedaços que já tem. Quando termina, mostra de quais peers recebeu os pedaços e continua compartilhando o arquivo.

Tracker:

```bash
java -cp target/transferencia.jar transferencia.Tracker 6000
```

Peer que começa com o arquivo completo (semeador):

```bash
java -cp target/transferencia.jar transferencia.Peer localhost 6000 7000 semeador arquivos/arquivo_50MB.bin
```

Peers que baixam o arquivo (cada um em uma porta):

```bash
java -cp target/transferencia.jar transferencia.Peer localhost 6000 7001 peer1
java -cp target/transferencia.jar transferencia.Peer localhost 6000 7002 peer2
```

## Limite de banda

A variável de ambiente `BANDA_MBPS` limita o upload de cada servidor e de cada peer (em Mbit/s). Todas as threads de um mesmo processo dividem esse limite, como se fosse a placa de rede do nó. Sem a variável não há limite.

## Docker

Cada servidor, cliente e peer roda em um container, com upload limitado a 100 Mbit/s por padrão. Sem esse limite, como os containers estão na mesma máquina, a transferência seria quase instantânea e não daria para comparar as arquiteturas.

Gerar a imagem e os arquivos de teste (uma vez):

```bash
docker compose run --rm gerador
```

Cliente-servidor (`MODO` pode ser `Sequencial`, `Threads` ou `Pool`; `MAXIMO` é o tamanho do pool):

```bash
MODO=Threads ARQUIVO=arquivo_50MB.bin docker compose up -d servidor
docker compose up --scale cliente=4 cliente
docker compose --profile cs down
```

P2P:

```bash
ARQUIVO=arquivo_50MB.bin docker compose up -d tracker semeador
docker compose up -d --scale peer=4 peer
docker compose logs -f peer
docker compose --profile p2p down
```

No PowerShell, as variáveis são definidas antes do comando com `$env:MODO="Threads"`, `$env:ARQUIVO="arquivo_50MB.bin"` e `$env:BANDA_MBPS="100"`.

## Experimentos

O script `scripts/experimentos.sh` (bash, com Docker) roda todas as combinações de arquitetura (Sequencial, Threads, Pool com N = 2 e P2P), tamanho do arquivo (5, 50 e 500 MB) e quantidade de clientes (1, 2, 4 e 8), com 3 repetições e upload de 100 Mbit/s em cada nó. Em cada experimento os clientes (ou peers) começam no mesmo instante. O tempo de cada cliente fica em `resultados/tempos.csv` e, no P2P, a quantidade de pedaços que cada peer recebeu do semeador fica em `resultados/origem_p2p.csv`. Se o script for interrompido, ao rodar de novo ele continua de onde parou.

```bash
bash scripts/experimentos.sh
```

Os parâmetros podem ser trocados por variáveis de ambiente, por exemplo para uma rodada rápida:

```bash
TAMANHOS=5 CLIENTES="1 2" REPETICOES=1 bash scripts/experimentos.sh
```

Depois, o script de análise calcula o tempo mínimo, médio e máximo de cada experimento (`resultados/resumo.csv`) e gera os gráficos (`resultados/grafico_5MB.png`, `grafico_50MB.png` e `grafico_500MB.png`). Precisa do matplotlib.

```bash
python scripts/analisar.py
```
