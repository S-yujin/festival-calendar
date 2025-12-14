CREATE TABLE bookmark (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    festival_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_member_festival UNIQUE (member_id, festival_id),
    CONSTRAINT fk_bookmark_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_festival FOREIGN KEY (festival_id) REFERENCES festival_event(id) ON DELETE CASCADE
);

CREATE INDEX idx_bookmark_member ON bookmark(member_id);
CREATE INDEX idx_bookmark_created ON bookmark(created_at);

ALTER TABLE festival_master ADD COLUMN pattern_sample_count INT;
ALTER TABLE festival_master ADD COLUMN expected_month INT;
ALTER TABLE festival_master ADD COLUMN expected_week_of_month INT;
ALTER TABLE festival_master ADD COLUMN expected_day_of_week VARCHAR(20);
ALTER TABLE festival_master ADD COLUMN expected_duration_days INT;
ALTER TABLE festival_master ADD COLUMN pattern_last_updated DATETIME;