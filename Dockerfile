# --- Этап сборки (Build stage) ---
# Используем базовый образ с JDK 21 для сборки нашего Maven проекта
FROM eclipse-temurin:21-jdk-jammy as builder

# Устанавливаем рабочую директорию внутри контейнера
WORKDIR /app

# Копируем Maven Wrapper и pom.xml для загрузки зависимостей
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Загружаем зависимости (этот слой будет кэшироваться, если pom.xml не менялся)
RUN ./mvnw dependency:go-offline -B

# Копируем исходный код
COPY src src

# Собираем приложение в JAR-файл
# -DskipTests пропускает тесты для ускорения сборки образа (для продакшена тесты лучше запускать в CI/CD)
RUN ./mvnw package -DskipTests

# --- Этап запуска (Runtime stage) ---
# Используем более легковесный образ с JRE 21 для запуска приложения
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Копируем собранный JAR-файл из этапа сборки
# Dockerfile предполагает, что ваш JAR будет в target/ и будет единственным JAR-файлом там
# или будет называться deans-office-automation-0.0.1-SNAPSHOT.jar (по вашему pom.xml)
# Если имя другое, скорректируйте путь к JAR_FILE
ARG JAR_FILE=target/deans-office-automation-0.0.1-SNAPSHOT.jar
COPY --from=builder /app/${JAR_FILE} application.jar

# Указываем порт, который приложение будет слушать внутри контейнера
# Этот порт должен совпадать с server.port в application.properties (по умолчанию 8088)
EXPOSE 8088

# Команда для запуска приложения
# JAVA_OPTS можно будет передать через переменные окружения в Render для настройки памяти и т.д.
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
