@echo off
chcp 65001 >nul
setlocal

if "%OPENAI_API_KEY%"=="" (
    echo [错误] 未设置 OPENAI_API_KEY 环境变量
    echo 请先执行: set OPENAI_API_KEY=你的API密钥
    echo 或在系统环境变量中配置后重新打开命令行
    pause
    exit /b 1
)

rem 先检查同目录下的 jar（适配 target 目录），再检查上级 target 目录
if exist "%~dp0llm-chat.jar" (
    set JAR_FILE=%~dp0llm-chat.jar
) else if exist "%~dp0target\llm-chat.jar" (
    set JAR_FILE=%~dp0target\llm-chat.jar
) else (
    echo [提示] 未找到 llm-chat.jar，正在构建...
    call mvn clean package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo [错误] 构建失败
        pause
        exit /b 1
    )
    set JAR_FILE=%~dp0target\llm-chat.jar
)

echo 启动中...
java -Dfile.encoding=UTF-8 -jar "%JAR_FILE%"

if %ERRORLEVEL% NEQ 0 (
    pause
)
endlocal
