# Stage 1: ビルドアプリケーション
FROM eclipse-temurin:17-jdk-focal AS builder

WORKDIR /app

# Mavenラッパーとpom.xmlをコピー
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# mvnw に実行権限を付与
RUN chmod +x mvnw

# 依存関係をダウンロード
RUN ./mvnw dependency:go-offline -B

# ソースコードをコピー
COPY src ./src

# アプリケーションをビルド
RUN ./mvnw package -DskipTests

# Stage 2: 実行用の軽量イメージを作成
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# ビルドステージから実行可能JARをコピー
ARG JAR_FILE=target/springboot-bookmark-manager-0.0.1-SNAPSHOT.jar
COPY --from=builder /app/${JAR_FILE} app.jar

# ポートを公開
EXPOSE 8080
# EXPOSE 8443

# アプリケーションを実行
ENTRYPOINT ["java","-jar","app.jar"]