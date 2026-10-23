# Cassin

Website học tiếng Anh: ôn từ vựng bằng flashcard (hộp Leitner) và ôn ngữ pháp bằng trắc nghiệm.

| Phần | Công nghệ |
| --- | --- |
| backend/ | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL |
| frontend/ | Angular 22, SCSS |

## Chạy trên máy (Windows)

### 1. Tạo database (một lần)

Mở psql bằng tài khoản `postgres`:

```
D:\PostgreSQL\app\bin\psql.exe -U postgres
```

Rồi chạy:

```sql
CREATE USER cassin WITH PASSWORD 'cassin';
CREATE DATABASE cassin OWNER cassin;
```

### 2. Chạy backend

```
cd backend
mvn spring-boot:run
```

API chạy ở http://localhost:8080. Đổi thông tin DB bằng biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

### 3. Chạy frontend

Cần Node.js 22.22.3+ hoặc 24.15+.

```
cd frontend
npm install
npm start
```

Mở http://localhost:4200.
