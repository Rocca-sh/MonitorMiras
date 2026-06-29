#!/bin/bash

# Token de autorización
TOKEN="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIwMUtWWDREMDFSUEdUR1dWOTJTQlhGVEFWTSIsIm9yZ0lkIjoiNDQwMTAyMDA0OSIsInJvbCI6Ik9XTkVSIiwiaWF0IjoxNzgyMzE1Mzg0fQ.wfLQ4r6mxgGHsWTlvGvAyOItodK1C6yRxyk6QwWauZKQVbWOcDhF5pI8D01U9ZEM"

# Lista de canales que se desean probar (1, 3, 5, 7, 8)
CHANNELS=("00"  "01" "02" "03" "04" "05" "06" "07")

# Sip ID del DVR base
DVR_SIP_ID="44010200491110000030"

# Prefijo del canal (los primeros 18 dígitos)
CHANNEL_PREFIX="440102004913100000"

play_channel() {
    local CH=$1
    local CHANNEL_ID="${CHANNEL_PREFIX}${CH}"
    local URL="http://localhost:8080/api/dvr/play/${DVR_SIP_ID}?channelId=${CHANNEL_ID}"
    
    echo "========================================"
    echo "Solicitando canal: ${CH} (${CHANNEL_ID})"
    
    # Hacemos la petición con cURL y usamos jq para extraer específicamente el 'http_flv'
    local HTTP_FLV_LINK=$(curl -s -X GET "$URL" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Accept: application/json" | jq -r '.http_flv')

    # Validamos que se haya obtenido un link válido (que no sea null ni esté vacío)
    if [ -n "$HTTP_FLV_LINK" ] && [ "$HTTP_FLV_LINK" != "null" ]; then
        echo "Link obtenido exitosamente para el canal $CH: $HTTP_FLV_LINK"
        echo "Abriendo ffplay para el canal $CH..."
        
        # Ejecutamos ffplay en segundo plano sin buffer para sincronizacion en tiempo real
        ffplay -fflags nobuffer -flags low_delay -strict experimental -x 640 -y 360 -an -window_title "Canal $CH" "$HTTP_FLV_LINK" > /dev/null 2>&1 &
    else
        echo "Error: No se pudo obtener el link http_flv para el canal $CH"
    fi
}

echo "Iniciando prueba de reproducción simultanea de videos..."

for CH in "${CHANNELS[@]}"; do
    # Ejecutamos la funcion en segundo plano (&) para pedir todas las camaras al mismo tiempo
    play_channel "$CH" &
done

# Esperamos a que terminen todas las peticiones curl
wait

echo "========================================"
echo "Se han solicitado todas las reproducciones. Revisa las ventanas de ffplay."
echo "Para cerrar todos los reproductores después, puedes ejecutar: pkill ffplay"
