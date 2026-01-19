# MKR Server Deployment Guide

Полное руководство по настройке серверной инфраструктуры MKR на вашем собственном сервере.

## Обзор архитектуры

MKR Messaging требует следующие серверные компоненты:

```
┌─────────────────────────────────────────────────────────────┐
│                        mkr.su                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   IMAP/SMTP  │  │    WebRTC    │  │   Media      │     │
│  │              │  │              │  │   Storage    │     │
│  │  imap.mkr.su │  │  signaling   │  │ media.mkr.su │     │
│  │  smtp.mkr.su │  │  turn/stun   │  │              │     │
│  │              │  │              │  │              │     │
│  │  (сообщения) │  │ (звонки)     │  │  (файлы)     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Что нужно настроить

### 1. Почтовый сервер (IMAP/SMTP) - **ОБЯЗАТЕЛЬНО**

Для обмена сообщениями MKR использует стандартные email протоколы.

**Адреса:**
- IMAP: `imap.mkr.su:993` (SSL)
- SMTP: `smtp.mkr.su:465` (SSL)

**Варианты настройки:**

#### Вариант A: Mail-in-a-Box (Рекомендуется)

Самый простой и быстрый способ:

```bash
# На Ubuntu 20.04+ / Debian
curl -s https://mailinabox.email/setup.sh | sudo bash
```

** during setup:**
- Hostname: `mkr.su`
- Email: `admin@mkr.su`

**Mail-in-a-Box автоматически настроит:**
- ✅ Postfix (SMTP)
- ✅ Dovecot (IMAP)
- ✅ Spam фильтрация
- ✅ SSL сертификаты (Let's Encrypt)
- ✅ Web интерфейс (Roundcube)

#### Вариант B: Docker Mailserver

Если используете Docker:

```yaml
# docker-compose.yml
version: "3"

services:
  mailserver:
    image: mailserver/docker-mailserver:latest
    container_name: mailserver
    hostname: mkr.su
    ports:
      - "25:25"
      - "143:143"
      - "465:465"
      - "587:587"
      - "993:993"
    volumes:
      - ./mail-data:/var/mail
      - ./mail-config:/tmp/docker-mailserver
    environment:
      - SSL_TYPE=letsencrypt
      - PERMIT_DOCKER=network
    restart: always
```

```bash
docker-compose up -d
```

#### Вариант C: Postfix + Dovecot (Ручная настройка)

**Установка:**
```bash
sudo apt update
sudo apt install postfix dovecot-core dovecot-imapd
```

**Конфигурация Postfix (`/etc/postfix/main.cf`):**
```conf
smtpd_tls_cert_file=/etc/ssl/certs/mkr.pem
smtpd_tls_key_file=/etc/ssl/private/mkr.key
smtpd_use_tls=yes
smtpd_tls_auth_only=yes

smtpd_sasl_auth_enable = yes
smtpd_sasl_type = dovecot
smtpd_sasl_path = private/auth

smtpd_client_restrictions = permit_mynetworks, permit_sasl_authenticated
smtpd_recipient_restrictions = permit_mynetworks, permit_sasl_authenticated, reject_unauth_destination

smtpd_relay_restrictions = permit_mynetworks, permit_sasl_authenticated, defer_unauth_destination
```

**Конфигурация Dovecot (`/etc/dovecot/conf.d/10-master.conf`):**
```conf
service imap-login {
  inet_listener imaps {
    port = 993
    ssl = yes
  }
}

service auth {
  unix_listener /var/spool/postfix/private/auth {
    mode = 0666
    user = postfix
    group = postfix
  }
}
```

**Создание email пользователей:**
```bash
# Добавить пользователя
sudo adduser user1

# Создать почтовый ящик
sudo maildirmake.dovecot /etc/skel/Maildir
sudo cp -r /etc/skel/Maildir /home/user1/
sudo chown -R user1:user1 /home/user1/Maildir

# Установить пароль для SASL
sudo printf "user1@mkr.su\tpassword\n" | sudo tee -a /etc/dovecot/users
```

---

### 2. WebRTC сервер (для звонков) - **ОПЦИОНАЛЬНО**

Звонки в MKR работают через WebRTC, требующий signalling сервер и TURN/STUN.

**Адреса:**
- Signaling: `wss://signaling.mkr.su`
- TURN: `turn:mkr.su:3478`
- STUN: `stun:mkr.su:3478`

#### Вариант A: coturn (Простой TURN/STUN сервер)

```bash
# Установка
sudo apt update
sudo apt install coturn

# Конфигурация /etc/turnserver.conf
cat <<EOF | sudo tee /etc/turnserver.conf
listening-port=3478
fingerprint
lt-cred-mech
user=turnuser:your_secret_password

# TLS
cert=/etc/ssl/certs/mkr.pem
pkey=/etc/ssl/private/mkr.key
tls-port=5349

# External IP
external-ip=YOUR_SERVER_IP
realm=mkr.su

# Логи
log-file=/var/log/turnserver.log
simple-log
EOF

# Запуск
sudo systemctl enable coturn
sudo systemctl start coturn
```

**Проверка:**
```bash
turnutils_uclient -v -u turnuser -w your_secret_password turn.mkr.su
```

#### Вариант B: Janus Gateway (Полный WebRTC сервер с signaling)

```bash
# Установка зависимостей
sudo apt install libmicrohttpd-dev libjansson-dev libssl-dev \
    libsrtp2-dev libsofia-sip-ua-dev libglib2.0-dev \
    libopus-dev libogg-dev libini-config-dev pkg-config

# Сборка
git clone https://github.com/meetecho/janus-gateway.git
cd janus-gateway
sh autogen.sh
./configure --prefix=/opt/janus
make
sudo make install

# Конфигурация WebSocket для signaling
cat <<EOF | sudo tee /opt/janus/etc/janus/janus.transport.websockets.jcfg
{
  "websockets": {
    "enabled": true,
    "ws": { "port": 8188 },
    "wss": {
      "enabled": true,
      "port": 8989,
      "cert": "/etc/ssl/certs/mkr.pem",
      "key": "/etc/ssl/private/mkr.key"
    }
  }
}
EOF

# Запуск
sudo /opt/janus/bin/janus --config /opt/janus/etc/janus/janus.cfg
```

**Обновите MKRConfig.swift для Janus:**
```swift
public static let webrtcSignalingServer = "wss://signaling.mkr.su:8989"
```

---

### 3. Media Storage (для файлов) - **ОПЦИОНАЛЬНО**

Для хранения больших файлов можно использовать S3-совместимое хранилище.

**Адрес:** `https://media.mkr.su`

#### Вариант A: MinIO (S3-совместимое хранилище)

```bash
# Docker
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=mkradmin" \
  -e "MINIO_ROOT_PASSWORD=your_secure_password" \
  -v /mnt/data:/data \
  minio/minio server /data --console-address ":9001"

# Или через docker-compose
cat <<EOF > docker-compose.media.yml
version: "3"

services:
  minio:
    image: minio/minio
    container_name: mkr-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: mkradmin
      MINIO_ROOT_PASSWORD: your_secure_password
    volumes:
      - ./minio-data:/data
    command: server /data --console-address ":9001"
    restart: always

  # Nginx reverse proxy
  nginx:
    image: nginx:alpine
    container_name: mkr-nginx
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    depends_on:
      - minio
    restart: always
EOF

docker-compose -f docker-compose.media.yml up -d
```

**Nginx конфиг для media.mkr.su:**
```nginx
server {
    listen 443 ssl http2;
    server_name media.mkr.su;

    ssl_certificate /etc/nginx/ssl/mkr.pem;
    ssl_certificate_key /etc/nginx/ssl/mkr.key;

    location / {
        proxy_pass http://minio:9000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Вариант B: Простой PHP upload (если не нужно S3)

```php
// /var/www/media.mkr.su/upload.php
<?php
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES['file'])) {
    $uploadDir = '/var/www/uploads/';
    $filename = uniqid() . '_' . basename($_FILES['file']['name']);

    if (move_uploaded_file($_FILES['file']['tmp_name'], $uploadDir . $filename)) {
        echo json_encode([
            'success' => true,
            'url' => 'https://media.mkr.su/uploads/' . $filename
        ]);
    } else {
        http_response_code(500);
        echo json_encode(['error' => 'Upload failed']);
    }
}
?>
```

---

## DNS Настройка

Добавьте следующие DNS записи для `mkr.su`:

```
# Основные записи
A       @       1.2.3.4
AAAA    @       2001:db8::1

# Почтовые записи
A       imap    1.2.3.4
A       smtp    1.2.3.4
A       mail    1.2.3.4
MX      @       10  mail.mkr.su.

# WebRTC сервер
A       turn    1.2.3.4
A       signaling 1.2.3.4
SRV     _turn._udp  @  turn 3478
SRV     _turns._tcp @  turn 5349

# Media storage
A       media   1.2.3.4

# SPF (для почты)
TXT     @       "v=spf1 mx a ip4:1.2.3.4 -all"

# DKIM (сгенерируйте ключи)
default._domainkey  TXT  "v=DKIM1; k=rsa; p=YOUR_PUBLIC_KEY"

# DMARC
_dmarc  TXT        "v=DMARC1; p=quarantine; rua=mailto:dmarc@mkr.su"
```

---

## SSL Сертификаты

Используйте Let's Encrypt для бесплатных SSL сертификатов:

```bash
# Установка certbot
sudo apt install certbot

# Для Apache/Nginx
sudo certbot --nginx -d mkr.su -d imap.mkr.su -d smtp.mkr.su \
  -d turn.mkr.su -d signaling.mkr.su -d media.mkr.su

# Или только получение сертификатов
sudo certbot certonly --standalone -d mkr.su -d mail.mkr.su

# Сертификаты будут в:
# /etc/letsencrypt/live/mkr.su/fullchain.pem
# /etc/letsencrypt/live/mkr.su/privkey.pem
```

---

## Firewall Настройка

Откройте необходимые порты:

```bash
# UFW на Ubuntu
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 80/tcp      # HTTP
sudo ufw allow 443/tcp     # HTTPS
sudo ufw allow 993/tcp     # IMAPS
sudo ufw allow 465/tcp     # SMTPS
sudo ufw allow 587/tcp     # Submission
sudo ufw allow 3478/udp    # TURN
sudo ufw allow 5349/tcp    # TURNS
sudo ufw allow 8188/tcp    # Janus WebSocket
sudo ufw allow 8989/tcp    # Janus WebSocket Secure

# Включить firewall
sudo ufw enable
```

---

## Создание пользователей

### Для почтового сервера:

```bash
# Используя Mail-in-a-Browser web interface
# https://mkr.su/admin

# Или через командную строку:
sudo adduser username
sudo printf "username@mkr.su\tpassword\n" | sudo tee -a /etc/dovecot/users
```

### Для @mkr.su email адресов:

Пользователи могут регистрироваться сами через ваше веб-интерфейс или вы можете создавать их вручную.

---

## Проверка работоспособности

### Проверка IMAP:
```bash
openssl s_client -connect imap.mkr.su:993
# Должно показать: * OK Dovecot ready
```

### Проверка SMTP:
```bash
openssl s_client -connect smtp.mkr.su:465
# Должно показать: 220 mkr.su ESMTP Postfix
```

### Проверка TURN:
```bash
turnutils_uclient -v -u turnuser -w password turn.mkr.su
```

### Проверка WebRTC signaling (если Janus):
```bash
curl https://signaling.mkr.su:8989
# Должно вернуть Janus info
```

---

## Обновление MKRConfig.swift

Убедитесь, что все адреса серверов указаны правильно:

```swift
// Если вы используете другие домены или порты:
public static let imapServer = "imap.mkr.su"        // ваш IMAP сервер
public static let imapPort = 993                     // ваш порт IMAP
public static let smtpServer = "smtp.mkr.su"        // ваш SMTP сервер
public static let smtpPort = 465                     // ваш порт SMTP
public static let webrtcSignalingServer = "wss://signaling.mkr.su"  // ваш signaling
```

---

## Минимальная конфигурация

Если нужно только базовая функциональность (сообщения):

| Компонент | Обязательность | Быстрая настройка |
|-----------|---------------|-------------------|
| IMAP/SMTP | ✅ **Обязательно** | Mail-in-a-Box (5 мин) |
| WebRTC | ❌ Опционально | coturn (10 мин) |
| Media Storage | ❌ Опционально | Не нужно (файлы в письмах) |

**Для начала работы нужен только IMAP/SMTP сервер!**

---

## Мониторинг

```bash
# Логи
tail -f /var/log/mail.log              # Почта
tail -f /var/log/turnserver.log         # TURN
journalctl -u coturn -f                 # TURN (systemd)

# Статус сервисов
sudo systemctl status postfix
sudo systemctl status dovecot
sudo systemctl status coturn
```

---

## Поддержка

Для вопросов: dev@mkr.su

---

**Важно:** Начните с настройки IMAP/SMTP сервера. Это всё, что нужно для базовой работы мессенджера. Звонки и хранилище файлов можно добавить позже!
