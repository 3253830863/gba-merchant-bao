CREATE TABLE `consumer_account` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `merchant_id` bigint NOT NULL,
  `phone` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Normalized E.164 phone',
  `display_name` varchar(64) NOT NULL DEFAULT '',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_merchant_id` (`merchant_id`, `id`),
  UNIQUE KEY `uk_consumer_merchant_phone` (`merchant_id`, `phone`),
  CONSTRAINT `fk_consumer_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_consumer_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `merchant_staff_account` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `merchant_id` bigint NOT NULL,
  `username` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Normalized lowercase username',
  `phone` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Normalized E.164 phone',
  `display_name` varchar(64) NOT NULL DEFAULT '',
  `password_hash` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Salted BCrypt digest; never a raw password',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `created_by` bigint NULL COMMENT 'NULL only for the first provisioned staff account',
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_merchant_id` (`merchant_id`, `id`),
  UNIQUE KEY `uk_staff_merchant_username` (`merchant_id`, `username`),
  UNIQUE KEY `uk_staff_merchant_phone` (`merchant_id`, `phone`),
  KEY `idx_staff_merchant_creator` (`merchant_id`, `created_by`),
  CONSTRAINT `fk_staff_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `fk_staff_creator` FOREIGN KEY (`merchant_id`, `created_by`)
    REFERENCES `merchant_staff_account` (`merchant_id`, `id`),
  CONSTRAINT `ck_staff_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
