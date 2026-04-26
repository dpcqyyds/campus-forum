#!/bin/bash
# 腾讯云服务器环境配置脚本

echo "=== 开始配置服务器环境 ==="

# 1. 更新系统
sudo apt update && sudo apt upgrade -y

# 2. 安装 Java 17
sudo apt install openjdk-17-jdk -y
java -version

# 3. 安装 MySQL 8.0
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql

# 4. 配置 MySQL
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'your_strong_password';"
sudo mysql -e "CREATE DATABASE campus_forum CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mysql -e "FLUSH PRIVILEGES;"

# 5. 安装 Nginx（反向代理）
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx

# 6. 安装 Git
sudo apt install git -y

# 7. 创建应用目录
sudo mkdir -p /opt/campus-forum
sudo chown -R $USER:$USER /opt/campus-forum

echo "=== 环境配置完成 ==="
