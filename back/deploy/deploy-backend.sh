#!/bin/bash
# 后端部署脚本

APP_NAME="campus-forum"
APP_DIR="/opt/campus-forum"
JAR_NAME="project-0.0.1-SNAPSHOT.jar"
PID_FILE="$APP_DIR/app.pid"

echo "=== 开始部署后端应用 ==="

# 1. 停止旧应用
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat $PID_FILE)
    if ps -p $OLD_PID > /dev/null; then
        echo "停止旧应用 (PID: $OLD_PID)..."
        kill $OLD_PID
        sleep 5
    fi
    rm -f $PID_FILE
fi

# 2. 备份旧版本
if [ -f "$APP_DIR/$JAR_NAME" ]; then
    echo "备份旧版本..."
    mv $APP_DIR/$JAR_NAME $APP_DIR/$JAR_NAME.bak.$(date +%Y%m%d_%H%M%S)
fi

# 3. 复制新版本
echo "复制新版本..."
cp target/$JAR_NAME $APP_DIR/

# 4. 复制生产配置
cp deploy/application-prod.properties $APP_DIR/application.properties

# 5. 创建必要目录
mkdir -p $APP_DIR/uploads/images
mkdir -p $APP_DIR/backups
mkdir -p $APP_DIR/logs

# 6. 启动应用
echo "启动应用..."
cd $APP_DIR
nohup java -jar \
    -Xms512m -Xmx1024m \
    -Dspring.profiles.active=prod \
    -Dspring.config.location=file:./application.properties \
    $JAR_NAME > logs/console.log 2>&1 &

# 7. 保存 PID
echo $! > $PID_FILE

echo "=== 部署完成 ==="
echo "应用 PID: $(cat $PID_FILE)"
echo "查看日志: tail -f $APP_DIR/logs/application.log"
echo "查看控制台: tail -f $APP_DIR/logs/console.log"

# 8. 等待启动
sleep 10
if ps -p $(cat $PID_FILE) > /dev/null; then
    echo "✓ 应用启动成功"
else
    echo "✗ 应用启动失败，请查看日志"
    exit 1
fi
