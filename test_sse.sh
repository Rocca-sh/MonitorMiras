#!/bin/bash

# Token de autorizacion extraido de test_videos.sh (infinito para pruebas)
TOKEN="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIwMUtWWDREMDFSUEdUR1dWOTJTQlhGVEFWTSIsIm9yZ0lkIjoiNDQwMTAyMDA0OSIsInJvbCI6Ik9XTkVSIiwiaWF0IjoxNzgyMzE1Mzg0fQ.wfLQ4r6mxgGHsWTlvGvAyOItodK1C6yRxyk6QwWauZKQVbWOcDhF5pI8D01U9ZEM"

URL="http://localhost:8080/api/view/dvr-sse/stream"

echo "=========================================================="
echo " Suscribiendose a eventos SSE del DVR y GPS en tiempo real"
echo " URL: $URL"
echo " Presiona Ctrl+C para terminar la conexion"
echo "=========================================================="

# Utilizamos curl con -N (--no-buffer) para imprimir la salida del servidor en tiempo real.
# No terminara hasta que el servidor cierre la conexion.
curl -N -s -X GET "$URL" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: text/event-stream"
