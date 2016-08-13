#!/bin/bash

# Spling-loaded のjarを取得
SPRINGLOADED_URL=http://repo.spring.io/release/org/springframework/springloaded/1.2.5.RELEASE/springloaded-1.2.5.RELEASE.jar

if [ ! -f "springloaded-1.2.5.RELEASE.jar" ]; then
  curl -o springloaded-1.2.5.RELEASE.jar $SPRINGLOADED_URL
fi

COMPILE_ONLY=
if [ "$1" == "-c" ]; then
  COMPILE_ONLY=1
fi

if [ -z "$COMPILE_ONLY" ]; then
  # 依存jarを収集
  # 依存が変化した場合に不要になったjarが混じらないようにcleanを実施
  mvn \
    -DoutputDirectory=target/lib \
    -Dcompile=compile \
    clean \
    dependency:copy-dependencies
fi

# コンパイル
mvn compile

