#!/bin/bash

# Pioneer Messenger Server Setup Script
# Ubuntu 24.04

set -e

echo "=== Pioneer Server Setup ==="

# Update system
echo "Updating system..."
apt update && apt upgrade -y

# Install Java 17
echo "Installing Java 17..."
apt install -y openjdk-17-jdk

# Install PostgreSQL
echo "Installing PostgreSQL..."
apt install -y postgresql postgresql-contrib

# Start PostgreSQL
systemctl start postgresql
systemctl enable postgresql

# Configure PostgreSQL
echo "Configuring PostgreSQL..."
sudo -u postgres psql << EOF
CREATE USER pioneer WITH PASSWORD 'PioneerSecure2024!';
CREATE DATABASE pioneer OWNER pioneer;
GRANT ALL PRIVILEGES ON DATABASE pioneer TO pioneer;
\q
EOF

# Install Nginx
echo "Installing Nginx..."
apt install -y nginx

# Configure Nginx as reverse proxy
cat > /etc/nginx/sites-available/pioneer << 'NGINX'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/pioneer /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl restart nginx

# Install UFW firewall
echo "Configuring firewall..."
apt install -y ufw
ufw allow ssh
ufw allow http
ufw allow https
ufw allow 8080
ufw --force enable

# Create app directory
mkdir -p /opt/pioneer
mkdir -p /var/log/pioneer

echo "=== Server setup complete ==="
echo "Next: Upload the backend JAR file to /opt/pioneer/"
