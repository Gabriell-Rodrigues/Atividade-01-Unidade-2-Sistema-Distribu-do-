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

O arquivo é dividido em pedaços de 256 KB. O tracker guarda a lista de peers e o tamanho do arquivo. Cada peer baixa os pedaços dos outros peers e, ao mesmo tempo, envia para os outros os pedaços que já tem. Quando termina, continua compartilhando o arquivo.

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
