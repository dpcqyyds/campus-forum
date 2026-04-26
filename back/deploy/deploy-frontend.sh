#!/bin/bash
# 前端部署脚本

FRONTEND_DIR="/opt/campus-forum/frontend"
SOURCE_DIR="C:\Users\25366\Desktop\project"  # 本地前端项目路径

echo "=== 开始部署前端应用 ==="

# 1. 在本地构建前端（在 Windows 上执行）
echo "请先在本地执行以下命令构建前端："
echo "cd $SOURCE_DIR"
echo "npm run build"
echo ""
echo "构建完成后，将 dist 目录上传到服务器"
echo ""

# 2. 在服务器上执行以下步骤
echo "=== 服务器端操作 ==="

# 创建前端目录
sudo mkdir -p $FRONTEND_DIR

# 备份旧版本
if [ -d "$FRONTEND_DIR/index.html" ]; then
    echo "备份旧版本..."
    sudo mv $FRONTEND_DIR $FRONTEND_DIR.bak.$(date +%Y%m%d_%H%M%S)
    sudo mkdir -p $FRONTEND_DIR
fi

# 解压上传的前端文件（假设上传到 /tmp/dist.tar.gz）
echo "解压前端文件..."
sudo tar -xzf /tmp/dist.tar.gz -C $FRONTEND_DIR

# 设置权限
sudo chown -R www-data:www-data $FRONTEND_DIR
sudo chmod -R 755 $FRONTEND_DIR

echo "=== 前端部署完成 ==="
