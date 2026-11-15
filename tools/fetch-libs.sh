#!/usr/bin/env bash
# 下载编译期需要的 Xaero 依赖到 libs/。
#
# 这几个 jar 不在公共 maven 上，而且 Xaero 的许可不允许随本仓库再分发，
# 所以仓库只放这个脚本，jar 由使用者自己拉。
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p libs

VERSION_ID="MfxfgXaO"   # Modrinth 版本 id，对应 xaerominimap-fabric-26.3-26.6.2
URL="https://cdn.modrinth.com/data/1bokaNcj/versions/${VERSION_ID}/xaerominimap-fabric-26.3-26.6.2.jar"
OUT="libs/xaerominimap-fabric-26.3-26.6.2.jar"

if [ ! -f "$OUT" ]; then
  echo "下载 Xaero's Minimap..."
  curl -fL "$URL" -o "$OUT"
fi

if [ ! -f libs/xaerolib-fabric-26.3-1.8.5.jar ]; then
  echo "从 Minimap jar 里取出内嵌的 XeroLib..."
  unzip -o -j "$OUT" 'META-INF/jars/xaerolib-*.jar' -d libs/
fi

# Mod Menu 是可选的编译依赖：装了它 mods 列表里才有齿轮，设置页本身 /wpsync config 也能开。
MODMENU_URL="https://cdn.modrinth.com/data/mOgUt4GM/versions/xckkBHD5/modmenu-21.0.1.jar"
if [ ! -f libs/modmenu-21.0.1.jar ]; then
  echo "下载 Mod Menu..."
  curl -fL "$MODMENU_URL" -o libs/modmenu-21.0.1.jar
fi

echo "libs/ 就绪："
ls -1 libs/*.jar
