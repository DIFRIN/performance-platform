#!/bin/sh
set -eu
umask 077

out=${1:-distribution/example/tls/generated}
if [ -e "$out" ]; then
    echo "Refusing to overwrite existing certificate directory: $out" >&2
    exit 1
fi
mkdir -p "$out"
out=$(cd "$out" && pwd)
password=${TLS_TEST_STORE_PASSWORD:-changeit}

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 7 \
    -subj "/CN=Platform test CA" -keyout "$out/ca.key" -out "$out/ca.crt"
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 7 \
    -subj "/CN=Untrusted test CA" -keyout "$out/rogue-ca.key" -out "$out/rogue-ca.crt"

cat > "$out/extensions.cnf" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=DNS:localhost,DNS:orchestrator,DNS:agent,DNS:kafka,DNS:rabbitmq,IP:127.0.0.1
EOF

for identity in server agent ui rogue; do
    ca=ca
    if [ "$identity" = rogue ]; then ca=rogue-ca; fi
    openssl req -new -newkey rsa:2048 -nodes -sha256 \
        -subj "/CN=$identity" -keyout "$out/$identity.key" -out "$out/$identity.csr"
    openssl x509 -req -sha256 -days 7 -in "$out/$identity.csr" \
        -CA "$out/$ca.crt" -CAkey "$out/$ca.key" -CAcreateserial \
        -extfile "$out/extensions.cnf" -out "$out/$identity.crt"
    openssl pkcs12 -export -name "$identity" \
        -inkey "$out/$identity.key" -in "$out/$identity.crt" -certfile "$out/$ca.crt" \
        -out "$out/$identity.p12" -passout "pass:$password"
done

keytool -importcert -noprompt -alias test-ca -storetype PKCS12 \
    -file "$out/ca.crt" -keystore "$out/truststore.p12" -storepass "$password"
chmod 644 "$out/ca.crt" "$out/rogue-ca.crt" \
    "$out/server.crt" "$out/agent.crt" "$out/ui.crt" "$out/rogue.crt"
echo "Test certificates created in $out (expire in 7 days; never use in production)."
