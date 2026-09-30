# Transferência de arquivos: cliente-servidor x P2P

Atividade 01 da Unidade 2 de Sistemas Distribuídos (COMP0470, UFS).

Avaliação de desempenho da transferência de um arquivo usando a arquitetura cliente-servidor (servidor sequencial, servidor com uma thread por cliente e servidor com pool de threads) e a arquitetura P2P, variando o tamanho do arquivo e a quantidade de clientes.

## Arquivos de teste

Os arquivos usados nos testes (5 MB, 50 MB e 500 MB) não ficam no repositório. Para gerar na pasta `arquivos`:

```bash
mvn package
java -cp target/transferencia.jar transferencia.GeradorArquivos arquivos 5 50 500
```
