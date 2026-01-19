# MKR Messaging Server Setup Guide

Это руководство по настройке серверной инфраструктуры для MKR Messaging приложения.

## Обзор архитектуры

MKR Messaging использует следующие серверные компоненты:

1. **IMAP Server** - для получения и хранения сообщений
2. **SMTP Server** - для отправки сообщений
3. **WebRTC Signaling Server** - для установления голосовых и видеозвонков
4. **TURN/STUN Server** - для пробивания NAT при звонках
5. **Media Storage Server** (опционально) - для хранения больших файлов

---

## 1. IMAP/SMTP Server Setup

### Рекомендуемые решения:

#### Вариант A: Mail-in-a-Box (Простой и надёжный)
```bash
# На Ubuntu 20.04+ / Debian
curl -s https://mailinabox.email/setup.sh | sudo bash
```

Mail-in-a-Box автоматически настроит:
- Postfix (SMTP)
- Dovecot (IMAP)
- Spam filtering
- SSL сертификаты (Let's Encrypt)
- Веб-интерфейс администрирования

#### Вариант B: Postfix + Dovecot (Ручная настройка)

**Установка Postfix (SMTP):**
```bash
sudo apt update
sudo apt install postfix postfix-pcre
```

**Установка Dovecot (IMAP):**
```bash
sudo apt install dovecot-core dovecot-imapd
```

**Конфигурация для MKR (`/etc/postfix/main.cf`):**
```
smtpd_tls_cert_file=/etc/ssl/certs/mkr.pem
smtpd_tls_key_file=/etc/ssl/private/mkr.key
smtpd_use_tls=yes
smtpd_tls_auth_only=yes

# Разрешить аутентификацию
smtpd_sasl_auth_enable = yes
smtpd_sasl_type = dovecot
smtpd_sasl_path = private/auth

# Ограничения для безопасности
smtpd_client_restrictions = permit_mynetworks, permit_sasl_authenticated
smtpd_recipient_restrictions = permit_mynetworks, permit_sasl_authenticated, reject_unauth_destination
```

**Конфигурация Dovecot (`/etc/dovecot/conf.d/10-master.conf`):**
```
service imap-login {
  inet_listener imap {
    port = 0  # Отключить незашифрованный IMAP
  }
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

#### Вариант C: Облачные решения
- **Google Workspace**: workspace.google.com
- **Microsoft 365**: microsoft.com/microsoft-365
- **Zoho Mail**: zoho.com/mail

---

## 2. WebRTC Signaling Server

WebRTC требует сигнальный сервер для координации соединения между участниками звонка.

### Вариант A: coturn (TURN + Signaling)

**Установка:**
```bash
sudo apt install coturn
```

**Конфигурация (`/etc/turnserver.conf`):**
```
# Сетевые настройки
listening-port=3478
fingerprint
lt-cred-mech
user=mkr:YOUR_SECRET_KEY

# TLS/DTLS
cert=/etc/ssl/certs/mkr.pem
pkey=/etc/ssl/private/mkr.key
tls-port=5349

# STUN/TURN сервера
external-ip=YOUR_SERVER_IP
realm=mkr.su

# WebRTC signaling
use-auth-secret
static-auth-secret=YOUR_STATIC_SECRET
```

**Запуск:**
```bash
sudo systemctl enable coturn
sudo systemctl start coturn
```

### Вариант B: Janus Gateway (Больше функций)

```bash
# Установка зависимостей
sudo apt install libmicrohttpd-dev libjansson-dev libssl-dev libsrtp2-dev \
    libsofia-sip-ua-dev libglib2.0-dev libopus-dev libogg-dev libini-config-dev \
    libcollection-dev libconfig-dev pkg-config gengetopt libtool automake

# Сборка Janus
git clone https://github.com/meetecho/janus-gateway.git
cd janus-gateway
sh autogen.sh
./configure --prefix=/opt/janus
make
sudo make install
```

**Конфигурация WebSocket для WebRTC (`/opt/janus/etc/janus/janus.transport.websockets.jcfg`):**
```json
{
  "websockets": {
    "enabled": true,
    "ws": {
      "enabled": true,
      "port": 8188
    },
    "wss": {
      "enabled": true,
      "port": 8989,
      "cert": "/etc/ssl/certs/mkr.pem",
      "key": "/etc/ssl/private/mkr.key"
    }
  }
}
```

---

## 3. Media Storage Server (Опционально)

Для хранения больших файлов (видео, документы) можно использовать:

### MinIO (S3-совместимое хранилище)

**Docker установка:**
```bash
docker run -d \
  -p 9000:9000 \
  -p 9001:9001 \
  --name minio \
  -e "MINIO_ROOT_USER=mkradmin" \
  -e "MINIO_ROOT_PASSWORD=YOUR_SECURE_PASSWORD" \
  -v /mnt/data:/data \
  minio/minio server /data --console-address ":9001"
```

**Конфигурация в MKRConfig.swift:**
```swift
public static let mediaStorageServer = "https://media.mkr.su"
public static let mediaUploadEndpoint = "\(mediaStorageServer)/api/upload"
public static let mediaDownloadEndpoint = "\(mediaStorageServer)/api/download"
```

---

## 4. DNS Настройка

Добавьте следующие DNS записи для `mkr.su`:

```
# Почтовые записи
A       imap       1.2.3.4
A       smtp       1.2.3.4
A       mail       1.2.3.4
AAAA    imap       2001:db8::1
AAAA    smtp       2001:db8::1
AAAA    mail       2001:db8::1

# MX записи
@       MX 10      mail.mkr.su.

# WebRTC/TURN сервер
A       turn       1.2.3.4
A       signaling  1.2.3.4
SRV     _turn._udp  turn 3478
SRV     _turns._tcp turn 5349

# Media storage
A       media      1.2.3.4

# SPF для почты
@       TXT       "v=spf1 mx a ip4:1.2.3.4 -all"

# DKIM (сгенерируйте ключи)
default._domainkey  TXT  "v=DKIM1; k=rsa; p=YOUR_PUBLIC_KEY"

# DMARC
_dmarc  TXT        "v=DMARC1; p=quarantine; rua=mailto:dmarc@mkr.su"
```

---

## 5. SSL Сертификаты

Используйте Let's Encrypt для бесплатных SSL сертификатов:

```bash
sudo apt install certbot

# Для Apache/Nginx
sudo certbot --apache -d mkr.su -d mail.mkr.su -d imap.mkr.su -d smtp.mkr.su

# Только получение сертификатов
sudo certbot certonly --standalone -d mkr.su -d mail.mkr.su
```

---

## 6. Настройка приложения

Обновите файл `MKRConfig.swift` с реальными значениями:

```swift
public struct MKRConfig {
    // IMAP/SMTP
    public static let imapServer = "imap.mkr.su"
    public static let imapPort = 993
    public static let imapSecurity = "ssl"

    public static let smtpServer = "smtp.mkr.su"
    public static let smtpPort = 465
    public static let smtpSecurity = "ssl"

    // WebRTC
    public static let webrtcSignalingServer = "wss://signaling.mkr.su:8989"
    public static let webrtcTurnServer = "turn:mkr.su:3478"
    public static let webrtcStunServer = "stun:mkr.su:3478"

    // TURN credentials
    public static let turnUsername = "mkr"
    public static let turnPassword = "YOUR_SECRET_KEY"
}
```

---

## 7. Firewall Настройка

Откройте необходимые порты:

```bash
# UFW на Ubuntu
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 80/tcp      # HTTP
sudo ufw allow 443/tcp     # HTTPS
sudo ufw allow 993/tcp     # IMAPS
sudo ufw allow 465/tcp     # SMTPS
sudo ufw allow 587/tcp     # Submission (SMTP с TLS)
sudo ufw allow 3478/udp    # TURN
sudo ufw allow 5349/tcp    # TURNS
sudo ufw allow 8188/tcp    # Janus WebSocket
sudo ufw allow 8989/tcp    # Janus WebSocket Secure
```

---

## 8. Мониторинг

### Логи

- **Postfix**: `/var/log/mail.log`
- **Dovecot**: `/var/log/mail.log`
- **Coturn**: `/var/log/turnserver.log`
- **Janus**: `/opt/janus/logs/`

### Инструменты мониторинга

- **Netdata** для реального мониторинга
- **Prometheus + Grafana** для графиков
- **fail2ban** для защиты от брутфорса

---

## 9. Проверка работоспособности

### Проверка IMAP:
```bash
telnet imap.mkr.su 993
openssl s_client -connect imap.mkr.su:993
```

### Проверка SMTP:
```bash
telnet smtp.mkr.su 465
openssl s_client -connect smtp.mkr.su:465
```

### Проверка TURN:
```bash
turnutils_uclient -v -u mkr -w YOUR_SECRET_KEY turn.mkr.su
```

---

## 10. Безопасность

1. **Обязательные меры:**
   - Используйте надежные пароли
   - Включите двухфакторную аутентификацию для админ-панелей
   - Регулярно обновляйте ПО
   - Настройте автоматические бэкапы

2. **Рекомендации:**
   - Используйте Fail2ban для защиты от брутфорса
   - Настройте rate limiting
   - Логируйте все попытки авторизации
   - Используйте отдельные серверы для продакшена и тестов

---

## Дополнительные ресурсы

- [Mail-in-a-Box Documentation](https://mailinabox.email/)
- [Postfix Documentation](https://www.postfix.org/documentation.html)
- [Dovecot Manual](https://wiki.dovecot.org/)
- [coturn Documentation](https://github.com/coturn/coturn)
- [Janus Gateway](https://janus.conf.meetecho.com/)
- [Let's Encrypt](https://letsencrypt.org/docs/)

---

## Поддержка

Для вопросов и проблем: support@mkr.su
