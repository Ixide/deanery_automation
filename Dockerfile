# --- Этап сборки (Build stage) ---
# Используем базовый образ с JDK 21 для сборки нашего Maven проекта
FROM eclipse-temurin:21-jdk-jammy as builder

# Устанавливаем рабочую директорию внутри контейнера
WORKDIR /app

# Копируем Maven Wrapper и pom.xml для загрузки зависимостей
# Важно: убедитесь, что mvnw, .mvn, pom.xml находятся в корне вашего репозитория
# и что они добавлены в Git и отправлены на GitHub.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# === ИСПРАВЛЕНИЕ: Добавляем права на исполнение для mvnw ===
RUN chmod +x ./mvnw

# Загружаем зависимости (этот слой будет кэшироваться, если pom.xml не менялся)
# Флаг -B (batch mode) убирает интерактивные запросы и форматирование вывода,
# что хорошо для CI/CD и Docker сборок.
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
# Убедитесь, что имя JAR-файла соответствует тому, что генерирует ваша сборка.
# Обычно это <artifactId>-<version>.jar (например, deans-office-automation-0.0.1-SNAPSHOT.jar)
# Если вы используете Spring Boot Maven Plugin с <finalName>, имя может быть другим.
ARG JAR_FILE=target/deans-office-automation-0.0.1-SNAPSHOT.jar 
COPY --from=builder /app/${JAR_FILE} application.jar

# Указываем порт, который приложение будет слушать внутри контейнера.
# Этот порт должен совпадать с server.port в application.properties (по умолчанию 8088).
# Render может переопределить этот порт или ожидать, что приложение будет слушать порт,
# указанный в переменной окружения PORT (обычно 10000).
# Если Render предоставляет PORT, то в application.properties можно использовать server.port=${PORT:8088}
EXPOSE 8088

# Команда для запуска приложения
# JAVA_OPTS можно будет передать через переменные окружения в Render для настройки памяти (например, -Xmx512m)
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
