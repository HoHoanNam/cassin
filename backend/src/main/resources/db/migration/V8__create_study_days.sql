-- Mỗi ngày user ôn ít nhất 1 thẻ = 1 dòng. Dùng để tính "học N ngày liên tiếp".
CREATE TABLE study_days (
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_date DATE   NOT NULL,
    -- Khóa chính 2 cột: mỗi người mỗi ngày chỉ 1 dòng, và có sẵn index (user_id, study_date)
    -- cho truy vấn "các ngày học gần nhất của tôi".
    PRIMARY KEY (user_id, study_date)
);