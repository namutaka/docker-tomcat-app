TomcatアプリをDocker化する方法のサンプル
=======

以下の要件を目標としたTomcatを使ったWebアプリのDcoker化方法のサンプルプロジェクトです。

  1. Tomcatで動作させること
  2. 現状の動作のさせかたと同様にHTTPとAJPの両方に対応すること
  3. Dockerイメージのビルドがややこしくならないこと
  4. 開発環境もDockerで実行させること
  5. 開発時にコードの変更を簡易に素早く反映させられること

なるべく現状の動作方法を維持しつつ、開発環境にも対応させることを目標としています。


使い方
----

### 開発環境の起動

**初回やpom.xml編集時**

```bash
$ ./build.sh
$ docker-compose -f docker-compose-dev.yml up
```

**クラス追加や`resources`フォルダのファイル編集時**

```bash
$ ./build.sh -c
$ docker-compose -f docker-compose-dev.yml up
```


静的ファイルは修正すれば即座に反映される。
Javaファイルの修正後にIDE上でビルドをするだけで反映される。
ただし、クラスの追加や`resources`フォルダのファイルの編集などは`./build.sh -c`の実行が必要となる。
また、`pom.xml`の依存モジールを修正した場合は`./build.sh`からやり直す。

### Dockerイメージのビルド

```bash
$ ./build.sh
$ docker build --tag myapp:1.0 .
```

このイメージの起動方法は以下となる

```bash
$ docker-compose up
```


