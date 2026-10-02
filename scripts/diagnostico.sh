#!/bin/bash
set -u
cd "$(dirname "$0")/.." || exit 1

MB=${1:-500}
PEERS=${2:-8}
export BANDA_MBPS=${BANDA_MBPS:-100}
export ARQUIVO="arquivo_${MB}MB.bin"

PERFIS="--profile cs --profile p2p --profile arquivos"
SAIDA=resultados/diagnostico
mkdir -p "$SAIDA"

esperar() {
  local servico=$1 texto=$2 quantidade=$3 limite=$4
  local fim=$(( $(date +%s) + limite ))
  while [ "$(docker compose $PERFIS logs --no-color "$servico" 2>/dev/null | grep -c "$texto")" -lt "$quantidade" ]; do
    [ "$(date +%s)" -ge "$fim" ] && return 1
    sleep 1
  done
}

docker compose $PERFIS down --remove-orphans > /dev/null 2>&1
docker compose up -d tracker semeador > /dev/null 2>&1
esperar semeador "semeando" 1 120 || exit 1

export INICIO_MS=$(( $(date +%s%3N) + 5000 ))
docker compose up -d --scale peer="$PEERS" peer > /dev/null 2>&1

# processos bloqueados (b), escrita no disco (bo) e espera de E/S (wa) a cada 2 s
vmstat -w 2 > "$SAIDA/vmstat_${MB}MB_${PEERS}peers.log" &
VMSTAT=$!
esperar peer " recebeu " "$PEERS" 900
kill $VMSTAT

docker compose $PERFIS logs --no-color peer | grep " recebeu " > "$SAIDA/tempos_${MB}MB_${PEERS}peers.log"
cat "$SAIDA/tempos_${MB}MB_${PEERS}peers.log"
docker compose $PERFIS down --remove-orphans > /dev/null 2>&1
