#!/bin/bash

# MKR Messenger Server Setup Script
# Для Ubuntu 24.04

set -e

echo "🚀 Настройка MKR Messenger сервера..."

# Обновление системы
echo "📦 Обновление системы..."
apt update && apt upgrade -y

# Установка базовых пакетов
echo "🔧 Установка базовых пакетов..."
apt install -y curl wget git nginx certbot python3-certbot-nginx ufw fail2ban

# Настройка firewall
echo "🔥 Настройка firewall..."
ufw --force enable
ufw allow 22/tcp      # SSH
ufw allow 80/tcp      # HTTP
ufw allow 443/tcp     # HTTPS
ufw allow 25/tcp      # SMTP
ufw allow 587/tcp     # SMTP submission
ufw allow 465/tcp     # SMTPS
ufw allow 993/tcp     # IMAPS
ufw allow 995/tcp     # POP3S
ufw allow 3478/udp    # TURN
ufw allow 5349/tcp    # TURNS

# Установка Mail-in-a-Box
echo "📧 Установка Mail-in-a-Box..."
cd /root
curl -s https://mailinabox.email/setup.sh > setup_mail.sh
chmod +x setup_mail.sh

# Установка coturn для WebRTC
echo "📞 Установка coturn для звонков..."
apt install -y coturn

# Конфигурация coturn
cat > /etc/turnserver.conf << 'EOF'
# TURN server configuration for MKR
listening-port=3478
tls-listening-port=5349
fingerprint
lt-cred-mech
user=mkr:mkr_secret_2024
realm=kluboksrm.ru
total-quota=100
stale-nonce=600
cert=/etc/letsencrypt/live/kluboksrm.ru/fullchain.pem
pkey=/etc/letsencrypt/live/kluboksrm.ru/privkey.pem
cipher-list="ECDH+AESGCM:ECDH+CHACHA20:DH+AESGCM:ECDH+AES256:DH+AES256:ECDH+AES128:DH+AES:RSA+AESGCM:RSA+AES:!aNULL:!MD5:!DSS"
no-loopback-peers
no-multicast-peers
mobility
verbosity=2
EOF

# Включение coturn
systemctl enable coturn
systemctl start coturn

# Создание конфигурации Nginx для MKR
cat > /etc/nginx/sites-available/mkr << 'EOF'
server {
    listen 80;
    server_name kluboksrm.ru mail.kluboksrm.ru imap.kluboksrm.ru smtp.kluboksrm.ru;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name kluboksrm.ru;
    
    ssl_certificate /etc/letsencrypt/live/kluboksrm.ru/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/kluboksrm.ru/privkey.pem;
    
    # API проксирование (если есть бэкенд)
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # WebSocket для real-time
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
    
    # Статические файлы
    location / {
        root /var/www/mkr;
        try_files $uri $uri/ =404;
    }
}
EOF

# Активация сайта
ln -sf /etc/nginx/sites-available/mkr /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# Создание директории для статики
mkdir -p /var/www/mkr
echo "<h1>MKR Messenger Server</h1><p>Server is running!</p>" > /var/www/mkr/index.html

# Перезапуск nginx
systemctl reload nginx

# Установка Docker для дополнительных сервисов
echo "🐳 Установка Docker..."
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
systemctl enable docker
systemctl start docker

# Установка MinIO для файлов
echo "💾 Установка MinIO для файлов..."
docker run -d \
  --name minio \
  --restart unless-stopped \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=mkradmin" \
  -e "MINIO_ROOT_PASSWORD=MKR_Storage_2024!" \
  -v /opt/minio:/data \
  minio/minio server /data --console-address ":9001"

# Создание скрипта для получения SSL сертификатов
cat > /root/get_ssl.sh << 'EOF'
#!/bin/bash
# Получение SSL сертификатов для всех доменов
certbot --nginx -d kluboksrm.ru -d mail.kluboksrm.ru -d imap.kluboksrm.ru -d smtp.kluboksrm.ru --non-interactive --agree-tos --email admin@kluboksrm.ru

# Обновление конфигурации coturn с новыми сертификатами
systemctl restart coturn
EOF

chmod +x /root/get_ssl.sh

# Создание пользователей для тестирования
echo "👥 Создание тестовых пользователей..."
cat > /root/create_test_users.sh << 'EOF'
#!/bin/bash
# Этот скрипт нужно запустить ПОСЛЕ установки Mail-in-a-Box
# и настройки DNS записей

echo "Создание тестовых пользователей..."
echo "Запустите этот скрипт после завершения настройки Mail-in-a-Box"

# Пример создания пользователей через Mail-in-a-Box API
# curl -X POST https://kluboksrm.ru/admin/mail/users/add \
#   -d "email=test1@kluboksrm.ru" \
#   -d "password=TestPass123!" \
#   --user "admin@kluboksrm.ru:admin_password"
EOF

chmod +x /root/create_test_users.sh

echo "✅ Базовая настройка завершена!"
echo ""
echo "🔧 СЛЕДУЮЩИЕ ШАГИ:"
echo "1. Настройте DNS записи для kluboksrm.ru:"
echo "   A    kluboksrm.ru        193.111.117.137"
echo "   A    mail.kluboksrm.ru   193.111.117.137"
echo "   A    imap.kluboksrm.ru   193.111.117.137"
echo "   A    smtp.kluboksrm.ru   193.111.117.137"
echo "   MX   kluboksrm.ru        mail.kluboksrm.ru (приоритет 10)"
echo ""
echo "2. Запустите Mail-in-a-Box установку:"
echo "   cd /root && ./setup_mail.sh"
echo ""
echo "3. После настройки DNS получите SSL сертификаты:"
echo "   /root/get_ssl.sh"
echo ""
echo "4. Создайте тестовых пользователей:"
echo "   /root/create_test_users.sh"
echo ""
echo "📧 ДАННЫЕ ДЛЯ ПРИЛОЖЕНИЯ:"
echo "IMAP Server: imap.kluboksrm.ru:993 (SSL)"
echo "SMTP Server: smtp.kluboksrm.ru:465 (SSL)"
echo "TURN Server: turn:kluboksrm.ru:3478"
echo "TURN User: mkr"
echo "TURN Pass: mkr_secret_2024"
echo ""
echo "🔐 MinIO (файлы):"
echo "URL: http://193.111.117.137:9001"
echo "User: mkradmin"
echo "Pass: MKR_Storage_2024!"
echo ""
echo "Логи: /var/log/syslog, /var/log/mail.log"