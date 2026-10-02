#!/bin/bash
set -u
cd "$(dirname "$0")/.." || exit 1

TAMANHOS=${TAMANHOS:-"5 50 500"}
CLIENTES=${CLIENTES:-"1 2 4 8"}
ARQUITETURAS=${ARQUITETURAS:-"Sequencial Threads Pool P2P"}
REPETICOES=${REPETICOES:-3}
export BANDA_MBPS=${BANDA_MBPS:-100}
export MAXIMO=${POOL:-2}

PERFIS="--profile cs --profile p2p --profile arquivos"
TEMPOS=resultados/tempos.csv
ORIGEM=resultados/origem_p2p.csv
mkdir -p resultados/logs
[ -f "$TEMPOS" ] || echo "arquitetura,tamanho_mb,clientes,repeticao,no,tempo_s" > "$TEMPOS"
[ -f "$ORIGEM" ] || echo "tamanho_mb,clientes,repeticao,peer,pedacos_do_semeador,total_pedacos" > "$ORIGEM"

agora_ms() {
  date +%s%3N
}

esperar_log() {
  local servico=$1 texto=$2 quantidade=$3 limite=$4
  local inicio
  inicio=$(date +%s)
  while [ $(( $(date +%s) - inicio )) -lt "$limite" ]; do
    if [ "$(docker compose $PERFIS logs --no-color "$servico" 2>/dev/null | grep -c "$texto")" -ge "$quantidade" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

registrar_tempos() {
  grep -oE "[^ ]+ recebeu [0-9]+ bytes em [0-9.]+ s" "$5" \
    | awk -v a="$1" -v m="$2" -v n="$3" -v r="$4" '{print a "," m "," n "," r "," $1 "," $6}' >> "$TEMPOS"
}

rodar_cs() {
  local arquitetura=$1 n=$2 log=$3 limite=$4
  MODO=$arquitetura docker compose up -d servidor > "$log" 2>&1
  if ! esperar_log servidor "na porta" 1 60; then
    docker compose $PERFIS logs --no-color servidor >> "$log" 2>&1
    return 1
  fi
  export INICIO_MS=$(( $(agora_ms) + 10000 ))
  timeout "$limite" docker compose up --scale cliente="$n" cliente >> "$log" 2>&1
  docker compose $PERFIS logs --no-color servidor >> "$log" 2>&1
  [ "$(grep -cE 'recebeu [0-9]+ bytes' "$log")" -eq "$n" ]
}

rodar_p2p() {
  local mb=$1 n=$2 repeticao=$3 log=$4 limite=$5
  local terminou=0 ip_semeador
  docker compose up -d tracker semeador > "$log.up" 2>&1
  if ! esperar_log semeador "semeando" 1 120; then
    cat "$log.up" > "$log"
    docker compose $PERFIS logs --no-color tracker semeador >> "$log" 2>&1
    rm -f "$log.up"
    return 1
  fi
  export INICIO_MS=$(( $(agora_ms) + 10000 ))
  docker compose up -d --scale peer="$n" peer >> "$log.up" 2>&1
  esperar_log peer "recebeu" "$n" "$limite" && terminou=1
  cat "$log.up" > "$log"
  rm -f "$log.up"
  docker compose $PERFIS logs --no-color tracker semeador peer >> "$log" 2>&1
  [ "$terminou" -eq 1 ] || return 1

  ip_semeador=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
    "$(docker compose $PERFIS ps -q semeador)")
  grep -oE "[^ ]+ origem dos pedacos: \{[^}]*\}" "$log" \
    | awk -v m="$mb" -v n="$n" -v r="$repeticao" -v s="$ip_semeador:" '{
        lista = $0
        sub(/.*\{/, "", lista)
        sub(/\}.*/, "", lista)
        quantidade = split(lista, itens, ", ")
        total = 0
        do_semeador = 0
        for (i = 1; i <= quantidade; i++) {
          split(itens[i], par, "=")
          total += par[2]
          if (index(par[1], s) == 1) do_semeador += par[2]
        }
        print m "," n "," r "," $1 "," do_semeador "," total
      }' >> "$ORIGEM"
}

echo "gerando a imagem e os arquivos de teste"
docker compose $PERFIS down --remove-orphans > /dev/null 2>&1
docker compose $PERFIS build gerador > /dev/null || exit 1
docker compose run --rm gerador || exit 1

total=$(( REPETICOES * $(echo $TAMANHOS | wc -w) * $(echo $CLIENTES | wc -w) * $(echo $ARQUITETURAS | wc -w) ))
for passada in 1 2; do
  falhas=0
  atual=0
  for repeticao in $(seq 1 "$REPETICOES"); do
    for mb in $TAMANHOS; do
      for n in $CLIENTES; do
        for arquitetura in $ARQUITETURAS; do
          atual=$(( atual + 1 ))
          nome="${arquitetura}_${mb}MB_${n}clientes_rep${repeticao}"
          if grep -q "^$arquitetura,$mb,$n,$repeticao," "$TEMPOS"; then
            continue
          fi

          export ARQUIVO="arquivo_${mb}MB.bin"
          limite=$(awk -v m="$mb" -v n="$n" -v b="$BANDA_MBPS" 'BEGIN { printf "%d", 3 * n * m * 1048576 * 8 / (b * 1e6) + 90 }')
          log="resultados/logs/$nome.log"
          echo "[$atual/$total] $nome $(date '+%H:%M:%S')"

          docker compose $PERFIS down --remove-orphans > /dev/null 2>&1
          if [ "$arquitetura" = "P2P" ]; then
            rodar_p2p "$mb" "$n" "$repeticao" "$log" "$limite"
          else
            rodar_cs "$arquitetura" "$n" "$log" "$limite"
          fi
          if [ $? -eq 0 ]; then
            registrar_tempos "$arquitetura" "$mb" "$n" "$repeticao" "$log"
          else
            falhas=$(( falhas + 1 ))
            echo "  falhou, veja $log"
          fi
          docker compose $PERFIS down --remove-orphans > /dev/null 2>&1
        done
      done
    done
  done
  [ "$falhas" -eq 0 ] && break
  echo "$falhas experimento(s) falharam, tentando de novo"
done

echo "fim dos experimentos, tempos em $TEMPOS"
