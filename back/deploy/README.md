# 腾讯云部署完整指南（超详细版）

> 本指南适合零基础的同学，每一步都有详细说明。预计首次部署需要 2-3 小时。

---

## 一、准备工作（预计 30 分钟）

### 1.1 购买腾讯云服务器

#### 步骤1：注册腾讯云账号
1. 访问 https://cloud.tencent.com/
2. 点击右上角"注册"
3. 完成实名认证（需要身份证）
4. 学生可以申请学生优惠（约 10 元/月）

#### 步骤2：购买云服务器 CVM
1. 进入控制台 → 云服务器 CVM → 立即购买
2. **计费模式**：按量计费（灵活）或包年包月（便宜）
3. **地域**：选择离你最近的
   - 北京：华北地区用户
   - 上海：华东地区用户
   - 广州：华南地区用户
   - 成都：西南地区用户
4. **机型配置**：
   - 实例类型：标准型 S5
   - CPU：2核
   - 内存：4GB
   - 系统盘：50GB（高性能云硬盘）
5. **镜像**：
   - 公共镜像 → Ubuntu → **Ubuntu Server 22.04 LTS 64位**
6. **网络**：
   - 带宽：3Mbps（够用）或 5Mbps（更流畅）
   - 分配免费公网 IP：勾选
7. **安全组**：
   - 新建安全组
   - 勾选：放通 22、80、443、3389 端口
8. **登录方式**：
   - 设置密码（推荐）
   - 密码要求：大小写字母+数字+特殊字符，至少8位
   - **务必记住这个密码！**
9. 确认订单，支付

#### 步骤3：获取服务器信息
购买完成后，在控制台可以看到：
- **公网 IP**：例如 `123.456.789.10`（这是你服务器的地址）
- **内网 IP**：例如 `10.0.0.5`（内部通信用）
- **登录用户名**：Ubuntu 系统默认是 `ubuntu`，但腾讯云默认是 `root`

**记下你的公网 IP，后面会用到！**

---

### 1.2 连接到服务器

#### Windows 用户（推荐使用 PowerShell）

**方法1：使用 PowerShell（Windows 10/11 自带）**

1. 按 `Win + X`，选择"Windows PowerShell"或"终端"
2. 输入连接命令：
   ```bash
   ssh root@你的公网IP
   # 例如：ssh root@123.456.789.10
   ```
3. 首次连接会提示：
   ```
   Are you sure you want to continue connecting (yes/no)?
   ```
   输入 `yes` 回车
4. 输入你购买服务器时设置的密码（输入时不显示，正常输入后回车即可）
5. 看到类似这样的提示，说明连接成功：
   ```
   root@VM-0-5-ubuntu:~#
   ```

**方法2：使用 PuTTY（如果 PowerShell 不行）**

1. 下载 PuTTY：https://www.putty.org/
2. 打开 PuTTY
3. Host Name 填写：你的公网IP
4. Port 填写：22
5. Connection type 选择：SSH
6. 点击 Open
7. 输入用户名：`root`
8. 输入密码

**方法3：使用腾讯云网页控制台（最简单）**

1. 在腾讯云控制台找到你的服务器
2. 点击"登录"按钮
3. 选择"标准登录"
4. 在网页中直接操作

---

#### 首次登录后的安全设置

```bash
# 1. 修改 root 密码（可选，但推荐）
passwd
# 输入新密码两次

# 2. 更新系统（重要！）
apt update
apt upgrade -y
# 这个过程可能需要 5-10 分钟，耐心等待

# 3. 查看系统信息
uname -a
# 应该显示 Ubuntu 22.04

# 4. 查看磁盘空间
df -h
# 确保有足够空间（至少 10GB 可用）

# 5. 查看内存
free -h
# 确保有 4GB 内存
```

---

### 1.3 配置安全组（重要！）

如果购买时没有配置，需要手动配置：

1. 进入腾讯云控制台 → 云服务器 → 安全组
2. 找到你的服务器绑定的安全组
3. 点击"修改规则"
4. 添加入站规则：

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 22 | 0.0.0.0/0 | SSH 登录 |
| TCP | 80 | 0.0.0.0/0 | HTTP 访问 |
| TCP | 443 | 0.0.0.0/0 | HTTPS 访问 |
| TCP | 8080 | 0.0.0.0/0 | 后端调试（可选，生产环境建议删除）|

**注意**：不要开放 3306（MySQL）端口，避免被攻击！

---

### 1.4 准备本地文件

在开始部署前，确保你本地有这些文件：

```
C:\Users\25366\IdeaProjects\back\
├── target\project-0.0.1-SNAPSHOT.jar  （需要先打包）
├── deploy\
│   ├── server-setup.sh
│   ├── application-prod.properties
│   ├── deploy-backend.sh
│   ├── nginx.conf
│   └── README.md（你正在看的文件）
└── campus_forum.sql  （数据库备份文件）

C:\Users\25366\Desktop\project\
└── dist\  （需要先构建前端）
```

**如果没有这些文件，先执行：**

```bash
# 打包后端
cd C:\Users\25366\IdeaProjects\back
mvnw clean package -DskipTests

# 构建前端
cd C:\Users\25366\Desktop\project
npm run build
```

---

## 二、服务器环境配置（预计 20 分钟）

### 2.1 上传配置脚本到服务器

**方法1：使用 SCP 命令（推荐）**

在本地 Windows PowerShell 中执行：

```bash
# 进入部署文件目录
cd C:\Users\25366\IdeaProjects\back\deploy

# 上传脚本到服务器
scp server-setup.sh root@你的公网IP:/root/
# 例如：scp server-setup.sh root@123.456.789.10:/root/

# 输入服务器密码
```

**如果 scp 命令不存在，使用方法2**

**方法2：使用 WinSCP（图形化工具）**

1. 下载 WinSCP：https://winscp.net/
2. 安装并打开
3. 新建站点：
   - 文件协议：SFTP
   - 主机名：你的公网IP
   - 端口：22
   - 用户名：root
   - 密码：你的服务器密码
4. 点击"登录"
5. 左边是本地文件，右边是服务器文件
6. 找到 `server-setup.sh`，拖拽到右边的 `/root/` 目录

**方法3：复制粘贴（适合小文件）**

1. 在本地打开 `server-setup.sh`，复制全部内容
2. 在服务器上执行：
   ```bash
   nano /root/server-setup.sh
   ```
3. 粘贴内容（右键粘贴）
4. 按 `Ctrl + X`，然后按 `Y`，再按 `Enter` 保存

---

### 2.2 执行环境配置脚本

连接到服务器后，执行：

```bash
# 1. 进入 root 目录
cd /root

# 2. 查看文件是否上传成功
ls -lh server-setup.sh
# 应该显示文件信息

# 3. 给脚本添加执行权限
chmod +x server-setup.sh

# 4. 执行脚本（这个过程需要 10-15 分钟）
./server-setup.sh
```

**脚本会自动安装：**
- ✅ Java 17（运行后端）
- ✅ MySQL 8.0（数据库）
- ✅ Nginx（Web 服务器）
- ✅ Git（版本控制）
- ✅ 创建应用目录

**执行过程中可能会提示：**
```
Do you want to continue? [Y/n]
```
输入 `Y` 回车即可

**安装完成后，验证：**

```bash
# 检查 Java 版本
java -version
# 应该显示：openjdk version "17.x.x"

# 检查 MySQL 状态
systemctl status mysql
# 应该显示：active (running)
# 按 q 退出

# 检查 Nginx 状态
systemctl status nginx
# 应该显示：active (running)

# 检查应用目录
ls -la /opt/campus-forum
# 应该显示目录已创建
```

---

### 2.3 配置 MySQL 数据库

#### 步骤1：设置 MySQL root 密码

```bash
# 登录 MySQL（首次登录可能不需要密码）
sudo mysql

# 或者如果需要密码
sudo mysql -u root -p
```

进入 MySQL 后，你会看到 `mysql>` 提示符。

#### 步骤2：修改 root 密码

```sql
-- 修改 root 密码（改成你自己的强密码）
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'YourStrongPassword123!';

-- 刷新权限
FLUSH PRIVILEGES;

-- 退出
EXIT;
```

**重要提示：**
- 密码要包含大小写字母、数字、特殊字符
- 至少 12 位
- **记住这个密码！后面配置文件要用**

#### 步骤3：创建数据库

```bash
# 重新登录 MySQL（使用新密码）
mysql -u root -p
# 输入刚才设置的密码
```

```sql
-- 创建数据库（指定字符集）
CREATE DATABASE campus_forum 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 查看数据库
SHOW DATABASES;
-- 应该能看到 campus_forum

-- 退出
EXIT;
```

#### 步骤4：创建专用数据库用户（推荐，更安全）

```bash
mysql -u root -p
```

```sql
-- 创建专用用户
CREATE USER 'campus_user'@'localhost' IDENTIFIED BY 'AnotherStrongPassword456!';

-- 授予权限（只能访问 campus_forum 数据库）
GRANT ALL PRIVILEGES ON campus_forum.* TO 'campus_user'@'localhost';

-- 刷新权限
FLUSH PRIVILEGES;

-- 查看用户
SELECT user, host FROM mysql.user;

-- 退出
EXIT;
```

**测试新用户：**

```bash
# 使用新用户登录
mysql -u campus_user -p
# 输入 campus_user 的密码

# 查看数据库
SHOW DATABASES;
# 应该能看到 campus_forum

# 退出
EXIT;
```

---

### 2.4 导入数据库结构和数据

#### 方法1：导入完整备份（包含测试数据）

```bash
# 1. 上传数据库文件到服务器
# 在本地 PowerShell 执行：
scp C:\Users\25366\IdeaProjects\back\campus_forum.sql root@你的公网IP:/tmp/

# 2. 在服务器上导入
mysql -u root -p campus_forum < /tmp/campus_forum.sql
# 输入 root 密码

# 3. 验证导入
mysql -u root -p -e "USE campus_forum; SHOW TABLES;"
# 应该显示所有表：users, posts, boards, 等等

# 4. 查看数据量
mysql -u root -p -e "USE campus_forum; SELECT COUNT(*) FROM posts;"
# 应该显示帖子数量
```

#### 方法2：只导入表结构（不导入测试数据）

```bash
# 1. 上传 schema.sql
scp C:\Users\25366\IdeaProjects\back\src\main\resources\schema.sql root@你的公网IP:/tmp/

# 2. 导入表结构
mysql -u root -p campus_forum < /tmp/schema.sql

# 3. 导入初始数据（如果有 data.sql）
scp C:\Users\25366\IdeaProjects\back\src\main\resources\data.sql root@你的公网IP:/tmp/
mysql -u root -p campus_forum < /tmp/data.sql
```

#### 验证数据库

```bash
# 登录数据库
mysql -u root -p

# 切换到数据库
USE campus_forum;

# 查看所有表
SHOW TABLES;

# 查看用户表
SELECT id, username, role FROM users LIMIT 5;

# 查看帖子表
SELECT id, title, author, status FROM posts LIMIT 5;

# 退出
EXIT;
```

**如果看到数据，说明导入成功！**

---

### 2.5 配置防火墙（可选但推荐）

Ubuntu 自带 UFW 防火墙：

```bash
# 1. 启用防火墙
sudo ufw enable

# 2. 允许 SSH（重要！否则会断开连接）
sudo ufw allow 22/tcp

# 3. 允许 HTTP 和 HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 4. 查看规则
sudo ufw status

# 应该显示：
# Status: active
# To                         Action      From
# --                         ------      ----
# 22/tcp                     ALLOW       Anywhere
# 80/tcp                     ALLOW       Anywhere
# 443/tcp                     ALLOW       Anywhere
```

**注意：不要开放 3306 端口！MySQL 只允许本地访问。**

---

## 三、后端部署（预计 30 分钟）

### 3.1 本地打包后端

在本地 Windows 上操作：

```bash
# 1. 打开 PowerShell，进入后端项目目录
cd C:\Users\25366\IdeaProjects\back

# 2. 清理旧的编译文件
.\mvnw clean

# 3. 打包（跳过测试，加快速度）
.\mvnw package -DskipTests

# 打包过程需要 2-5 分钟，耐心等待
# 看到 "BUILD SUCCESS" 说明打包成功
```

**打包成功后，会生成：**
```
target\project-0.0.1-SNAPSHOT.jar
```

**验证 JAR 包：**
```bash
# 查看文件大小（应该有 50-80 MB）
dir target\project-0.0.1-SNAPSHOT.jar
```

---

### 3.2 上传文件到服务器

#### 上传 JAR 包

```bash
# 在本地 PowerShell 执行
cd C:\Users\25366\IdeaProjects\back

# 上传 JAR 包到服务器
scp target\project-0.0.1-SNAPSHOT.jar root@你的公网IP:/opt/campus-forum/
# 这个文件比较大，上传需要 1-3 分钟
```

#### 上传配置文件

```bash
# 上传生产环境配置
scp deploy\application-prod.properties root@你的公网IP:/opt/campus-forum/application.properties

# 上传部署脚本
scp deploy\deploy-backend.sh root@你的公网IP:/opt/campus-forum/
```

**验证上传：**

在服务器上执行：
```bash
ls -lh /opt/campus-forum/
# 应该看到：
# project-0.0.1-SNAPSHOT.jar  (约 50-80 MB)
# application.properties
# deploy-backend.sh
```

---

### 3.3 修改生产环境配置

在服务器上编辑配置文件：

```bash
# 使用 nano 编辑器（简单易用）
nano /opt/campus-forum/application.properties
```

**需要修改的地方：**

#### 1. 数据库密码（必改）

找到这一行：
```properties
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

改成你在 2.3 步骤设置的 MySQL 密码：
```properties
spring.datasource.password=YourStrongPassword123!
```

#### 2. 数据库用户名（如果创建了专用用户）

如果你创建了 `campus_user`，修改：
```properties
spring.datasource.username=campus_user
spring.datasource.password=AnotherStrongPassword456!
```

#### 3. 腾讯云审核配置（可选）

如果要启用腾讯云内容审核：
```properties
app.moderation.tencent.enabled=true
app.moderation.tencent.secret-id=你的SecretId
app.moderation.tencent.secret-key=你的SecretKey
app.moderation.tencent.region=ap-beijing
app.moderation.tencent.bucket=你的存储桶名称
```

如果不启用，保持：
```properties
app.moderation.tencent.enabled=false
```

#### 4. 其他配置（一般不需要改）

- 文件上传路径：`/opt/campus-forum/uploads/images`
- 备份目录：`/opt/campus-forum/backups`
- 服务器端口：`8080`

**保存文件：**
- 按 `Ctrl + X`
- 按 `Y`（确认保存）
- 按 `Enter`（确认文件名）

---

### 3.4 创建必要的目录

```bash
# 创建上传目录
mkdir -p /opt/campus-forum/uploads/images

# 创建备份目录
mkdir -p /opt/campus-forum/backups

# 创建日志目录
mkdir -p /opt/campus-forum/logs

# 设置权限
chmod -R 755 /opt/campus-forum/uploads
chmod -R 755 /opt/campus-forum/backups
chmod -R 755 /opt/campus-forum/logs

# 验证目录
ls -la /opt/campus-forum/
```

---

### 3.5 手动启动后端（测试）

先手动启动一次，确保没有问题：

```bash
# 进入应用目录
cd /opt/campus-forum

# 启动应用（前台运行，方便看日志）
java -jar \
  -Xms512m -Xmx1024m \
  -Dspring.profiles.active=prod \
  -Dspring.config.location=file:./application.properties \
  project-0.0.1-SNAPSHOT.jar
```

**启动过程：**
1. 看到大量日志输出（正常）
2. 等待 10-30 秒
3. 看到类似这样的日志，说明启动成功：
   ```
   Started ProjectApplication in 15.234 seconds
   ```

**测试接口：**

打开另一个终端（不要关闭当前终端），执行：

```bash
# 测试后端是否运行
curl http://localhost:8080/api/health
# 或者测试其他接口
curl http://localhost:8080/api/boards
```

如果返回 JSON 数据，说明后端运行正常！

**停止测试：**
- 回到运行 Java 的终端
- 按 `Ctrl + C` 停止应用

---

### 3.6 使用部署脚本启动（后台运行）

测试成功后，使用脚本启动：

```bash
# 给脚本添加执行权限
chmod +x /opt/campus-forum/deploy-backend.sh

# 执行部署脚本
cd /opt/campus-forum
./deploy-backend.sh
```

**脚本会自动：**
1. 停止旧应用（如果有）
2. 备份旧版本
3. 启动新应用（后台运行）
4. 保存进程 PID
5. 检查启动状态

**看到这样的输出，说明成功：**
```
=== 开始部署后端应用 ===
启动应用...
=== 部署完成 ===
应用 PID: 12345
✓ 应用启动成功
```

**查看日志：**

```bash
# 查看应用日志（实时）
tail -f /opt/campus-forum/logs/application.log

# 查看控制台日志
tail -f /opt/campus-forum/logs/console.log

# 按 Ctrl + C 退出日志查看
```

**检查应用状态：**

```bash
# 查看进程
ps aux | grep java

# 查看端口监听
netstat -tlnp | grep 8080
# 应该显示：0.0.0.0:8080

# 测试接口
curl http://localhost:8080/api/boards
```

---

### 3.7 配置开机自启动（systemd）

让应用在服务器重启后自动启动：

#### 步骤1：创建 systemd 服务文件

```bash
# 创建服务文件
sudo nano /etc/systemd/system/campus-forum.service
```

#### 步骤2：粘贴以下内容

```ini
[Unit]
Description=Campus Forum Backend Service
Documentation=https://github.com/your-repo
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/campus-forum
ExecStart=/usr/bin/java -jar \
  -Xms512m -Xmx1024m \
  -Dspring.profiles.active=prod \
  -Dspring.config.location=file:/opt/campus-forum/application.properties \
  /opt/campus-forum/project-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/campus-forum/logs/console.log
StandardError=append:/opt/campus-forum/logs/console.log

[Install]
WantedBy=multi-user.target
```

**保存文件：** `Ctrl + X` → `Y` → `Enter`

#### 步骤3：启用服务

```bash
# 重新加载 systemd 配置
sudo systemctl daemon-reload

# 启用开机自启
sudo systemctl enable campus-forum

# 启动服务
sudo systemctl start campus-forum

# 查看状态
sudo systemctl status campus-forum
```

**应该显示：**
```
● campus-forum.service - Campus Forum Backend Service
   Loaded: loaded (/etc/systemd/system/campus-forum.service; enabled)
   Active: active (running) since ...
```

#### 步骤4：常用命令

```bash
# 启动服务
sudo systemctl start campus-forum

# 停止服务
sudo systemctl stop campus-forum

# 重启服务
sudo systemctl restart campus-forum

# 查看状态
sudo systemctl status campus-forum

# 查看日志
sudo journalctl -u campus-forum -f

# 禁用开机自启
sudo systemctl disable campus-forum
```

---

### 3.8 后端部署验证清单

在继续前端部署前，确保：

- [ ] JAR 包已上传到 `/opt/campus-forum/`
- [ ] 配置文件已修改（数据库密码等）
- [ ] 应用成功启动（查看日志无报错）
- [ ] 端口 8080 正在监听
- [ ] 接口测试返回正常数据
- [ ] systemd 服务已配置并启用

**全部完成后，继续下一步！**

---

## 四、前端部署（预计 20 分钟）

### 4.1 修改前端 API 地址

在本地修改前端配置，让它连接到服务器后端：

#### 步骤1：创建生产环境配置文件

```bash
# 在本地 PowerShell 执行
cd C:\Users\25366\Desktop\project

# 创建生产环境配置文件
notepad .env.production
```

#### 步骤2：填写配置内容

在打开的记事本中输入：

```env
# 生产环境 API 地址
VITE_API_BASE_URL=http://你的公网IP/api

# 例如：
# VITE_API_BASE_URL=http://123.456.789.10/api
```

**注意：**
- 如果有域名，改成：`http://your-domain.com/api`
- 如果配置了 HTTPS，改成：`https://your-domain.com/api`
- 末尾不要加斜杠 `/`

保存并关闭记事本。

---

### 4.2 本地构建前端

```bash
# 确保在前端项目目录
cd C:\Users\25366\Desktop\project

# 安装依赖（如果还没安装）
npm install

# 构建生产版本
npm run build
```

**构建过程：**
1. 需要 1-3 分钟
2. 看到 "build complete" 说明成功
3. 会生成 `dist` 目录

**验证构建结果：**

```bash
# 查看 dist 目录
dir dist

# 应该看到：
# index.html
# assets/ (包含 CSS 和 JS 文件)
# favicon.ico
```

---

### 4.3 打包前端文件

```bash
# 在前端项目目录
cd C:\Users\25366\Desktop\project

# 打包 dist 目录（使用 tar 命令）
tar -czf dist.tar.gz dist/

# 如果 tar 命令不存在，使用 7-Zip 或 WinRAR 手动压缩
```

**如果没有 tar 命令：**

1. 右键点击 `dist` 文件夹
2. 选择"发送到" → "压缩(zipped)文件夹"
3. 重命名为 `dist.zip`

---

### 4.4 上传前端文件到服务器

#### 方法1：上传压缩包

```bash
# 上传到服务器
scp dist.tar.gz root@你的公网IP:/tmp/

# 或者如果是 zip 格式
scp dist.zip root@你的公网IP:/tmp/
```

#### 方法2：使用 WinSCP（推荐，更直观）

1. 打开 WinSCP，连接到服务器
2. 找到本地的 `dist.tar.gz` 或 `dist.zip`
3. 拖拽到服务器的 `/tmp/` 目录

---

### 4.5 在服务器上部署前端

连接到服务器，执行：

```bash
# 1. 创建前端目录
sudo mkdir -p /opt/campus-forum/frontend

# 2. 解压前端文件
cd /tmp

# 如果是 tar.gz 格式
sudo tar -xzf dist.tar.gz -C /opt/campus-forum/frontend --strip-components=1

# 如果是 zip 格式（需要先安装 unzip）
sudo apt install unzip -y
sudo unzip dist.zip -d /opt/campus-forum/frontend
sudo mv /opt/campus-forum/frontend/dist/* /opt/campus-forum/frontend/
sudo rm -rf /opt/campus-forum/frontend/dist

# 3. 设置权限
sudo chown -R www-data:www-data /opt/campus-forum/frontend
sudo chmod -R 755 /opt/campus-forum/frontend

# 4. 验证文件
ls -la /opt/campus-forum/frontend/
# 应该看到：index.html, assets/, favicon.ico 等
```

---

### 4.6 测试前端文件

```bash
# 查看 index.html 内容
head -20 /opt/campus-forum/frontend/index.html

# 应该看到 HTML 代码

# 查看 assets 目录
ls -lh /opt/campus-forum/frontend/assets/
# 应该看到 .js 和 .css 文件
```

---

## 五、Nginx 配置（预计 15 分钟）

### 5.1 上传 Nginx 配置文件

在本地执行：

```bash
# 上传配置文件
scp C:\Users\25366\IdeaProjects\back\deploy\nginx.conf root@你的公网IP:/tmp/campus-forum.conf
```

---

### 5.2 修改 Nginx 配置

在服务器上编辑配置：

```bash
# 复制到 Nginx 配置目录
sudo cp /tmp/campus-forum.conf /etc/nginx/sites-available/campus-forum

# 编辑配置
sudo nano /etc/nginx/sites-available/campus-forum
```

**需要修改的地方：**

找到这一行：
```nginx
server_name your-domain.com;
```

改成：
```nginx
# 如果有域名
server_name your-domain.com www.your-domain.com;

# 如果没有域名，使用 IP
server_name 123.456.789.10;

# 或者使用下划线（接受任何域名/IP）
server_name _;
```

保存文件：`Ctrl + X` → `Y` → `Enter`

---

### 5.3 启用 Nginx 配置

```bash
# 1. 创建软链接（启用配置）
sudo ln -s /etc/nginx/sites-available/campus-forum /etc/nginx/sites-enabled/

# 2. 删除默认配置（避免冲突）
sudo rm /etc/nginx/sites-enabled/default

# 3. 测试配置是否正确
sudo nginx -t

# 应该显示：
# nginx: configuration file /etc/nginx/nginx.conf test is successful
```

**如果测试失败：**
- 检查配置文件语法
- 查看错误信息
- 确保路径正确

---

### 5.4 重启 Nginx

```bash
# 重启 Nginx
sudo systemctl restart nginx

# 查看状态
sudo systemctl status nginx

# 应该显示：active (running)
```

---

### 5.5 测试访问

#### 测试1：在服务器上测试

```bash
# 测试前端首页
curl http://localhost/

# 应该返回 HTML 代码

# 测试后端 API
curl http://localhost/api/boards

# 应该返回 JSON 数据
```

#### 测试2：在本地浏览器测试

打开浏览器，访问：

```
http://你的公网IP
```

**应该看到：**
- 校园论坛的登录页面
- 页面样式正常
- 可以点击按钮

**如果看不到页面：**
1. 检查安全组是否开放 80 端口
2. 检查 Nginx 是否运行：`sudo systemctl status nginx`
3. 查看 Nginx 错误日志：`sudo tail -f /var/log/nginx/error.log`

---

### 5.6 测试完整功能

在浏览器中测试：

1. **注册账号**
   - 填写用户名、邮箱、密码
   - 点击注册
   - 应该成功

2. **登录**
   - 使用刚注册的账号登录
   - 应该进入首页

3. **发布帖子**
   - 点击"发布"
   - 填写标题和内容
   - 提交
   - 应该成功

4. **上传图片**
   - 在发帖时上传图片
   - 应该能正常上传和显示

**如果功能正常，恭喜你，部署成功！**

---

### 5.7 查看 Nginx 日志

```bash
# 查看访问日志（实时）
sudo tail -f /var/log/nginx/access.log

# 查看错误日志
sudo tail -f /var/log/nginx/error.log

# 按 Ctrl + C 退出
```

---

## 六、域名和 HTTPS 配置（可选，预计 20 分钟）

### 6.1 域名配置

如果你有域名（例如在腾讯云购买）：

#### 步骤1：域名解析

1. 登录腾讯云控制台
2. 进入"云解析 DNS"
3. 找到你的域名，点击"解析"
4. 添加记录：

| 主机记录 | 记录类型 | 记录值 | TTL |
|---------|---------|--------|-----|
| @ | A | 你的服务器公网IP | 600 |
| www | A | 你的服务器公网IP | 600 |

5. 保存

#### 步骤2：等待生效

- DNS 解析需要 5-10 分钟生效
- 可以用 `ping your-domain.com` 测试

#### 步骤3：修改 Nginx 配置

```bash
sudo nano /etc/nginx/sites-available/campus-forum
```

修改 `server_name`：
```nginx
server_name your-domain.com www.your-domain.com;
```

重启 Nginx：
```bash
sudo nginx -t
sudo systemctl restart nginx
```

#### 步骤4：修改前端配置

重新构建前端，使用域名：

```env
# .env.production
VITE_API_BASE_URL=http://your-domain.com/api
```

重新构建并上传。

---

### 6.2 配置 HTTPS（免费证书）

使用 Let's Encrypt 免费 SSL 证书：

#### 步骤1：安装 Certbot

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx -y
```

#### 步骤2：申请证书

```bash
# 自动配置 HTTPS
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 按照提示操作：
# 1. 输入邮箱（用于证书到期提醒）
# 2. 同意服务条款：输入 Y
# 3. 是否接收邮件：输入 N
# 4. 是否重定向 HTTP 到 HTTPS：选择 2（推荐）
```

#### 步骤3：验证 HTTPS

访问：`https://your-domain.com`

应该看到：
- 浏览器地址栏有锁图标
- 证书有效

#### 步骤4：自动续期

Let's Encrypt 证书有效期 90 天，需要自动续期：

```bash
# 测试续期
sudo certbot renew --dry-run

# 如果测试成功，Certbot 会自动配置定时任务
# 查看定时任务
sudo systemctl list-timers | grep certbot
```

#### 步骤5：修改前端配置

```env
# .env.production
VITE_API_BASE_URL=https://your-domain.com/api
```

重新构建并上传前端。

---

### 6.3 强制 HTTPS（推荐）

如果配置了 HTTPS，建议强制所有 HTTP 请求跳转到 HTTPS：

Certbot 已经自动配置了，但你可以手动检查：

```bash
sudo nano /etc/nginx/sites-available/campus-forum
```

应该看到类似这样的配置：

```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com www.your-domain.com;
    
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # ... 其他配置
}
```

---

## 七、监控和维护

### 7.1 查看应用状态

```bash
# 查看后端状态
sudo systemctl status campus-forum

# 查看日志
tail -f /opt/campus-forum/logs/application.log

# 查看 Nginx 日志
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

### 7.2 备份策略

自动备份已配置（每天凌晨2点），备份文件在：
```
/opt/campus-forum/backups/
```

手动备份：
```bash
mysqldump -u root -p campus_forum > /opt/campus-forum/backups/manual_backup_$(date +%Y%m%d).sql
```

### 7.3 更新应用

```bash
# 1. 本地打包新版本
# 2. 上传到服务器
scp target/project-0.0.1-SNAPSHOT.jar root@your_server_ip:/tmp/

# 3. 在服务器上更新
sudo systemctl stop campus-forum
sudo cp /tmp/project-0.0.1-SNAPSHOT.jar /opt/campus-forum/
sudo systemctl start campus-forum
```

---

## 八、常见问题

### 8.1 端口被占用

```bash
# 查看端口占用
sudo lsof -i :8080
sudo kill -9 <PID>
```

### 8.2 数据库连接失败

```bash
# 检查 MySQL 状态
sudo systemctl status mysql

# 检查连接
mysql -u root -p -e "SHOW DATABASES;"
```

### 8.3 文件上传失败

```bash
# 检查目录权限
sudo chown -R root:root /opt/campus-forum/uploads
sudo chmod -R 755 /opt/campus-forum/uploads
```

### 8.4 Nginx 502 错误

```bash
# 检查后端是否运行
sudo systemctl status campus-forum

# 检查端口监听
sudo netstat -tlnp | grep 8080
```

---

## 九、性能优化建议

### 9.1 数据库优化

```sql
-- 添加索引
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_board_id ON posts(board_id);
CREATE INDEX idx_posts_author ON posts(author);
CREATE INDEX idx_posts_created_at ON posts(created_at);
```

### 9.2 应用优化

- 增加 JVM 内存：`-Xms1g -Xmx2g`
- 启用 Spring Boot Actuator 监控
- 配置连接池参数

### 9.3 Nginx 优化

- 启用 Gzip 压缩
- 配置静态资源缓存
- 启用 HTTP/2

---

## 十、费用估算

**基础配置（月费用）**
- 云服务器 2核4GB：¥100-150
- 带宽 3Mbps：包含在服务器费用中
- 域名：¥50-100/年
- SSL证书：免费（Let's Encrypt）

**总计：约 ¥150-200/月**

---

## 十一、快速部署命令汇总

```bash
# 1. 服务器环境配置
bash server-setup.sh

# 2. 导入数据库
mysql -u root -p campus_forum < campus_forum.sql

# 3. 部署后端
cd /opt/campus-forum
./deploy-backend.sh

# 4. 部署前端
tar -xzf /tmp/dist.tar.gz -C /opt/campus-forum/frontend --strip-components=1

# 5. 配置 Nginx
sudo ln -s /etc/nginx/sites-available/campus-forum /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl restart nginx

# 6. 启动服务
sudo systemctl enable campus-forum
sudo systemctl start campus-forum
```

---

## 联系方式

如有问题，请查看日志：
- 后端日志：`/opt/campus-forum/logs/application.log`
- Nginx日志：`/var/log/nginx/error.log`
